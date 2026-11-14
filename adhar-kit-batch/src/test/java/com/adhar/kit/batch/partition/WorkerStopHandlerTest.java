package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.GracefulStopLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A worker's graceful stop (design 0025 §5.9): SIGTERM stops the partition the worker is running and
 * nothing else. The job execution is the manager's, and stopping it would stop every worker's partition.
 */
class WorkerStopHandlerTest {

    private final JobExecution job = new JobExecution(7L, new JobInstance(1L, "importJob"), new JobParameters());

    @Test
    @DisplayName("SIGTERM on a worker marks only its own partition terminateOnly and never stops the job")
    @SuppressWarnings("unchecked")
    void sigtermStopsOnlyTheWorkersPartition() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        ObjectProvider<JobOperator> operators = mock(ObjectProvider.class);
        when(operators.getObject()).thenReturn(operator);
        WorkerStopHandler handler = new WorkerStopHandler();
        GracefulStopLifecycle lifecycle = new GracefulStopLifecycle(operators, Duration.ofMillis(200), handler);
        StepExecution mine = new StepExecution(11L, "importStep:partition1", job);
        StepExecution other = new StepExecution(12L, "importStep:partition2", job);
        lifecycle.start();
        lifecycle.runStarted();
        handler.serving(mine);

        lifecycle.stop();

        assertThat(mine.isTerminateOnly()).isTrue();
        assertThat(other.isTerminateOnly()).isFalse();
        verify(operator, never()).stop(any(JobExecution.class));
        assertThat(lifecycle.isStopping()).isTrue();
    }

    @Test
    @DisplayName("a partition taken after the stop is stopped before its first chunk")
    void laterPartitionsStopAtOnce() {
        WorkerStopHandler handler = new WorkerStopHandler();
        assertThat(handler.stop()).as("nothing in hand").isFalse();

        StepExecution late = new StepExecution(13L, "importStep:partition3", job);
        handler.serving(late);

        assertThat(late.isTerminateOnly()).isTrue();
        assertThat(handler.isStopRequested()).isTrue();
    }

    @Test
    @DisplayName("a finished partition is no longer the worker's: a stop after it touches nothing")
    void servedPartitionsAreReleased() {
        WorkerStopHandler handler = new WorkerStopHandler();
        StepExecution done = new StepExecution(14L, "importStep:partition0", job);
        handler.serving(done);
        handler.served(done);

        assertThat(handler.stop()).isFalse();
        assertThat(done.isTerminateOnly()).isFalse();
    }
}
