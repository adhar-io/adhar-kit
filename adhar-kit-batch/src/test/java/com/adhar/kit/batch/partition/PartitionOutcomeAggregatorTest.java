package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.fault.NonRetryableBatchException;
import com.adhar.kit.batch.runtime.ExitCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the remote manager makes of its partitions (design 0025 §5.7, §5.9): a partition its worker
 * stopped is a retryable failure unless the run itself was asked to stop, and a partition that failed
 * for a reason retrying cannot fix fails the run as such.
 */
class PartitionOutcomeAggregatorTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final JobExecution job = new JobExecution(7L, new JobInstance(1L, "importJob"), new JobParameters());
    private final PartitionOutcomeAggregator aggregator = new PartitionOutcomeAggregator(repository);

    private StepExecution partition(String name, BatchStatus status, ExitStatus exit) {
        StepExecution execution = new StepExecution(name, job);
        execution.setStatus(status);
        execution.setExitStatus(exit);
        return execution;
    }

    private StepExecution manager() {
        StepExecution manager = new StepExecution("importManager", job);
        manager.setStatus(BatchStatus.COMPLETED);
        return manager;
    }

    private void jobIs(BatchStatus status) {
        JobExecution current = new JobExecution(7L, job.getJobInstance(), job.getJobParameters());
        current.setStatus(status);
        when(repository.getJobExecution(7L)).thenReturn(current);
    }

    @Test
    @DisplayName("a partition stopped by its worker fails the manager step: STOPPED would be exit 4, which never retries")
    void workerStopIsARetryableFailure() {
        jobIs(BatchStatus.STARTED);
        StepExecution manager = manager();

        // Thrown: the manager step fails with it, retryable, and its message becomes the run's exit description.
        assertThatThrownBy(() -> aggregator.aggregate(manager, List.of(partition("importStep:partition0",
                BatchStatus.COMPLETED, ExitStatus.COMPLETED), partition("importStep:partition1", BatchStatus.STOPPED,
                ExitStatus.STOPPED)))).isInstanceOf(IllegalStateException.class).isNotInstanceOf(NonRetryableBatchException.class)
                .hasMessageContaining("[importStep:partition1]").hasMessageContaining("stopped by their workers");
    }

    @Test
    @DisplayName("a stop of the run (operator, or the manager's own SIGTERM) stays STOPPED")
    void requestedStopStaysStopped() {
        for (BatchStatus stop : List.of(BatchStatus.STOPPING, BatchStatus.STOPPED)) {
            jobIs(stop);
            StepExecution manager = manager();

            aggregator.aggregate(manager, List.of(partition("importStep:partition0", BatchStatus.STOPPED,
                    ExitStatus.STOPPED)));

            assertThat(manager.getStatus()).as("job %s", stop).isEqualTo(BatchStatus.STOPPED);
        }
    }

    @Test
    @DisplayName("counts and a completed run are aggregated as Spring Batch aggregates them")
    void countsAreSummed() {
        jobIs(BatchStatus.STARTED);
        StepExecution manager = manager();
        StepExecution first = partition("importStep:partition0", BatchStatus.COMPLETED, ExitStatus.COMPLETED);
        first.setWriteCount(10);
        StepExecution second = partition("importStep:partition1", BatchStatus.COMPLETED, ExitStatus.COMPLETED);
        second.setWriteCount(5);

        aggregator.aggregate(manager, List.of(first, second));

        assertThat(manager.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(manager.getWriteCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("a partition failed by a NonRetryableBatchException (or a subclass, or a skip limit) fails the run as not retryable")
    void notRetryablePartitionsFailTheRunAsSuch() {
        jobIs(BatchStatus.STARTED);
        for (Throwable cause : List.of(new NonRetryableBatchException("control totals differ"),
                new ControlTotalsDiffer(), new SkipLimitExceededException(3, new IllegalStateException("bad row")),
                new IllegalStateException("wrapped", new NonRetryableBatchException("inner")))) {
            StepExecution manager = manager();
            StepExecution failed = partition("importStep:partition2", BatchStatus.FAILED,
                    ExitStatus.FAILED.addExitDescription(cause));

            assertThatThrownBy(() -> aggregator.aggregate(manager, List.of(failed)))
                    .as("%s", cause).isInstanceOf(NonRetryableBatchException.class)
                    .hasMessageContaining("importStep:partition2");
            JobExecution run = new JobExecution(8L, job.getJobInstance(), job.getJobParameters());
            run.setStatus(BatchStatus.FAILED);
            run.addFailureException(new NonRetryableBatchException("from the aggregator"));
            assertThat(ExitCodes.of(run)).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        }
    }

    @Test
    @DisplayName("any other partition failure stays retryable")
    void otherFailuresStayRetryable() {
        jobIs(BatchStatus.STARTED);
        StepExecution manager = manager();

        aggregator.aggregate(manager, List.of(partition("importStep:partition0", BatchStatus.FAILED,
                ExitStatus.FAILED.addExitDescription(new IllegalStateException("downstream unavailable")))));

        assertThat(manager.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(PartitionOutcomeAggregator.notRetryable("java.lang.IllegalStateException: x\n\tat a.b.C.d(C.java:1)"))
                .isEmpty();
        assertThat(PartitionOutcomeAggregator.notRetryable(null)).isEmpty();
    }

    /** An application's own not-retryable exception. */
    static class ControlTotalsDiffer extends NonRetryableBatchException {
        ControlTotalsDiffer() {
            super("control totals differ");
        }
    }
}
