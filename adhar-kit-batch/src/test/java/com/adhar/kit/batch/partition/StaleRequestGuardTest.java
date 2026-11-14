package com.adhar.kit.batch.partition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The worker half of the stale-request guard (design 0025 §5.10, F20), one rule at a time: only a
 * request of the live attempt, for a partition that has not started, runs.
 */
class StaleRequestGuardTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final StaleRequestGuard guard = new StaleRequestGuard(repository);
    private final JobInstance instance = new JobInstance(1L, "importJob");

    private StepExecution partition(long jobExecutionId, BatchStatus job, BatchStatus partition) {
        JobExecution execution = new JobExecution(jobExecutionId, instance, new JobParameters());
        execution.setStatus(job);
        StepExecution step = new StepExecution(40L + jobExecutionId, "importStep:partition0", execution);
        step.setStatus(partition);
        when(repository.getStepExecution(step.getId())).thenReturn(step);
        return step;
    }

    private void latestIs(long jobExecutionId) {
        when(repository.getLastJobExecution(instance)).thenReturn(new JobExecution(jobExecutionId, instance,
                new JobParameters()));
    }

    private static StepExecutionRequest request(StepExecution partition) {
        return new StepExecutionRequest("importStep", partition.getId());
    }

    @Test
    @DisplayName("a request of the live attempt for a partition not yet started runs")
    void live() {
        StepExecution partition = partition(7L, BatchStatus.STARTED, BatchStatus.STARTING);
        latestIs(7L);

        StaleRequestGuard.Verdict verdict = guard.admit(request(partition));

        assertThat(verdict.admitted()).isTrue();
        assertThat(verdict.partition()).isSameAs(partition);
    }

    @Test
    @DisplayName("a request whose job execution is not STARTED (failed, stopped, recovered, completed) is discarded")
    void notStarted() {
        for (BatchStatus status : new BatchStatus[]{BatchStatus.FAILED, BatchStatus.STOPPED, BatchStatus.STOPPING,
                BatchStatus.COMPLETED, BatchStatus.STARTING, BatchStatus.ABANDONED}) {
            StepExecution partition = partition(7L, status, BatchStatus.STARTING);
            latestIs(7L);

            StaleRequestGuard.Verdict verdict = guard.admit(request(partition));

            assertThat(verdict.admitted()).as("job %s", status).isFalse();
            assertThat(verdict.reason()).contains(status.name());
        }
    }

    @Test
    @DisplayName("a request of an attempt a newer execution has superseded is discarded, even while it still reads STARTED")
    void superseded() {
        StepExecution partition = partition(7L, BatchStatus.STARTED, BatchStatus.STARTING);
        latestIs(9L);

        StaleRequestGuard.Verdict verdict = guard.admit(request(partition));

        assertThat(verdict.admitted()).isFalse();
        assertThat(verdict.reason()).contains("superseded by 9");
    }

    @Test
    @DisplayName("a request for a partition that already started or ended is discarded: a partition runs once")
    void partitionAlreadyRan() {
        for (BatchStatus status : new BatchStatus[]{BatchStatus.STARTED, BatchStatus.COMPLETED, BatchStatus.FAILED}) {
            StepExecution partition = partition(7L, BatchStatus.STARTED, status);
            latestIs(7L);

            assertThat(guard.admit(request(partition)).admitted()).as("partition %s", status).isFalse();
        }
    }

    @Test
    @DisplayName("a request for a step execution the repository does not know is discarded")
    void unknown() {
        when(repository.getStepExecution(99L)).thenThrow(new EmptyResultDataAccessException(1));

        StaleRequestGuard.Verdict verdict = guard.admit(new StepExecutionRequest("importStep", 99L));

        assertThat(verdict.admitted()).isFalse();
        assertThat(verdict.partition()).isNull();
    }
}
