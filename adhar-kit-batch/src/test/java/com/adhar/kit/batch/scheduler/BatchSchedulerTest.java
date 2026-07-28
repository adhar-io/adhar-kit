package com.adhar.kit.batch.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchScheduler} using mocked Spring infrastructure.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    @SuppressWarnings("rawtypes")
    private ScheduledFuture future;
    @Mock
    private Job job;

    private BatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BatchScheduler(taskScheduler, jobLauncher, applicationContext);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(future);
    }

    @Test
    @DisplayName("scheduleJob registers a trigger and tracks the job")
    void scheduleJobTracks() {
        scheduler.scheduleJob("dailyJob", "0 0 2 * * *");

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        assertThat(scheduler.listScheduledJobs()).containsExactly("dailyJob");
    }

    @Test
    @DisplayName("scheduleJob replaces an existing schedule for the same job name")
    void scheduleJobReplacesExisting() {
        scheduler.scheduleJob("job", "0 0 * * * *");
        scheduler.scheduleJob("job", "0 30 * * * *");

        // First future should have been cancelled when re-scheduling
        verify(future).cancel(false);
        assertThat(scheduler.listScheduledJobs()).containsExactly("job");
    }

    @Test
    @DisplayName("scheduled task runnable launches the job successfully")
    void runnableLaunchesJob() throws Exception {
        when(applicationContext.getBean("myJob", Job.class)).thenReturn(job);

        scheduler.scheduleJob("myJob", "0 0 * * * *");

        Runnable task = captureTask();
        task.run();

        verify(applicationContext).getBean("myJob", Job.class);
        verify(jobLauncher).run(eq(job), any(JobParameters.class));
    }

    @Test
    @DisplayName("scheduled task swallows exceptions from job launch")
    void runnableHandlesFailure() {
        when(applicationContext.getBean("badJob", Job.class))
                .thenThrow(new IllegalArgumentException("no such bean"));

        scheduler.scheduleJob("badJob", "0 0 * * * *");

        Runnable task = captureTask();
        // Should not propagate the exception
        task.run();

        verify(applicationContext).getBean("badJob", Job.class);
    }

    @Test
    @DisplayName("cancelScheduledJob returns true and cancels a known job")
    void cancelKnownJob() {
        scheduler.scheduleJob("job", "0 0 * * * *");

        boolean cancelled = scheduler.cancelScheduledJob("job");

        assertThat(cancelled).isTrue();
        verify(future).cancel(false);
        assertThat(scheduler.listScheduledJobs()).isEmpty();
    }

    @Test
    @DisplayName("cancelScheduledJob returns false for an unknown job")
    void cancelUnknownJob() {
        assertThat(scheduler.cancelScheduledJob("ghost")).isFalse();
    }

    @Test
    @DisplayName("listScheduledJobs returns an unmodifiable view")
    void listIsUnmodifiable() {
        scheduler.scheduleJob("job", "0 0 * * * *");

        var jobs = scheduler.listScheduledJobs();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jobs.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("with a lock held elsewhere, the job is skipped on this instance")
    void skipsWhenLockHeldElsewhere() throws Exception {
        var lock = mock(com.adhar.kit.batch.lock.SchedulerLock.class);
        when(lock.tryLock(eq("lockedJob"), any(java.time.Duration.class))).thenReturn(false);
        var lockingScheduler = new BatchScheduler(taskScheduler, jobLauncher, applicationContext,
                lock, java.time.Duration.ofMinutes(1));

        lockingScheduler.runScheduled("lockedJob");

        verify(applicationContext, org.mockito.Mockito.never()).getBean("lockedJob", Job.class);
        verify(jobLauncher, org.mockito.Mockito.never()).run(any(Job.class), any(JobParameters.class));
        verify(lock, org.mockito.Mockito.never()).unlock("lockedJob");
    }

    @Test
    @DisplayName("with a lock acquired, the job runs and the lock is released")
    void runsAndReleasesLockWhenAcquired() throws Exception {
        var lock = mock(com.adhar.kit.batch.lock.SchedulerLock.class);
        when(lock.tryLock(eq("myJob"), any(java.time.Duration.class))).thenReturn(true);
        when(applicationContext.getBean("myJob", Job.class)).thenReturn(job);
        var lockingScheduler = new BatchScheduler(taskScheduler, jobLauncher, applicationContext,
                lock, java.time.Duration.ofMinutes(1));

        lockingScheduler.runScheduled("myJob");

        verify(jobLauncher).run(eq(job), any(JobParameters.class));
        verify(lock).unlock("myJob");
    }

    @Test
    @DisplayName("the lock is released even when the job launch fails")
    void releasesLockOnFailure() {
        var lock = mock(com.adhar.kit.batch.lock.SchedulerLock.class);
        when(lock.tryLock(eq("badJob"), any(java.time.Duration.class))).thenReturn(true);
        when(applicationContext.getBean("badJob", Job.class))
                .thenThrow(new IllegalArgumentException("no such bean"));
        var lockingScheduler = new BatchScheduler(taskScheduler, jobLauncher, applicationContext,
                lock, java.time.Duration.ofMinutes(1));

        lockingScheduler.runScheduled("badJob");

        verify(lock).unlock("badJob");
    }

    private Runnable captureTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Trigger.class));
        return captor.getValue();
    }
}
