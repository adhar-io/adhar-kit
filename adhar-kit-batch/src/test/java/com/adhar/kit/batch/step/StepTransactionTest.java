package com.adhar.kit.batch.step;

import com.adhar.kit.batch.listener.BatchJobLoggerObserver;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.lock.JdbcSchedulerLock;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.repository.BatchSchemaMigrator;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.FailureInjector;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.annotation.AfterRead;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * INV-4 of design 0025 on a real database: with the REAL auto-configuration on H2, business writes
 * and the JobRepository's progress share one transaction. {@link TestJobs#IMPORT_JOB}'s writer
 * inserts its chunk and THEN fails, so a failed chunk's rows exist in the transaction when it
 * fails: only a shared transaction rolls them back. A step left on Spring Batch's default
 * (resourceless) transaction manager would keep them, and a restart would write them twice.
 */
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class, StepTransactionTest.Observers.class})
class StepTransactionTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.h2().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BatchOperator operator;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    JobRepository jobRepository;
    @Autowired
    AdharSteps steps;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    FailureInjector failures;
    @Autowired
    RecordingObserver observer;
    @Autowired
    @Qualifier(TestJobs.IMPORT_JOB)
    Job importJob;

    @BeforeEach
    void setUp() {
        failures.clear();
        observer.committed.clear();
    }

    private static JobParameters run() {
        return new JobParametersBuilder().addString("run", UUID.randomUUID().toString()).toJobParameters();
    }

    private int targetRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class);
    }

    private int distinctTargetRows() {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class);
    }

    private static StepExecution onlyStep(JobExecution execution) {
        assertThat(execution.getStepExecutions()).hasSize(1);
        return execution.getStepExecutions().iterator().next();
    }

    @Test
    @DisplayName("every step AdharSteps builds is on the JobRepository's transaction manager")
    void theRealStepUsesTheRepositoryTransactionManager() throws Exception {
        Object importStep = context.getBean("importStep");
        Object target = AopUtils.isAopProxy(importStep)
                ? ((org.springframework.aop.framework.Advised) importStep).getTargetSource().getTarget() : importStep;

        assertThat(ReflectionTestUtils.getField(target, "transactionManager"))
                .isSameAs(steps.getTransactionManager())
                .isSameAs(transactionManager);
    }

    @Test
    @DisplayName("a failed chunk rolls back its business writes with the step's progress")
    void aFailedChunkRollsBackItsWrites() throws Exception {
        TestJobs.seed(jdbc, 25);
        failures.failOn(15, 1);

        JobExecution execution = operator.start(importJob, run());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        // Chunk 1 committed. Chunk 2 inserted 11..20 and then failed on 15: none of it stays.
        assertThat(targetRows()).isEqualTo(10);
        StepExecution step = onlyStep(execution);
        assertThat(step.getCommitCount()).isEqualTo(1);
        assertThat(step.getRollbackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a restart resumes from the last commit and writes nothing twice")
    void aRestartResumesFromTheLastCommit() throws Exception {
        TestJobs.seed(jdbc, 25);
        failures.failOn(15, 1);
        JobParameters parameters = run();
        assertThat(operator.start(importJob, parameters).getStatus()).isEqualTo(BatchStatus.FAILED);

        JobExecution restarted = operator.start(importJob, parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(targetRows()).isEqualTo(25);
        assertThat(distinctTargetRows()).isEqualTo(25);
        assertThat(onlyStep(restarted).getWriteCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("the JobRepository's progress commits with the chunk: a failed chunk is not in it")
    void progressIsCommittedWithTheChunk() throws Exception {
        TestJobs.seed(jdbc, 25);
        failures.failOn(15, 1);

        JobExecution execution = operator.start(importJob, run());

        assertThat(jobRepository.getStepExecution(onlyStep(execution).getId()).getWriteCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("observers hear about committed chunks only, through the kit's listeners")
    void observersHearOnlyCommittedChunks() throws Exception {
        TestJobs.seed(jdbc, 25);
        failures.failOn(15, 1);

        operator.start(importJob, run());

        assertThat(observer.committed).containsExactly(10L);
    }

    @Test
    @DisplayName("a tasklet step commits and rolls back with the repository's transaction too")
    void taskletTransaction() throws Exception {
        TestJobs.seed(jdbc, 0);
        var attempts = new AtomicInteger();
        var step = steps.tasklet("failingTasklet", (contribution, chunkContext) -> {
            jdbc.update("INSERT INTO TARGET_ITEMS (ID) VALUES (?)", attempts.incrementAndGet());
            if (attempts.get() == 1) {
                throw new IllegalStateException("after the insert");
            }
            return RepeatStatus.FINISHED;
        });
        Job job = new JobBuilder("taskletJob", jobRepository).start(step).build();
        JobParameters parameters = run();

        assertThat(jobOperator.start(job, parameters).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(targetRows()).isZero();

        assertThat(jobOperator.start(job, parameters).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForList("SELECT ID FROM TARGET_ITEMS", Long.class)).containsExactly(2L);
    }

    @Test
    @DisplayName("the builder's processor, listeners and streams all take part in a real run")
    void builderPartsRun() throws Exception {
        TestJobs.seed(jdbc, 0);
        ItemStream stream = mock(ItemStream.class);
        StepExecutionListener typed = mock(StepExecutionListener.class);
        var annotated = new AnnotatedListener();
        List<String> written = new CopyOnWriteArrayList<>();
        Step step = steps.<String, String>chunk("partsStep", 2)
                .reader(new ListItemReader<>(List.of("a", "b", "c")))
                .processor(String::toUpperCase)
                .writer(chunk -> written.addAll(chunk.getItems()))
                .stream(stream)
                .listener(typed)
                .listener((Object) annotated)
                .build();

        JobExecution execution = jobOperator.start(new JobBuilder("partsJob", jobRepository).start(step).build(), run());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(written).containsExactly("A", "B", "C");
        verify(stream).open(any());
        verify(stream, atLeastOnce()).update(any());
        verify(stream).close();
        verify(typed).beforeStep(any());
        assertThat(annotated.calls.get()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // The wiring on a database the kit does not manage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("on H2 the kit leaves the schema and the table prefix to Spring Boot")
    void unmanagedOnH2() {
        var migrator = context.getBean(BatchSchemaMigrator.class);

        assertThat(migrator.getLayout().managed()).isFalse();
        assertThat(migrator.getLayout().databaseProduct()).isEqualTo("H2");
        assertThat(context.getBean(BatchJdbcProperties.class).getTablePrefix()).isNull();
    }

    @Test
    @DisplayName("on H2 the scheduler lock table is unqualified, and the lock works")
    void lockOnH2() {
        SchedulerLock lock = context.getBean(SchedulerLock.class);

        assertThat(lock).isInstanceOf(JdbcSchedulerLock.class);
        assertThat(((JdbcSchedulerLock) lock).getTable()).isEqualTo("ADHAR_SCHEDULER_LOCK");
        assertThat(lock.tryLock("step-transaction-test", java.time.Duration.ofSeconds(30))).isTrue();
        lock.unlock("step-transaction-test");
    }

    @Test
    @DisplayName("the kit's listeners reach job and step beans")
    void listenersAreAttached() {
        BatchListenerAttacher attacher = context.getBean(BatchListenerAttacher.class);

        assertThat(attacher.isAttached(importJob)).isTrue();
        assertThat(attacher.isAttached(context.getBean("importStep"))).isTrue();
        assertThat(attacher.isAttached(context.getBean("slowStep"))).isTrue();
    }

    @Test
    @DisplayName("with adhar-kit-logging present every run drives its BATCH events and leaves nothing tracked")
    void structuredLogEvents() throws Exception {
        BatchJobLoggerObserver logging = context.getBean(BatchJobLoggerObserver.class);
        TestJobs.seed(jdbc, 5);

        assertThat(operator.start(importJob, run()).getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat((Integer) ReflectionTestUtils.invokeMethod(logging, "trackedRuns")).isZero();
    }

    /** Records the sizes of committed chunks. */
    static class RecordingObserver implements BatchRunObserver {

        final List<Long> committed = new CopyOnWriteArrayList<>();

        @Override
        public void chunkCommitted(StepExecution execution, long items) {
            committed.add(items);
        }
    }

    /** An application's own listener, declared by annotation. */
    public static class AnnotatedListener {

        final AtomicInteger calls = new AtomicInteger();

        @AfterRead
        public void afterRead(Object item) {
            calls.incrementAndGet();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Observers {

        @Bean
        RecordingObserver recordingObserver() {
            return new RecordingObserver();
        }
    }
}
