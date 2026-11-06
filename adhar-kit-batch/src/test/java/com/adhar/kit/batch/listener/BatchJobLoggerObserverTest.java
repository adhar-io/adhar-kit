package com.adhar.kit.batch.listener;

import com.adhar.adharkit.logging.batch.BatchJobLogger;
import com.adhar.adharkit.logging.batch.BatchJobRun;
import com.adhar.kit.batch.listener.BatchRunObserver.SkipPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchJobLoggerObserver}: every run drives the structured BATCH log events.
 */
class BatchJobLoggerObserverTest {

    private final BatchJobLogger logger = mock(BatchJobLogger.class);
    private final BatchJobRun run = mock(BatchJobRun.class);
    private final BatchJobLoggerObserver observer = new BatchJobLoggerObserver(logger);

    private final JobExecution job = new JobExecution(10L, new JobInstance(1L, "importJob"), new JobParameters());
    private final StepExecution step = new StepExecution(100L, "importStep", job);

    @BeforeEach
    void setUp() {
        when(logger.startJob("importJob")).thenReturn(run);
    }

    @Test
    @DisplayName("a run is tracked from its start until its end")
    void tracksTheRun() {
        observer.jobStarted(job);

        verify(logger).startJob("importJob");
        assertThat(observer.trackedRuns()).isEqualTo(1);

        job.setStatus(BatchStatus.COMPLETED);
        observer.jobFinished(job);

        assertThat(observer.trackedRuns()).isZero();
    }

    @Test
    @DisplayName("step transitions, committed items and skips reach the run")
    void reportsProgress() {
        observer.jobStarted(job);

        observer.stepStarted(step);
        observer.chunkCommitted(step, 3);
        observer.itemSkipped(step, SkipPhase.PROCESS, new IllegalArgumentException("invalid"));
        observer.stepFinished(step);

        verify(run).startStep("importStep");
        verify(run, times(3)).itemProcessed();
        verify(run).itemSkipped();
        verify(run).completeStep();
    }

    @Test
    @DisplayName("a completed job completes the run and fails nothing")
    void completes() {
        observer.jobStarted(job);
        job.setStatus(BatchStatus.COMPLETED);

        observer.jobFinished(job);

        verify(run).complete();
        verify(run, never()).fail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a failed job fails the run with its failure")
    void failsWithTheFailure() {
        var failure = new IllegalStateException("failure");
        observer.jobStarted(job);
        job.setStatus(BatchStatus.FAILED);
        job.addFailureException(failure);

        observer.jobFinished(job);

        verify(run).fail(failure);
        verify(run, never()).complete();
    }

    @Test
    @DisplayName("of several failures the run gets exactly one, and Spring Batch does not order them")
    void failsWithOneOfSeveral() {
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second");
        observer.jobStarted(job);
        job.setStatus(BatchStatus.FAILED);
        job.addFailureException(first);
        job.addFailureException(second);

        observer.jobFinished(job);

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(run, times(1)).fail(failure.capture());
        assertThat(failure.getValue()).isIn(first, second);
    }

    @Test
    @DisplayName("a stopped job with no failure fails the run, naming its status and exit code")
    void failsWithTheStatus() {
        observer.jobStarted(job);
        job.setStatus(BatchStatus.STOPPED);
        job.setExitStatus(ExitStatus.STOPPED);

        observer.jobFinished(job);

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(run).fail(failure.capture());
        assertThat(failure.getValue()).hasMessage("Job ended STOPPED with exit code STOPPED");
    }

    @Test
    @DisplayName("events of an execution it never saw start are ignored")
    void unknownExecution() {
        observer.stepStarted(step);
        observer.chunkCommitted(step, 2);
        observer.itemSkipped(step, SkipPhase.READ, new IllegalStateException("x"));
        observer.stepFinished(step);
        observer.jobFinished(job);

        verifyNoInteractions(logger, run);
        assertThat(observer.trackedRuns()).isZero();
    }

    @Test
    @DisplayName("a skip reported off the step's thread has no execution and is ignored")
    void skipWithoutAnExecution() {
        observer.jobStarted(job);

        observer.itemSkipped(null, SkipPhase.READ, new IllegalStateException("x"));

        verify(run, never()).itemSkipped();
    }

    @Test
    @DisplayName("two concurrent executions are tracked apart")
    void concurrentExecutions() {
        var otherJob = new JobExecution(11L, new JobInstance(2L, "otherJob"), new JobParameters());
        BatchJobRun otherRun = mock(BatchJobRun.class);
        when(logger.startJob("otherJob")).thenReturn(otherRun);
        observer.jobStarted(job);
        observer.jobStarted(otherJob);

        observer.chunkCommitted(new StepExecution(101L, "otherStep", otherJob), 1);

        verify(otherRun).itemProcessed();
        verify(run, never()).itemProcessed();
        assertThat(observer.trackedRuns()).isEqualTo(2);
    }
}
