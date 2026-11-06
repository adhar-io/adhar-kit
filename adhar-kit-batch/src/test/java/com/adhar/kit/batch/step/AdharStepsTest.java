package com.adhar.kit.batch.step;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.fault.FaultTolerance;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdharSteps} and {@link AdharChunkStepBuilder}: what a built step is made of.
 * {@code StepTransactionTest} proves on a real database that the transaction manager does its job.
 */
class AdharStepsTest {

    private static final ItemWriter<String> WRITER = chunk -> { };
    private static final Tasklet TASKLET = (contribution, context) -> RepeatStatus.FINISHED;

    private final JobRepository repository = mock(JobRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final BatchListenerAttacher listeners = mock(BatchListenerAttacher.class);
    private final BatchProperties properties = new BatchProperties();

    private AdharSteps steps;

    @BeforeEach
    void setUp() {
        steps = new AdharSteps(repository, transactionManager, listeners, properties, null);
    }

    private AdharChunkStepBuilder<String, String> chunk() {
        return steps.<String, String>chunk("step", 5).reader(new ListItemReader<>(List.of("a"))).writer(WRITER);
    }

    // ------------------------------------------------------------------
    // INV-4: the JobRepository's transaction manager, never a resourceless one
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a chunk step runs on the JobRepository's transaction manager, not a resourceless one")
    void chunkStepUsesTheRepositoryTransactionManager() {
        ChunkOrientedStep<String, String> step = chunk().build();

        assertThat(ReflectionTestUtils.getField(step, "transactionManager")).isSameAs(transactionManager);
        assertThat(steps.getTransactionManager()).isSameAs(transactionManager);
        assertThat(steps.getJobRepository()).isSameAs(repository);
    }

    @Test
    @DisplayName("a tasklet step runs on the JobRepository's transaction manager too")
    void taskletStepUsesTheRepositoryTransactionManager() {
        TaskletStep step = steps.tasklet("tasklet", TASKLET);

        assertThat(ReflectionTestUtils.getField(step, "transactionManager")).isSameAs(transactionManager);
        assertThat(step.getName()).isEqualTo("tasklet");
    }

    // ------------------------------------------------------------------
    // AdharSteps
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the chunk size defaults to adhar.batch.chunk.size")
    void chunkSizeFromProperties() {
        properties.getChunk().setSize(7);

        ChunkOrientedStep<String, String> step = steps.<String, String>chunk("step")
                .reader(new ListItemReader<>(List.of("a"))).writer(WRITER).build();

        assertThat(step.getName()).isEqualTo("step");
        assertThat(ReflectionTestUtils.getField(step, "chunkSize")).isEqualTo(7);
    }

    @Test
    @DisplayName("an explicit chunk size wins over the property")
    void explicitChunkSize() {
        assertThat(ReflectionTestUtils.getField(chunk().build(), "chunkSize")).isEqualTo(5);
    }

    @Test
    @DisplayName("every step it builds carries the kit's listeners")
    void attachesListeners() {
        ChunkOrientedStep<String, String> chunkStep = chunk().build();
        TaskletStep taskletStep = steps.tasklet("tasklet", TASKLET);

        verify(listeners).attach(chunkStep);
        verify(listeners).attach(taskletStep);
    }

    @Test
    @DisplayName("a step needs a name and a positive chunk size")
    void refusals() {
        assertThatThrownBy(() -> steps.chunk(" ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a name");
        assertThatThrownBy(() -> steps.tasklet("", TASKLET)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a name");
        assertThatThrownBy(() -> steps.chunk("step", 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize must be positive");
    }

    @Test
    @DisplayName("the application's observation registry reaches chunk, tasklet and plain builders")
    void observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        var observed = new AdharSteps(repository, transactionManager, listeners, properties, registry);

        ChunkOrientedStep<String, String> chunkStep = observed.<String, String>chunk("step", 1)
                .reader(new ListItemReader<>(List.of("a"))).writer(WRITER).build();
        TaskletStep taskletStep = observed.tasklet("tasklet", TASKLET);

        assertThat(ReflectionTestUtils.getField(chunkStep, "observationRegistry")).isSameAs(registry);
        assertThat(ReflectionTestUtils.getField(taskletStep, "observationRegistry")).isSameAs(registry);
    }

    @Test
    @DisplayName("without a registry the framework's no-op stays in place")
    void noObservationRegistry() {
        assertThat(ReflectionTestUtils.getField(chunk().build(), "observationRegistry"))
                .isEqualTo(ObservationRegistry.NOOP);
    }

    @Test
    @DisplayName("step(name) is Spring Batch's own builder on the same repository")
    void plainBuilder() {
        StepBuilder builder = steps.step("plain");

        TaskletStep step = builder.tasklet(TASKLET, transactionManager).build();

        assertThat(step.getName()).isEqualTo("plain");
        assertThat(ReflectionTestUtils.getField(step, "jobRepository")).isSameAs(repository);
    }

    // ------------------------------------------------------------------
    // AdharChunkStepBuilder
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the builder needs a reader and a writer")
    void readerAndWriterRequired() {
        assertThatThrownBy(() -> steps.<String, String>chunk("step", 1).writer(WRITER).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reader");
        assertThatThrownBy(() -> steps.<String, String>chunk("step", 1)
                .reader(new ListItemReader<>(List.of("a"))).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("writer");
    }

    @Test
    @DisplayName("reader, processor and writer reach the step")
    void itemHandlers() {
        ListItemReader<String> reader = new ListItemReader<>(List.of("a"));
        org.springframework.batch.infrastructure.item.ItemProcessor<String, String> processor = String::toUpperCase;

        ChunkOrientedStep<String, String> step = steps.<String, String>chunk("step", 1)
                .reader(reader).processor(processor).writer(WRITER).build();

        assertThat(ReflectionTestUtils.getField(step, "itemReader")).isSameAs(reader);
        assertThat(ReflectionTestUtils.getField(step, "itemProcessor")).isSameAs(processor);
        assertThat(ReflectionTestUtils.getField(step, "itemWriter")).isSameAs(WRITER);
    }

    @Test
    @DisplayName("start limit and restart behaviour reach the step")
    void restartSettings() {
        ChunkOrientedStep<String, String> step = chunk().startLimit(2).allowStartIfComplete(true).build();

        assertThat(step.getStartLimit()).isEqualTo(2);
        assertThat(step.isAllowStartIfComplete()).isTrue();
    }

    @Test
    @DisplayName("the chunk transaction's attributes reach the step, on the same transaction manager")
    void transactionAttribute() {
        var attribute = new DefaultTransactionAttribute();
        attribute.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        ChunkOrientedStep<String, String> step = chunk().transactionAttribute(attribute).build();

        assertThat(ReflectionTestUtils.getField(step, "transactionAttribute")).isSameAs(attribute);
        assertThat(ReflectionTestUtils.getField(step, "transactionManager")).isSameAs(transactionManager);
    }

    @Test
    @DisplayName("a task executor reaches the step")
    void taskExecutor() {
        var executor = new SimpleAsyncTaskExecutor();

        assertThat(ReflectionTestUtils.getField(chunk().taskExecutor(executor).build(), "taskExecutor"))
                .isSameAs(executor);
    }

    @Test
    @DisplayName("customize gets Spring Batch's own builder")
    void customize() {
        var seen = new AtomicReference<Object>();

        chunk().customize(seen::set).build();

        assertThat(seen.get()).isInstanceOf(org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder.class);
    }

    @Test
    @DisplayName("no declaration means no fault tolerance: a step fails on the first item failure")
    void noFaultToleranceByDefault() {
        assertThat(ReflectionTestUtils.getField(chunk().build(), "faultTolerant")).isEqualTo(false);
    }

    @Test
    @DisplayName("a declared fault tolerance reaches the step, resolved against adhar.batch.retry")
    void declaredFaultTolerance() {
        RetryPolicy policy = RetryPolicy.withDefaults();

        ChunkOrientedStep<String, String> step = chunk()
                .faultTolerance(FaultTolerance.none().retryPolicy(policy).skip(IllegalArgumentException.class, 3))
                .build();

        assertThat(ReflectionTestUtils.getField(step, "faultTolerant")).isEqualTo(true);
        assertThat(ReflectionTestUtils.getField(step, "retryPolicy")).isSameAs(policy);
    }

    @Test
    @DisplayName("a null fault tolerance is refused")
    void nullFaultTolerance() {
        assertThatThrownBy(() -> chunk().faultTolerance(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
