package com.adhar.kit.batch.operator;

import com.adhar.kit.batch.metrics.BatchJobStats;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.FailureInjector;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchOperator} using a mocked {@link JobOperator} and
 * {@link JobRepository}, and the same operations on a real JDBC repository
 * ({@link OnTheJdbcRepository}).
 */
@ExtendWith(MockitoExtension.class)
class BatchOperatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final JobParameters PARAMETERS = new JobParameters();

    @Mock
    private JobOperator jobOperator;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobResolver jobResolver;
    @Mock
    private Job job;

    private final JobInstance instance = new JobInstance(1L, "job");
    private final JobExecution execution = new JobExecution(10L, instance, PARAMETERS);

    private BatchOperator operator;

    @BeforeEach
    void setUp() {
        operator = new BatchOperator(jobOperator, jobRepository, jobResolver);
    }

    private JobExecution ended(long id, BatchStatus status, LocalDateTime start, LocalDateTime end) {
        var ended = new JobExecution(id, instance, PARAMETERS);
        ended.setStatus(status);
        ended.setStartTime(start);
        ended.setEndTime(end);
        return ended;
    }

    @Test
    @DisplayName("start delegates to JobOperator")
    void startDelegates() throws Exception {
        when(job.getName()).thenReturn("job");
        when(jobOperator.start(job, PARAMETERS)).thenReturn(execution);

        assertThat(operator.start(job, PARAMETERS)).isSameAs(execution);
    }

    @Test
    @DisplayName("start by name resolves the job")
    void startByName() throws Exception {
        when(jobResolver.resolve("job")).thenReturn(job);
        when(job.getName()).thenReturn("job");
        when(jobOperator.start(job, PARAMETERS)).thenReturn(execution);

        assertThat(operator.start("job", PARAMETERS)).isSameAs(execution);
    }

    @Test
    @DisplayName("start by an unknown name propagates the resolver's refusal")
    void startByUnknownName() {
        when(jobResolver.resolve("ghost")).thenThrow(new IllegalArgumentException("No Job named [ghost]"));

        assertThatThrownBy(() -> operator.start("ghost", PARAMETERS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("restart delegates to JobOperator and returns the new execution")
    void restartDelegates() throws Exception {
        var restarted = new JobExecution(11L, instance, PARAMETERS);
        when(jobOperator.restart(execution)).thenReturn(restarted);

        assertThat(operator.restart(execution)).isSameAs(restarted);
    }

    @Test
    @DisplayName("stop delegates to JobOperator")
    void stopDelegates() throws Exception {
        when(jobOperator.stop(execution)).thenReturn(true);

        assertThat(operator.stop(execution)).isTrue();
        verify(jobOperator).stop(execution);
    }

    @Test
    @DisplayName("stop propagates JobExecutionNotRunningException")
    void stopPropagates() throws Exception {
        when(jobOperator.stop(execution)).thenThrow(new JobExecutionNotRunningException("not running"));

        assertThatThrownBy(() -> operator.stop(execution))
                .isInstanceOf(JobExecutionNotRunningException.class);
    }

    @Test
    @DisplayName("abandon delegates to JobOperator and returns the execution")
    void abandonDelegates() throws Exception {
        when(jobOperator.abandon(execution)).thenReturn(execution);

        assertThat(operator.abandon(execution)).isSameAs(execution);
    }

    @Test
    @DisplayName("recover delegates to JobOperator and returns the execution")
    void recoverDelegates() {
        when(jobOperator.recover(execution)).thenReturn(execution);

        assertThat(operator.recover(execution)).isSameAs(execution);
    }

    @Test
    @DisplayName("getRunningExecutions comes from JobRepository")
    void getRunningExecutionsDelegates() {
        when(jobRepository.findRunningJobExecutions("job")).thenReturn(Set.of(execution));

        assertThat(operator.getRunningExecutions("job")).containsExactly(execution);
    }

    @Test
    @DisplayName("findExecution comes from JobRepository")
    void findExecutionDelegates() {
        when(jobRepository.getJobExecution(10L)).thenReturn(execution);

        assertThat(operator.findExecution(10L)).containsSame(execution);
        assertThat(operator.findExecution(99L)).isEmpty();
    }

    @Test
    @DisplayName("findExecutions and findLastExecution are empty without a JobInstance")
    void lookupsWithoutAnInstance() {
        assertThat(operator.findExecutions("job", PARAMETERS)).isEmpty();
        assertThat(operator.findLastExecution("job", PARAMETERS)).isEmpty();
    }

    @Test
    @DisplayName("findExecutions and findLastExecution read the JobInstance of the parameters")
    void lookupsWithAnInstance() {
        when(job.getName()).thenReturn("job");
        when(jobRepository.getJobInstance("job", PARAMETERS)).thenReturn(instance);
        when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(execution));
        when(jobRepository.getLastJobExecution(instance)).thenReturn(execution);

        assertThat(operator.findExecutions(job, PARAMETERS)).containsExactly(execution);
        assertThat(operator.findLastExecution("job", PARAMETERS)).containsSame(execution);
    }

    @Test
    @DisplayName("getJobStats is zeros for a job that never ran")
    void statsOfAJobThatNeverRan() {
        when(jobRepository.getJobInstances("job", 0, BatchOperator.DEFAULT_STATS_INSTANCES)).thenReturn(List.of());

        var stats = operator.getJobStats("job");

        assertThat(stats.totalExecutions()).isZero();
        assertThat(stats.avgDurationMs()).isZero();
        assertThat(stats.lastExecutionTime()).isNull();
    }

    @Test
    @DisplayName("getJobStats aggregates the executions from the JobRepository")
    void statsAggregate() {
        when(jobRepository.getJobInstances("job", 0, BatchOperator.DEFAULT_STATS_INSTANCES))
                .thenReturn(List.of(instance));
        when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(
                ended(1L, BatchStatus.COMPLETED, T0, T0.plusSeconds(2)),
                ended(2L, BatchStatus.FAILED, T0.plusMinutes(1), T0.plusMinutes(1).plusSeconds(4)),
                // Still running: counted as an execution, not in the mean, and the latest start.
                ended(3L, BatchStatus.STARTED, T0.plusMinutes(2), null)));

        var stats = operator.getJobStats("job");

        assertThat(stats.totalExecutions()).isEqualTo(3);
        assertThat(stats.successCount()).isEqualTo(1);
        assertThat(stats.failureCount()).isEqualTo(1);
        assertThat(stats.avgDurationMs()).isEqualTo(3000.0);
        assertThat(stats.lastExecutionTime())
                .isEqualTo(T0.plusMinutes(2).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("getJobStats refuses a non-positive instance count")
    void statsRefuseANonPositiveCount() {
        assertThatThrownBy(() -> operator.getJobStats("job", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The same operations on the real auto-configuration and a JDBC JobRepository (H2): what the
     * mocks above cannot show is what the repository then records.
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OnTheJdbcRepository {

        private ConfigurableApplicationContext context;
        private BatchOperator real;
        private Job importJob;
        private FailureInjector failures;
        private JdbcTemplate jdbc;

        @BeforeAll
        void start() {
            context = BatchTestApplication.builder(TestJobs.class).properties(BatchTestApplication.h2()).run();
            real = context.getBean(BatchOperator.class);
            importJob = context.getBean(TestJobs.IMPORT_JOB, Job.class);
            failures = context.getBean(FailureInjector.class);
            jdbc = context.getBean(JdbcTemplate.class);
        }

        @AfterAll
        void close() {
            context.close();
        }

        private JobParameters run() {
            return new JobParametersBuilder().addString("run", UUID.randomUUID().toString()).toJobParameters();
        }

        private JobExecution failedRun(JobParameters parameters) throws Exception {
            TestJobs.seed(jdbc, 25);
            failures.clear();
            failures.failOn(15, 1);
            JobExecution failed = real.start(importJob, parameters);
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            return failed;
        }

        @Test
        @DisplayName("restart resumes the same instance; findExecutions lists both, most recent first")
        void restartAndFind() throws Exception {
            JobParameters parameters = run();
            JobExecution failed = failedRun(parameters);

            JobExecution restarted = real.restart(real.findExecution(failed.getId()).orElseThrow());

            assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
            assertThat(real.findExecutions(importJob, parameters)).extracting(JobExecution::getId)
                    .containsExactly(restarted.getId(), failed.getId());
            assertThat(real.findLastExecution(TestJobs.IMPORT_JOB, parameters)).get()
                    .extracting(JobExecution::getStatus).isEqualTo(BatchStatus.COMPLETED);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class)).isEqualTo(25);
        }

        @Test
        @DisplayName("an abandoned execution is recorded ABANDONED and can no longer be restarted")
        void abandonIsFinal() throws Exception {
            JobExecution failed = failedRun(run());

            JobExecution abandoned = real.abandon(failed);

            assertThat(real.findExecution(abandoned.getId()).orElseThrow().getStatus()).isEqualTo(BatchStatus.ABANDONED);
            assertThatThrownBy(() -> real.restart(real.findExecution(failed.getId()).orElseThrow()))
                    .isInstanceOf(JobRestartException.class);
        }

        @Test
        @DisplayName("recover marks an orphaned STARTED execution FAILED in the repository, so it restarts")
        void recoverAnOrphan() throws Exception {
            JobExecution failed = failedRun(run());
            // What a pod killed mid-chunk leaves behind: the execution and its step still STARTED.
            jdbc.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'STARTED', EXIT_CODE = 'UNKNOWN', END_TIME = NULL"
                    + " WHERE JOB_EXECUTION_ID = ?", failed.getId());
            jdbc.update("UPDATE BATCH_STEP_EXECUTION SET STATUS = 'STARTED', END_TIME = NULL WHERE JOB_EXECUTION_ID = ?",
                    failed.getId());
            JobExecution orphan = real.findExecution(failed.getId()).orElseThrow();
            assertThat(real.getRunningExecutions(TestJobs.IMPORT_JOB)).extracting(JobExecution::getId).contains(orphan.getId());

            JobExecution recovered = real.recover(orphan);

            assertThat(real.findExecution(recovered.getId()).orElseThrow().getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(real.getRunningExecutions(TestJobs.IMPORT_JOB)).extracting(JobExecution::getId)
                    .doesNotContain(orphan.getId());
            assertThat(real.restart(real.findExecution(recovered.getId()).orElseThrow()).getStatus())
                    .isEqualTo(BatchStatus.COMPLETED);
        }

        @Test
        @DisplayName("stop of an execution that is not running is refused; stats count what the repository holds")
        void stopNotRunningAndStats() throws Exception {
            JobExecution failed = failedRun(run());

            assertThatThrownBy(() -> real.stop(failed)).isInstanceOf(JobExecutionNotRunningException.class);
            BatchJobStats stats = real.getJobStats(TestJobs.IMPORT_JOB);
            assertThat(stats.totalExecutions()).isGreaterThanOrEqualTo(1);
            assertThat(stats.failureCount()).isGreaterThanOrEqualTo(1);
        }
    }
}
