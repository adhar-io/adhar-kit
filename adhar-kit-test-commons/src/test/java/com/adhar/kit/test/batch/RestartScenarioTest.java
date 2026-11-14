package com.adhar.kit.test.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RestartScenario} on PostgreSQL 18.4 (A0.5 on the platform database): it proves a real
 * restart, and each of its assertions fails on the defect it exists for.
 */
@AdharBatchTest(classes = CopyJobApplication.class, properties = {
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:batch/schema.sql"})
@Testcontainers(disabledWithoutDocker = true)
class RestartScenarioTest {

    private static final AtomicLong DAY = new AtomicLong();

    @Autowired
    JobOperatorTestUtils jobs;
    @Autowired
    FailureInjector failures;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JobRepository jobRepository;
    @Autowired
    Step copyStep;

    private JobParameters parameters;

    @BeforeEach
    void setUp() {
        failures.clear();
        CopyJobApplication.seed(jdbc, 25);
        parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", LocalDate.of(2026, 1, 1).plusDays(DAY.incrementAndGet())).toJobParameters();
    }

    @Test
    @DisplayName("fail at item 15, restart: the same instance resumes after the last commit and writes each row once")
    void aRealRestart() throws Exception {
        RestartScenario.Outcome outcome = RestartScenario.of(jobs).withParameters(parameters).failingAt(failures, 15L).run()
                .assertStepHistory("copyStep", BatchStatus.FAILED, BatchStatus.COMPLETED)
                .assertResumedFromLastCommit("copyStep", 25)
                .assertExactlyOnce(jdbc, "target_items", "id", 25);

        assertThat(outcome.failed().getJobInstance().getInstanceId())
                .isEqualTo(outcome.restarted().getJobInstance().getInstanceId());
        assertThat(outcome.stepHistory("copyStep")).containsExactly(BatchStatus.FAILED, BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("a planned failure that never fires is reported, not mistaken for a pass")
    void anUncheckedItemIsReported() {
        assertThatThrownBy(() -> RestartScenario.of(jobs).withParameters(parameters).failingAt(failures, 999L).run())
                .isInstanceOf(AssertionError.class).hasMessageContaining("never checked");
        assertThatThrownBy(() -> RestartScenario.of(jobs).run()).hasMessageContaining("failingAt");
    }

    @Test
    @DisplayName("a restart that lands on a NEW JobInstance (an incrementer) is reported")
    void aNewInstanceIsReported() {
        Job incremented = new JobBuilder("copyJobWithIncrementer", jobRepository).incrementer(new RunIdIncrementer())
                .start(copyStep).build();

        assertThatThrownBy(() -> RestartScenario.of(jobs.getJobOperator(), incremented).withParameters(parameters)
                .failingAt(failures, 15L).run()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("started a NEW JobInstance");
    }

    @Test
    @DisplayName("a duplicated row and a missing row both fail assertExactlyOnce")
    void duplicatesAndGapsFail() throws Exception {
        RestartScenario.Outcome outcome = RestartScenario.of(jobs).withParameters(parameters).failingAt(failures, 15L).run();
        jdbc.update("INSERT INTO target_items (id) VALUES (3)");

        assertThatThrownBy(() -> outcome.assertExactlyOnce(jdbc, "target_items", "id", 25))
                .hasMessageContaining("wrote 1 rows twice");
        jdbc.update("DELETE FROM target_items WHERE id IN (3, 4)");
        assertThatThrownBy(() -> outcome.assertExactlyOnce(jdbc, "target_items", "id", 25))
                .hasMessageContaining("should have 25 rows but has 23");
        assertThatThrownBy(() -> outcome.assertExactlyOnce(jdbc, "target_items; DROP TABLE x", "id", 25))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a restart that started over, or a failure before the first commit, fails assertResumedFromLastCommit")
    void startingOverFails() {
        JobInstance instance = new JobInstance(1L, "copyJob");
        JobExecution failed = new JobExecution(1L, instance, parameters);
        StepExecution first = new StepExecution(1L, "copyStep", failed);
        first.setStatus(BatchStatus.FAILED);
        first.setCommitCount(1);
        first.setWriteCount(10);
        failed.addStepExecution(first);
        JobExecution restarted = new JobExecution(2L, instance, parameters);
        StepExecution second = new StepExecution(2L, "copyStep", restarted);
        second.setStatus(BatchStatus.COMPLETED);
        second.setReadCount(25);
        restarted.addStepExecution(second);
        RestartScenario.Outcome outcome = new RestartScenario.Outcome(failed, restarted);

        assertThatThrownBy(() -> outcome.assertResumedFromLastCommit("copyStep", 25)).hasMessageContaining("started over");
        first.setCommitCount(0);
        assertThatThrownBy(() -> outcome.assertResumedFromLastCommit("copyStep", 25))
                .hasMessageContaining("before its first commit");
        assertThatThrownBy(() -> outcome.assertStepHistory("copyStep", BatchStatus.COMPLETED))
                .hasMessageContaining("[FAILED, COMPLETED]");
        assertThatThrownBy(() -> outcome.assertResumedFromLastCommit("otherStep", 25)).hasMessageContaining("did not run");
    }
}
