package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.scheduler.BatchScheduler.SlotOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersIncrementer;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchScheduler}. A slot is driven through {@code runSlot} with a fixed
 * clock, so what the cron thread does at the slot's time is tested without waiting for it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final String DAILY = "0 0 2 * * *";

    @Mock
    private JobOperator jobOperator;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobResolver jobResolver;
    @Mock
    private SchedulerLock lock;
    @Mock
    private Job job;

    private final BatchProperties.Scheduler settings = new BatchProperties.Scheduler();

    private BatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(job.getName()).thenReturn("job");
        when(jobResolver.resolve("job")).thenReturn(job);
        scheduler = scheduler(lock);
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    private BatchScheduler scheduler(SchedulerLock schedulerLock) {
        return new BatchScheduler(jobOperator, jobRepository, jobResolver, schedulerLock, settings,
                Duration.ofSeconds(5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JobExecution completed() {
        var execution = new JobExecution(1L, new JobInstance(1L, "job"), BatchScheduler.slotParameters(NOW));
        execution.setStatus(BatchStatus.COMPLETED);
        return execution;
    }

    private void lockFree() {
        when(lock.tryLock(eq("job"), any(Duration.class))).thenReturn(true);
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scheduleJob tracks the job, and scheduling it again replaces the schedule")
    void scheduleJobTracks() {
        scheduler.scheduleJob("job", DAILY);
        scheduler.scheduleJob("job", "0 30 * * * *");

        assertThat(scheduler.listScheduledJobs()).containsExactly("job");
    }

    @Test
    @DisplayName("scheduleJob names the job in the refusal of an invalid cron expression")
    void invalidCron() {
        assertThatThrownBy(() -> scheduler.scheduleJob("job", "not a cron"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("job")
                .hasMessageContaining("six fields");
        assertThat(scheduler.listScheduledJobs()).isEmpty();
    }

    @Test
    @DisplayName("declarative schedules come from adhar.batch.scheduler.jobs")
    void declarativeSchedules() {
        settings.setJobs(Map.of("job", DAILY));

        assertThat(scheduler(null).listScheduledJobs()).containsExactly("job");
    }

    @Test
    @DisplayName("cancelScheduledJob returns true for a known job")
    void cancelKnownJob() {
        scheduler.scheduleJob("job", DAILY);

        assertThat(scheduler.cancelScheduledJob("job")).isTrue();
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
        scheduler.scheduleJob("job", DAILY);

        var jobs = scheduler.listScheduledJobs();

        assertThatThrownBy(() -> jobs.add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------------------
    // Slot identity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a slot's identity is its nominal time in whole seconds; the trigger is not identifying")
    void slotParameters() {
        JobParameters parameters = BatchScheduler.slotParameters(Instant.parse("2026-11-06T02:00:00.750Z"));

        assertThat(parameters.getString(BatchScheduler.SCHEDULED_TIME_PARAMETER)).isEqualTo("2026-11-06T02:00:00Z");
        assertThat(parameters.getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER).identifying()).isTrue();
        assertThat(parameters.getString(BatchScheduler.TRIGGER_PARAMETER)).isEqualTo(BatchScheduler.EMBEDDED_TRIGGER);
        assertThat(parameters.getParameter(BatchScheduler.TRIGGER_PARAMETER).identifying()).isFalse();
    }

    // ------------------------------------------------------------------
    // A slot
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a slot with its lock acquired runs the job and releases the lock")
    void runsAndReleasesLockWhenAcquired() throws Exception {
        lockFree();
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(completed());

        assertThat(scheduler.runSlot("job", job, NOW)).isEqualTo(SlotOutcome.RAN);

        verify(jobOperator).start(job, BatchScheduler.slotParameters(NOW));
        verify(lock).unlock("job");
        assertThat(scheduler.runningJobs()).isEmpty();
    }

    @Test
    @DisplayName("a slot whose lock is held elsewhere is skipped, and that lock is not released")
    void skipsWhenLockHeldElsewhere() throws Exception {
        when(lock.tryLock(eq("job"), any(Duration.class))).thenReturn(false);

        assertThat(scheduler.runSlot("job", job, NOW)).isEqualTo(SlotOutcome.LOCKED_ELSEWHERE);

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
        verify(lock, never()).unlock("job");
    }

    @Test
    @DisplayName("a slot later than the misfire threshold is skipped before taking the lock")
    void misfire() throws Exception {
        Instant nominal = NOW.minus(settings.getMisfireThreshold()).minusSeconds(1);

        assertThat(scheduler.runSlot("job", job, nominal)).isEqualTo(SlotOutcome.MISFIRED);

        verify(lock, never()).tryLock(any(), any());
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("a slot exactly at the misfire threshold still runs")
    void atTheMisfireThreshold() throws Exception {
        lockFree();
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(completed());

        assertThat(scheduler.runSlot("job", job, NOW.minus(settings.getMisfireThreshold())))
                .isEqualTo(SlotOutcome.RAN);
    }

    @Test
    @DisplayName("a slot that already has a JobInstance is not run twice")
    void alreadyRan() throws Exception {
        lockFree();
        when(jobRepository.getJobInstance("job", BatchScheduler.slotParameters(NOW)))
                .thenReturn(new JobInstance(7L, "job"));

        assertThat(scheduler.runSlot("job", job, NOW)).isEqualTo(SlotOutcome.ALREADY_RAN);

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
        verify(lock).unlock("job");
    }

    @Test
    @DisplayName("another instance claiming the slot during the start counts as already run")
    void claimedDuringTheStart() throws Exception {
        lockFree();
        when(jobOperator.start(eq(job), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("running"));

        assertThat(scheduler.runSlot("job", job, NOW)).isEqualTo(SlotOutcome.ALREADY_RAN);
    }

    @Test
    @DisplayName("the lock is released even when the job cannot start")
    void releasesLockOnFailure() throws Exception {
        lockFree();
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenThrow(new IllegalStateException("boom"));

        assertThat(scheduler.runSlot("job", job, NOW)).isEqualTo(SlotOutcome.FAILED_TO_START);

        verify(lock).unlock("job");
        assertThat(scheduler.runningJobs()).isEmpty();
    }

    @Test
    @DisplayName("without a lock, a slot still runs")
    void runsWithoutALock() throws Exception {
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(completed());

        assertThat(scheduler(null).runSlot("job", job, NOW)).isEqualTo(SlotOutcome.RAN);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("start arms the next slot and stop disarms it")
    void startAndStop() {
        scheduler.scheduleJob("job", DAILY);

        scheduler.start();

        assertThat(scheduler.isRunning()).isTrue();
        assertThat(scheduler.nextFireTime("job")).contains(Instant.parse("2026-11-07T02:00:00Z"));

        scheduler.stop();

        assertThat(scheduler.isRunning()).isFalse();
        assertThat(scheduler.nextFireTime("job")).isEmpty();
    }

    @Test
    @DisplayName("stop is the first thing to happen on shutdown")
    void stopsFirst() {
        assertThat(scheduler.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("start fails when the lock cannot be used")
    void startVerifiesTheLock() {
        org.mockito.Mockito.doThrow(new IllegalStateException("no table")).when(lock).verify();

        assertThatThrownBy(scheduler::start).isInstanceOf(IllegalStateException.class).hasMessage("no table");
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    @DisplayName("start refuses a job with an incrementer, which would ignore the slot's identity")
    void refusesAnIncrementer() {
        when(job.getJobParametersIncrementer()).thenReturn(mock(JobParametersIncrementer.class));
        scheduler.scheduleJob("job", DAILY);

        assertThatThrownBy(scheduler::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobParametersIncrementer");
    }

    @Test
    @DisplayName("scheduling an unknown job while running is refused")
    void unknownJobWhileRunning() {
        when(jobResolver.resolve("ghost")).thenThrow(new IllegalArgumentException("No Job named [ghost]"));
        scheduler.start();

        assertThatThrownBy(() -> scheduler.scheduleJob("ghost", DAILY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(scheduler.listScheduledJobs()).isEmpty();
    }
}
