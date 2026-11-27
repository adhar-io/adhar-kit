package com.adhar.kit.batch.ops;

import com.adhar.kit.batch.fault.DeadLetterReplayRegistry;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.runtime.JdbcExecutionLease;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.OpsJobs;
import com.adhar.kit.batch.support.PostgresDatabases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dlq-replay and the housekeeping on PostgreSQL 18 in the kit's own schema: the dead letters
 * are JSONB rows of {@code batch.ADHAR_BATCH_DEAD_LETTER} (a write-phase one included), and deleting
 * an instance takes its executions, their parameters, contexts and steps, and its lease rows.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {BatchTestApplication.class, OpsJobs.class})
class OpsCommandsTestContainerTest {

    private static final String URL = PostgresDatabases.createDatabase("ops");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.postgres(URL).forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    JobRepository jobRepository;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DeadLetterReplayRegistry replays;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    @Qualifier(OpsJobs.LEDGER_JOB)
    Job ledgerJob;

    @BeforeEach
    void reset() {
        OpsJobs.reset();
        for (String table : List.of("OPS_LEDGER", "batch.ADHAR_BATCH_DEAD_LETTER", "batch.ADHAR_BATCH_EXECUTION_LEASE",
                "batch.BATCH_STEP_EXECUTION_CONTEXT", "batch.BATCH_STEP_EXECUTION", "batch.BATCH_JOB_EXECUTION_CONTEXT",
                "batch.BATCH_JOB_EXECUTION_PARAMS", "batch.BATCH_JOB_EXECUTION", "batch.BATCH_JOB_INSTANCE")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private JobExecution ledger(String run) throws Exception {
        return jobOperator.start(ledgerJob, new JobParametersBuilder().addString("run", run).toJobParameters());
    }

    @Test
    @DisplayName("A3.6 on PostgreSQL: process- and write-phase rejects are JSONB dead letters, replayed and marked")
    void replay() throws Exception {
        OpsJobs.REJECT_PROCESS.add(2L);
        OpsJobs.REJECT_WRITE.add(8L);
        JobExecution execution = ledger("pg-a");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForList("SELECT PHASE FROM batch.ADHAR_BATCH_DEAD_LETTER WHERE JOB_EXECUTION_ID = ? "
                + "ORDER BY ID", String.class, execution.getId())).containsExactly("process", "write");
        OpsJobs.reset();
        DeadLetterReplayer replayer = new DeadLetterReplayer(jdbc, "batch.ADHAR_BATCH_DEAD_LETTER", replays,
                transactionManager, jobRepository, DeadLetterReplayer.Deserializer.standard(), Clock.systemUTC());

        DeadLetterReplayer.Result result = replayer.replay(OpsJobs.LEDGER_JOB, execution.getId());

        assertThat(result).isEqualTo(new DeadLetterReplayer.Result(2, 0, 0));
        assertThat(jdbc.queryForList("SELECT ID FROM OPS_LEDGER WHERE RUN = 'pg-a' ORDER BY ID", Long.class))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch.ADHAR_BATCH_DEAD_LETTER WHERE JOB_EXECUTION_ID = ? "
                + "AND REPLAYED_AT IS NOT NULL", Long.class, execution.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("on PostgreSQL: an item a restart dead-lettered again (JSONB twins) is written once, the other a duplicate")
    void replayWritesARestartsTwinOnce() throws Exception {
        OpsJobs.REJECT_WRITE.add(8L);
        // Entry 9 fails the step after entry 8's write-phase dead letter committed during the scan.
        OpsJobs.FAIL_WRITE.add(9L);
        JobExecution failed = ledger("pg-d");
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        OpsJobs.FAIL_WRITE.clear();
        JobExecution restart = jobOperator.restart(failed);
        assertThat(restart.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch.ADHAR_BATCH_DEAD_LETTER WHERE JOB_EXECUTION_ID IN "
                + "(?, ?)", Long.class, failed.getId(), restart.getId())).isEqualTo(2);
        OpsJobs.reset();
        DeadLetterReplayer replayer = new DeadLetterReplayer(jdbc, "batch.ADHAR_BATCH_DEAD_LETTER", replays,
                transactionManager, jobRepository, DeadLetterReplayer.Deserializer.standard(), Clock.systemUTC());

        assertThat(replayer.replay(OpsJobs.LEDGER_JOB, restart.getId())).isEqualTo(new DeadLetterReplayer.Result(1, 0, 1));
        assertThat(replayer.replay(OpsJobs.LEDGER_JOB, failed.getId())).isEqualTo(new DeadLetterReplayer.Result(0, 0, 0));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM OPS_LEDGER WHERE RUN = 'pg-d' AND ID = 8", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT REPLAY_EXECUTION_ID FROM batch.ADHAR_BATCH_DEAD_LETTER WHERE "
                + "JOB_EXECUTION_ID IN (?, ?)", Long.class, failed.getId(), restart.getId()))
                .containsOnly(restart.getId());
    }

    @Test
    @DisplayName("D4 on PostgreSQL: housekeeping deletes old instances with everything under them, leases included")
    void housekeeping() throws Exception {
        JobExecution oldest = ledger("pg-h1");
        JobExecution older = ledger("pg-h2");
        JobExecution newest = ledger("pg-h3");
        ExecutionLease lease = new JdbcExecutionLease(jdbc, "batch.ADHAR_BATCH_EXECUTION_LEASE");
        lease.acquire(oldest.getId(), new ExecutionLease.Owner("gone-pod", "uid", "node"));
        List<String> recorded = new CopyOnWriteArrayList<>();
        RepositoryHousekeeping housekeeping = new RepositoryHousekeeping(jobRepository, lease,
                (job, succeeded, deleted) -> recorded.add(succeeded + ":" + deleted),
                Clock.offset(Clock.systemUTC(), Duration.ofDays(200)));

        RepositoryHousekeeping.Result result = housekeeping.clean(OpsJobs.LEDGER_JOB, true, 90, 1);

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(recorded).containsExactly("true:2");
        for (String table : List.of("JOB_EXECUTION", "JOB_EXECUTION_PARAMS", "JOB_EXECUTION_CONTEXT", "STEP_EXECUTION")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch.BATCH_" + table + " WHERE JOB_EXECUTION_ID IN (?, ?)",
                    Long.class, oldest.getId(), older.getId())).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch.ADHAR_BATCH_EXECUTION_LEASE", Long.class)).isZero();
        assertThat(jobRepository.getJobExecution(newest.getId()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
