package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.step.AdharChunkStepBuilder;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FaultTolerance} in a real Spring Batch step on a real JobRepository and database: how
 * many times an item is really attempted, what is really skipped, what really fails the step.
 * {@code adhar.batch.retry.*} keeps its default of three attempts with a millisecond back-off.
 */
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class, FaultToleranceStepTest.Observers.class})
class FaultToleranceStepTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.h2().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("adhar.batch.retry.initial-backoff", () -> "1ms");
        registry.add("adhar.batch.retry.max-backoff", () -> "1ms");
    }

    @Autowired
    AdharSteps steps;
    @Autowired
    JobRepository jobRepository;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RecordingObserver observer;

    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final List<String> written = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        observer.skips.clear();
        observer.committed.clear();
        jdbc.update("DELETE FROM TARGET_ITEMS");
    }

    private int attemptsOn(String item) {
        return attempts.getOrDefault(item, new AtomicInteger()).get();
    }

    private ItemProcessor<String, String> failing(String badItem, RuntimeException failure, int times) {
        return item -> {
            int attempt = attempts.computeIfAbsent(item, key -> new AtomicInteger()).incrementAndGet();
            if (item.equals(badItem) && attempt <= times) {
                throw failure;
            }
            return item;
        };
    }

    private AdharChunkStepBuilder<String, String> step(ItemProcessor<String, String> processor) {
        return steps.<String, String>chunk("step", 2)
                .reader(new ListItemReader<>(List.of("a", "b", "c")))
                .processor(processor)
                .writer(chunk -> written.addAll(chunk.getItems()));
    }

    private JobExecution run(AdharChunkStepBuilder<String, String> builder) throws Exception {
        var job = new JobBuilder("faultToleranceJob", jobRepository).start(builder.build()).build();
        return jobOperator.start(job, new JobParametersBuilder().addString("run", UUID.randomUUID().toString())
                .toJobParameters());
    }

    private static StepExecution onlyStep(JobExecution execution) {
        return execution.getStepExecutions().iterator().next();
    }

    private static List<Throwable> causesOf(JobExecution execution) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable failure : execution.getAllFailureExceptions()) {
            for (Throwable current = failure; current != null; current = current.getCause()) {
                causes.add(current);
            }
        }
        return causes;
    }

    @Test
    @DisplayName("without a declaration an item failure fails the step at once")
    void noDeclaration() throws Exception {
        JobExecution execution = run(step(failing("b", new QueryTimeoutException("timeout"), 1)));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(attemptsOn("b")).isEqualTo(1);
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("a transient failure is retried until the item goes through")
    void transientFailureIsRetried() throws Exception {
        JobExecution execution = run(step(failing("b", new QueryTimeoutException("timeout"), 2))
                .faultTolerance(FaultTolerance.transientInfrastructure()));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(attemptsOn("b")).isEqualTo(3);
        assertThat(attemptsOn("a")).isEqualTo(1);
        assertThat(written).containsExactly("a", "b", "c");
        assertThat(onlyStep(execution).getSkipCount()).isZero();
    }

    @Test
    @DisplayName("retries stop at adhar.batch.retry.max-attempts, the first attempt included, and the step fails")
    void retriesAreBounded() throws Exception {
        JobExecution execution = run(step(failing("b", new QueryTimeoutException("timeout"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.transientInfrastructure()));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(attemptsOn("b")).isEqualTo(3);
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("maxAttempts on the declaration overrides the configured attempts")
    void declaredAttempts() throws Exception {
        run(step(failing("b", new QueryTimeoutException("timeout"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.transientInfrastructure().maxAttempts(5)));

        assertThat(attemptsOn("b")).isEqualTo(5);
    }

    @Test
    @DisplayName("a failure no preset covers is not retried")
    void otherFailuresAreNotRetried() throws Exception {
        JobExecution execution = run(step(failing("b", new IllegalStateException("bug"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.transientInfrastructure()));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(attemptsOn("b")).isEqualTo(1);
    }

    @Test
    @DisplayName("a transient failure caused by a NonRetryableBatchException is not retried")
    void nonRetryableInTheChainIsNotRetried() throws Exception {
        var failure = new QueryTimeoutException("timeout", new NonRetryableBatchException("invalid"));

        JobExecution execution = run(step(failing("b", failure, Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.transientInfrastructure()));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(attemptsOn("b")).isEqualTo(1);
    }

    @Test
    @DisplayName("a retryable failure's cause counts: a wrapped connection failure is retried")
    void causeChainIsMatched() throws Exception {
        var failure = new IllegalStateException("wrapped", new ConnectException("refused"));

        JobExecution execution = run(step(failing("b", failure, 1))
                .faultTolerance(FaultTolerance.transientInfrastructure()));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(attemptsOn("b")).isEqualTo(2);
    }

    @Test
    @DisplayName("a skippable item is skipped once its retries are spent, and the others are written")
    void skippedAfterItsRetries() throws Exception {
        JobExecution execution = run(step(failing("b", new QueryTimeoutException("timeout"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.transientInfrastructure().skip(QueryTimeoutException.class, 5)));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(attemptsOn("b")).isEqualTo(3);
        assertThat(written).containsExactly("a", "c");
        assertThat(onlyStep(execution).getProcessSkipCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a NonRetryableBatchException declared skippable is skipped on its first attempt")
    void nonRetryableIsSkippedWithoutRetry() throws Exception {
        JobExecution execution = run(step(failing("b", new NonRetryableBatchException("invalid"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.transientInfrastructure().skip(NonRetryableBatchException.class, 1)));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(attemptsOn("b")).isEqualTo(1);
        assertThat(written).containsExactly("a", "c");
    }

    @Test
    @DisplayName("a skip reaches the observers with its phase, through the kit's listeners")
    void skipReachesTheObservers() throws Exception {
        run(step(failing("b", new IllegalArgumentException("invalid"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.none().skip(IllegalArgumentException.class, 1)));

        assertThat(observer.skips).containsExactly(BatchRunObserver.SkipPhase.PROCESS);
        // a in the first chunk, b gone; then c.
        assertThat(observer.committed).containsExactly(1L, 1L);
    }

    @Test
    @DisplayName("going over the skip limit fails the step: the data, not the infrastructure, is at fault")
    void skipLimitExceeded() throws Exception {
        ItemProcessor<String, String> everyItemFails = item -> {
            throw new IllegalArgumentException("invalid");
        };

        JobExecution execution = run(step(everyItemFails)
                .faultTolerance(FaultTolerance.none().skip(IllegalArgumentException.class, 1)));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(causesOf(execution)).hasAtLeastOneElementOfType(SkipLimitExceededException.class);
    }

    @Test
    @DisplayName("the application's own skip policy decides instead of the declared types")
    void ownSkipPolicy() throws Exception {
        JobExecution execution = run(step(failing("b", new IllegalStateException("anything"), Integer.MAX_VALUE))
                .faultTolerance(FaultTolerance.none().skipPolicy((failure, count) -> true)));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(written).containsExactly("a", "c");
    }

    @Test
    @DisplayName("a read failure is retried too")
    void readsAreRetried() throws Exception {
        var failures = new AtomicInteger(2);
        var reader = new ListItemReader<>(List.of("a", "b"));
        var builder = steps.<String, String>chunk("step", 2)
                .reader(() -> {
                    if (failures.getAndDecrement() > 0) {
                        throw new QueryTimeoutException("timeout");
                    }
                    return reader.read();
                })
                .writer(chunk -> written.addAll(chunk.getItems()))
                .faultTolerance(FaultTolerance.transientInfrastructure());

        assertThat(run(builder).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(written).containsExactly("a", "b");
    }

    @Test
    @DisplayName("a bad row is isolated on write: the chunk rolls back, then each item commits alone")
    void writeSkipIsolatesTheItem() throws Exception {
        ItemWriter<String> writer = chunk -> {
            for (String item : chunk) {
                jdbc.update("INSERT INTO TARGET_ITEMS (ID) VALUES (?)", (long) item.charAt(0));
                if (item.equals("b")) {
                    throw new DuplicateKeyException("constraint");
                }
            }
        };
        var builder = steps.<String, String>chunk("step", 3)
                .reader(new ListItemReader<>(List.of("a", "b", "c")))
                .writer(writer)
                .faultTolerance(FaultTolerance.none().skip(DuplicateKeyException.class, 1));

        JobExecution execution = run(builder);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(onlyStep(execution).getWriteSkipCount()).isEqualTo(1);
        // The bad item's own insert rolled back with its transaction: a and c remain.
        assertThat(jdbc.queryForList("SELECT ID FROM TARGET_ITEMS ORDER BY ID", Long.class))
                .containsExactly((long) 'a', (long) 'c');
        assertThat(observer.skips).containsExactly(BatchRunObserver.SkipPhase.WRITE);
    }

    /** Records the kit's skip and commit notifications. */
    static class RecordingObserver implements BatchRunObserver {

        final List<SkipPhase> skips = new CopyOnWriteArrayList<>();
        final List<Long> committed = new CopyOnWriteArrayList<>();

        @Override
        public void itemSkipped(StepExecution execution, SkipPhase phase, Throwable error) {
            skips.add(phase);
        }

        @Override
        public void chunkCommitted(StepExecution execution, long items) {
            committed.add(items);
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
