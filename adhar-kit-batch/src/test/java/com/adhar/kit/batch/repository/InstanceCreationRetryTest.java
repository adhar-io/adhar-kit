package com.adhar.kit.batch.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InstanceCreationRetry}; the conflict itself on PostgreSQL is
 * {@link ConcurrentLaunchTestContainerTest}.
 */
class InstanceCreationRetryTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job job = mock(Job.class);
    private final JobParameters parameters = new JobParameters();
    private final JobExecution execution = new JobExecution(1L, new JobInstance(1L, "job"), parameters);

    @Test
    @DisplayName("a serialization conflict is started again until it succeeds")
    void retriesConflicts() throws Exception {
        when(jobOperator.start(job, parameters)).thenThrow(new CannotAcquireLockException("serialize"),
                new CannotAcquireLockException("serialize")).thenReturn(execution);

        assertThat(InstanceCreationRetry.start(jobOperator, job, parameters)).isSameAs(execution);
        verify(jobOperator, times(3)).start(job, parameters);
    }

    @Test
    @DisplayName("the last conflict is rethrown after MAX_ATTEMPTS starts")
    void givesUp() throws Exception {
        when(jobOperator.start(job, parameters)).thenThrow(new CannotAcquireLockException("serialize"));

        assertThatThrownBy(() -> InstanceCreationRetry.start(jobOperator, job, parameters))
                .isInstanceOf(CannotAcquireLockException.class);
        verify(jobOperator, times(InstanceCreationRetry.MAX_ATTEMPTS)).start(job, parameters);
    }

    @Test
    @DisplayName("a duplicate of the same identity and any other failure are not retried")
    void otherFailuresAreNotRetried() throws Exception {
        when(jobOperator.start(job, parameters)).thenThrow(new JobExecutionAlreadyRunningException("running"),
                new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> InstanceCreationRetry.start(jobOperator, job, parameters))
                .isInstanceOf(JobExecutionAlreadyRunningException.class);
        assertThatThrownBy(() -> InstanceCreationRetry.start(jobOperator, job, parameters))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(jobOperator, times(2)).start(job, parameters);
    }

    @Test
    @DisplayName("an instance whose first execution another launch has not stored yet is started again, and refused as the duplicate it is")
    void aHalfCreatedInstanceIsStartedAgain() throws Exception {
        // TaskExecutorJobLauncher's own wording, between the other launch's two transactions.
        when(jobOperator.start(job, parameters)).thenThrow(
                new IllegalStateException("Cannot find any job execution for job instance: JobInstance: id=1"),
                new JobExecutionAlreadyRunningException("running"));

        assertThatThrownBy(() -> InstanceCreationRetry.start(jobOperator, job, parameters))
                .isInstanceOf(JobExecutionAlreadyRunningException.class);
        verify(jobOperator, times(2)).start(job, parameters);
    }

    @Test
    @DisplayName("an instance that never gets an execution, and any other illegal state, are rethrown")
    void otherIllegalStatesAreRethrown() throws Exception {
        when(jobOperator.start(job, parameters)).thenThrow(new IllegalStateException("the context is closed"));
        assertThatThrownBy(() -> InstanceCreationRetry.start(jobOperator, job, parameters))
                .isInstanceOf(IllegalStateException.class).hasMessage("the context is closed");
        verify(jobOperator, times(1)).start(job, parameters);

        JobOperator orphaned = mock(JobOperator.class);
        when(orphaned.start(job, parameters)).thenThrow(
                new IllegalStateException(InstanceCreationRetry.INSTANCE_WITHOUT_EXECUTION + ": JobInstance: id=1"));
        assertThatThrownBy(() -> InstanceCreationRetry.start(orphaned, job, parameters))
                .isInstanceOf(IllegalStateException.class);
        verify(orphaned, times(InstanceCreationRetry.MAX_ATTEMPTS)).start(job, parameters);
    }
}
