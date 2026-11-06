package com.adhar.kit.batch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BatchListenerAttacher}, with real Spring Batch jobs and steps on an in-memory
 * repository. The calls during a real run prove a listener is attached. They cannot prove it is
 * attached once, because Spring Batch's composites ignore a listener they already hold; that the
 * attacher resolves each listener only once is what shows its own guard works.
 */
class BatchListenerAttacherTest {

    private final JobRepository repository = new ResourcelessJobRepository();

    private final AdharJobExecutionListener jobListener = mock(AdharJobExecutionListener.class);
    private final AdharStepExecutionListener stepListener = mock(AdharStepExecutionListener.class);
    private final AdharChunkListener chunkListener = mock(AdharChunkListener.class);
    private final AdharSkipListener skipListener = mock(AdharSkipListener.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AdharJobExecutionListener> jobProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AdharStepExecutionListener> stepProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AdharChunkListener> chunkProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AdharSkipListener> skipProvider = mock(ObjectProvider.class);

    private BatchListenerAttacher attacher;

    @BeforeEach
    void setUp() {
        when(jobProvider.getIfAvailable()).thenReturn(jobListener);
        when(stepProvider.getIfAvailable()).thenReturn(stepListener);
        when(chunkProvider.getIfAvailable()).thenReturn(chunkListener);
        when(skipProvider.getIfAvailable()).thenReturn(skipListener);
        attacher = new BatchListenerAttacher(jobProvider, stepProvider, chunkProvider, skipProvider);
    }

    private TaskletStep taskletStep(String name) {
        return new StepBuilder(name, repository)
                .tasklet((contribution, context) -> RepeatStatus.FINISHED, new ResourcelessTransactionManager())
                .build();
    }

    private ChunkOrientedStep<String, String> chunkStep(String name, List<String> items) {
        return new StepBuilder(name, repository).<String, String>chunk(2)
                .transactionManager(new ResourcelessTransactionManager())
                .reader(new ListItemReader<>(items))
                .processor(item -> {
                    if (item.startsWith("bad")) {
                        throw new IllegalArgumentException("invalid item");
                    }
                    return item;
                })
                .writer(chunk -> { })
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(10)
                .build();
    }

    private Job job(Step first, Step... more) {
        var flow = new JobBuilder("job", repository).start(first);
        for (Step step : more) {
            flow = flow.next(step);
        }
        return flow.build();
    }

    private JobExecution run(Job job) throws Exception {
        var parameters = new JobParameters();
        var instance = repository.createJobInstance(job.getName(), parameters);
        var execution = repository.createJobExecution(instance, parameters, new ExecutionContext());
        job.execute(execution);
        return execution;
    }

    @Test
    @DisplayName("resolving the listeners waits for the first use: a post-processor must not create beans early")
    void lazy() {
        verifyNoInteractions(jobProvider, stepProvider, chunkProvider, skipProvider);
    }

    @Test
    @DisplayName("a job gets the job listener and the step listener reaches every step it lists")
    void attachesToAJobAndItsSteps() throws Exception {
        TaskletStep first = taskletStep("first");
        TaskletStep second = taskletStep("second");
        Job job = job(first, second);

        attacher.attach(job);

        assertThat(attacher.isAttached(job)).isTrue();
        assertThat(attacher.isAttached(first)).isTrue();
        assertThat(attacher.isAttached(second)).isTrue();
        assertThat(run(job).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(jobListener, times(1)).beforeJob(any());
        verify(jobListener, times(1)).afterJob(any());
        verify(stepListener, times(2)).beforeStep(any());
        verify(stepListener, times(2)).afterStep(any());
    }

    @Test
    @DisplayName("attaching is idempotent: reached as a bean, through its job and directly, a step listens once")
    void idempotent() throws Exception {
        TaskletStep step = taskletStep("step");
        Job job = job(step);

        attacher.postProcessAfterInitialization(step, "step");
        attacher.attach(step);
        attacher.attach(job);
        attacher.attach(job);
        attacher.postProcessAfterInitialization(job, "job");

        run(job);

        verify(jobProvider, times(1)).getIfAvailable();
        verify(stepProvider, times(1)).getIfAvailable();
        verify(jobListener, times(1)).beforeJob(any());
        verify(stepListener, times(1)).beforeStep(any());
        verify(stepListener, times(1)).afterStep(any());
    }

    @Test
    @DisplayName("a chunk step also gets the chunk and skip listeners, once each")
    void chunkSteps() throws Exception {
        ChunkOrientedStep<String, String> step = chunkStep("chunk", List.of("a", "bad", "b"));
        Job job = job(step);

        attacher.attach(step);
        attacher.attach(job);

        JobExecution execution = run(job);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(chunkProvider, times(1)).getIfAvailable();
        verify(skipProvider, times(1)).getIfAvailable();
        verify(stepListener, times(1)).beforeStep(any());
        // Three items in chunks of two: two chunks.
        verify(chunkListener, times(2)).afterChunk(any(org.springframework.batch.infrastructure.item.Chunk.class));
        verify(skipListener, times(1)).onSkipInProcess(eq("bad"), any());
    }

    @Test
    @DisplayName("a tasklet step gets no chunk or skip listener")
    void taskletStepHasNoChunkListeners() throws Exception {
        TaskletStep step = taskletStep("tasklet");

        attacher.attach(step);
        run(job(step));

        verify(stepListener, times(1)).beforeStep(any());
        verifyNoInteractions(chunkProvider, skipProvider);
    }

    @Test
    @DisplayName("as a post-processor it instruments jobs and steps and returns every bean unchanged")
    void postProcessor() {
        TaskletStep step = taskletStep("step");
        Job job = job(taskletStep("inner"));
        Object other = new Object();

        assertThat(attacher.postProcessAfterInitialization(step, "step")).isSameAs(step);
        assertThat(attacher.postProcessAfterInitialization(job, "job")).isSameAs(job);
        assertThat(attacher.postProcessAfterInitialization(other, "other")).isSameAs(other);

        assertThat(attacher.isAttached(step)).isTrue();
        assertThat(attacher.isAttached(job)).isTrue();
        assertThat(attacher.isAttached(other)).isFalse();
    }

    @Test
    @DisplayName("a job or step that is not the framework's own class is left alone")
    void foreignImplementations() {
        Job job = mock(Job.class);
        Step step = mock(Step.class);

        attacher.attach(job);
        attacher.attach(step);

        assertThat(attacher.isAttached(job)).isFalse();
        assertThat(attacher.isAttached(step)).isFalse();
        verifyNoInteractions(jobProvider, stepProvider);
    }

    @Test
    @DisplayName("a step its job cannot find is skipped without failing the startup")
    void unknownStep() {
        var job = mock(Job.class, org.mockito.Mockito.withSettings()
                .extraInterfaces(org.springframework.batch.core.step.ListableStepLocator.class));
        var locator = (org.springframework.batch.core.step.ListableStepLocator) job;
        when(locator.getStepNames()).thenReturn(List.of("ghost"));

        assertThatCode(() -> attacher.attach((Job) job)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a listener bean that is absent is simply not attached")
    void absentListeners() throws Exception {
        when(jobProvider.getIfAvailable()).thenReturn(null);
        when(stepProvider.getIfAvailable()).thenReturn(null);
        when(chunkProvider.getIfAvailable()).thenReturn(null);
        when(skipProvider.getIfAvailable()).thenReturn(null);
        var step = chunkStep("chunk", List.of("a"));
        Job job = job(step);

        attacher.attach(job);

        assertThat(attacher.isAttached(job)).isTrue();
        assertThat(attacher.isAttached(step)).isTrue();
        assertThat(run(job).getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
