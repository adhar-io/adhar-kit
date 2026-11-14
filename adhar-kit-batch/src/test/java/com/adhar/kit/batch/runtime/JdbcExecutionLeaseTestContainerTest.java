package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JdbcExecutionLease} on PostgreSQL 18 in the kit's schema ({@code V6_0_1}): TIMESTAMPTZ
 * columns, a null commit time inside {@code COALESCE}, and the foreign key to the execution, whose
 * deletion (retention) takes the lease with it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class})
class JdbcExecutionLeaseTestContainerTest {

    private static final String URL = PostgresDatabases.createDatabase("lease");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.postgres(URL).forEach((key, value) -> registry.add(key, () -> value));
        registry.add("test.slow-job.sleep", () -> "0");
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    @Qualifier(TestJobs.SLOW_JOB)
    Job slowJob;

    @Test
    @DisplayName("the lease contract on PostgreSQL, against a real execution")
    void onPostgres() throws Exception {
        JobExecution execution = jobOperator.start(slowJob, new JobParametersBuilder()
                .addString("adhar.scheduled-time", "lease-pg").toJobParameters());
        JdbcExecutionLease lease = new JdbcExecutionLease(jdbc, "batch.ADHAR_BATCH_EXECUTION_LEASE");

        JdbcExecutionLeaseTest.contract(lease, execution.getId(), () -> jdbc.update(
                "UPDATE batch.ADHAR_BATCH_EXECUTION_LEASE SET HEARTBEAT_AT = CURRENT_TIMESTAMP - INTERVAL '5 minutes'"));

        assertThatThrownBy(() -> lease.acquire(-1, JdbcExecutionLeaseTest.SELF)).as("only real executions")
                .isInstanceOf(DataIntegrityViolationException.class);
        lease.acquire(execution.getId(), JdbcExecutionLeaseTest.SELF);
        jdbc.update("DELETE FROM batch.BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN (SELECT "
                + "STEP_EXECUTION_ID FROM batch.BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?)", execution.getId());
        jdbc.update("DELETE FROM batch.BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?", execution.getId());
        jdbc.update("DELETE FROM batch.BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID = ?", execution.getId());
        jdbc.update("DELETE FROM batch.BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ?", execution.getId());
        jdbc.update("DELETE FROM batch.BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", execution.getId());
        assertThat(lease.find(execution.getId())).as("deleted with its execution").isEmpty();
    }
}
