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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The paths of {@link BatchScheduler} that need its threads: firing, catching up, overruns, lock
 * renewal and shutdown. Time is a clock the test moves; a slot's delay is computed from it, so
 * nothing here waits for the wall clock. Threads are coordinated with latches, whose timeouts only
 * bound a failure and never decide a result, so a CPU-contended CI node slows these tests down
 * without failing them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchSchedulerFiringTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");
    private static final String EVERY_FIVE_MINUTES = "0 */5 * * * *";
    private static final String HOURLY = "0 0 * * * *";
    private static final String NEVER_FIRES = "0 0 0 31 2 *";
    private static final long PATIENCE_MS = 60_000;
    private static final Duration LEASE = Duration.ofMinutes(2);

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

    private final JobInstance instance = new JobInstance(7L, "job");
    private final BatchProperties.Scheduler settings = new BatchProperties.Scheduler();
    private final MovableClock clock = new MovableClock(NOON);
    private Duration shutdownTimeout = Duration.ofSeconds(PATIENCE_MS / 1000);

    private BatchScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        when(job.getName()).thenReturn("job");
        when(jobResolver.resolve("job")).thenReturn(job);
        when(lock.tryLock(eq("job"), any(Duration.class))).thenReturn(true);
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(ended(BatchStatus.COMPLETED));
        settings.setLockLease(LEASE);
        // Far longer than any test: only the tests about renewal shorten it.
        settings.setLockRenewInterval(Duration.ofHours(1));
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    private BatchScheduler scheduler(SchedulerLock schedulerLock) {
        scheduler = new BatchScheduler(jobOperator, jobRepository, jobResolver, schedulerLock, settings,
                shutdownTimeout, clock);
        return scheduler;
    }

    private BatchScheduler started() {
        BatchScheduler started = scheduler(lock);
        started.start();
        return started;
    }

    private JobExecution ended(BatchStatus status) {
        return execution(status);
    }

    private JobExecution execution(BatchStatus status) {
        var execution = new JobExecution(1L, instance, BatchScheduler.slotParameters(NOON));
        execution.setStatus(status);
        return execution;
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(PATIENCE_MS, TimeUnit.MILLISECONDS)).as("waited for the other thread").isTrue();
    }

    private void slotStartedThen(Answer<JobExecution> during) throws Exception {
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenAnswer(during);
    }

    /**
     * Matches the parameters of one slot. {@code JobParameters.equals} cannot: a {@code JobParameter}
     * equals another of the same name whatever its value, so every slot's parameters are equal.
     */
    private static JobParameters slot(Instant nominal) {
        String expected = BatchScheduler.slotParameters(nominal).getString(BatchScheduler.SCHEDULED_TIME_PARAMETER);
        return argThat(parameters -> parameters != null
                && expected.equals(parameters.getString(BatchScheduler.SCHEDULED_TIME_PARAMETER)));
    }

    /** The slot's JobInstance does not exist before the start, and does once the run is on. */
    private void instanceExistsOnceStarted() {
        when(jobRepository.getJobInstance(eq("job"), any(JobParameters.class))).thenReturn(null).thenReturn(instance);
    }

    private void awaitNextFire(Instant expected) {
        await().atMost(Duration.ofMillis(PATIENCE_MS))
                .untilAsserted(() -> assertThat(scheduler.nextFireTime("job")).contains(expected));
    }

    /** Lets the renewal thread finish anything due before a moment well after its next tick. */
    private void drainRenewals() throws Exception {
        var renewer = (ScheduledExecutorService) ReflectionTestUtils.getField(scheduler, "renewer");
        renewer.schedule(() -> { }, 50, TimeUnit.MILLISECONDS).get(PATIENCE_MS, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------
    // Catching up when the scheduler starts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a slot due within the misfire threshold runs when the scheduler starts, then the next is armed")
    void catchUpWithinTheThreshold() throws Exception {
        clock.set(NOON.plusSeconds(30));
        settings.setJobs(Map.of("job", HOURLY));

        started();

        verify(jobOperator, timeout(PATIENCE_MS)).start(eq(job), slot(NOON));
        awaitNextFire(NOON.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("catching up runs only the latest of the slots within the threshold")
    void catchUpRunsOnlyTheLatest() throws Exception {
        // 12:05 and 12:10 are both within the ten-minute threshold of 12:11; only the latest runs.
        clock.set(NOON.plus(Duration.ofMinutes(11)));
        settings.setJobs(Map.of("job", EVERY_FIVE_MINUTES));

        started();

        verify(jobOperator, timeout(PATIENCE_MS)).start(eq(job), slot(NOON.plus(Duration.ofMinutes(10))));
        awaitNextFire(NOON.plus(Duration.ofMinutes(15)));
        verify(jobOperator, times(1)).start(eq(job), any(JobParameters.class));
    }

    @Test
    @DisplayName("a slot older than the misfire threshold is not caught up: the next one is armed instead")
    void noCatchUpBeyondTheThreshold() throws Exception {
        clock.set(NOON.plus(settings.getMisfireThreshold()).plusSeconds(1));
        settings.setJobs(Map.of("job", HOURLY));

        started();

        assertThat(scheduler.nextFireTime("job")).contains(NOON.plus(Duration.ofHours(1)));
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("a schedule registered while running starts with its next slot, never one already due")
    void noCatchUpForALateRegistration() throws Exception {
        BatchScheduler running = started();

        running.scheduleJob("job", "0 0 12 * * *");

        assertThat(running.nextFireTime("job")).contains(NOON.plus(Duration.ofDays(1)));
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("slots are computed in the configured time zone")
    void timeZone() {
        settings.setTimeZone(ZoneId.of("Asia/Singapore"));
        settings.setJobs(Map.of("job", "0 0 2 * * *"));

        started();

        // 02:00 in Singapore (UTC+8) is 18:00 UTC of the day before.
        assertThat(scheduler.nextFireTime("job")).contains(Instant.parse("2026-11-06T18:00:00Z"));
    }

    @Test
    @DisplayName("a cron with no future fire time arms nothing and does not fail the start")
    void cronWithNoFutureFire() {
        settings.setJobs(Map.of("job", NEVER_FIRES));

        BatchScheduler running = started();

        assertThat(running.isRunning()).isTrue();
        assertThat(running.nextFireTime("job")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Overruns
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an overrun skips the slots that passed except the latest, which still runs")
    void overrunRunsOnlyTheLatestSlot() throws Exception {
        // Slots closer together than the ten-minute threshold: 12:15 could still run, and must not.
        settings.setJobs(Map.of("job", EVERY_FIVE_MINUTES));
        Instant latest = NOON.plus(Duration.ofMinutes(20));
        when(jobOperator.start(eq(job), slot(NOON))).thenAnswer(call -> {
            clock.set(latest.plusSeconds(60));
            return ended(BatchStatus.COMPLETED);
        });

        started();

        verify(jobOperator, timeout(PATIENCE_MS)).start(eq(job), slot(latest));
        awaitNextFire(latest.plus(Duration.ofMinutes(5)));
        verify(jobOperator).start(eq(job), slot(NOON));
        verify(jobOperator, never()).start(eq(job), slot(NOON.plus(Duration.ofMinutes(5))));
        verify(jobOperator, never()).start(eq(job), slot(NOON.plus(Duration.ofMinutes(10))));
        verify(jobOperator, never()).start(eq(job), slot(NOON.plus(Duration.ofMinutes(15))));
        verify(jobOperator, times(2)).start(eq(job), any(JobParameters.class));
    }

    @Test
    @DisplayName("an overrun whose latest slot is beyond the threshold misfires it and arms the next")
    void overrunBeyondTheThreshold() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));
        when(jobOperator.start(eq(job), slot(NOON))).thenAnswer(call -> {
            clock.set(NOON.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(30)));
            return ended(BatchStatus.COMPLETED);
        });

        started();

        awaitNextFire(NOON.plus(Duration.ofHours(4)));
        verify(jobOperator, times(1)).start(eq(job), any(JobParameters.class));
    }

    @Test
    @DisplayName("a run that ends before the next slot simply arms it")
    void noOverrun() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));

        started();

        awaitNextFire(NOON.plus(Duration.ofHours(1)));
        verify(jobOperator, times(1)).start(eq(job), any(JobParameters.class));
    }

    @Test
    @DisplayName("a slot that blows up does not end the schedule")
    void aFailureKeepsTheSchedule() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));
        doThrow(new IllegalStateException("lock table gone")).when(lock).unlock("job");

        started();

        awaitNextFire(NOON.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("cancelling during a run lets it finish and arms nothing after it")
    void cancelDuringARun() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));
        var finished = new CountDownLatch(1);
        slotStartedThen(call -> {
            assertThat(scheduler.cancelScheduledJob("job")).isTrue();
            return ended(BatchStatus.COMPLETED);
        });
        doAnswer(call -> {
            finished.countDown();
            return null;
        }).when(lock).unlock("job");

        started();
        awaitLatch(finished);

        var pool = (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(scheduler, "executor");
        await().atMost(Duration.ofMillis(PATIENCE_MS)).until(() -> pool.getActiveCount() == 0);
        assertThat(pool.getQueue()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Rescheduling
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scheduling a job again while running replaces its armed slot")
    void replacesTheArmedSlot() {
        BatchScheduler running = started();

        running.scheduleJob("job", "0 0 13 * * *");
        assertThat(running.nextFireTime("job")).contains(NOON.plus(Duration.ofHours(1)));

        running.scheduleJob("job", "0 0 14 * * *");
        assertThat(running.nextFireTime("job")).contains(NOON.plus(Duration.ofHours(2)));

        var pool = (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(running, "executor");
        assertThat(pool.getQueue()).hasSize(1);
    }

    @Test
    @DisplayName("cancelling while running removes the armed slot")
    void cancelRemovesTheArmedSlot() {
        BatchScheduler running = started();
        running.scheduleJob("job", "0 0 13 * * *");

        assertThat(running.cancelScheduledJob("job")).isTrue();

        assertThat(running.nextFireTime("job")).isEmpty();
        var pool = (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(running, "executor");
        assertThat(pool.getQueue()).isEmpty();
    }

    @Test
    @DisplayName("a job with an incrementer scheduled while running is refused")
    void incrementerWhileRunning() {
        when(job.getJobParametersIncrementer())
                .thenReturn(org.mockito.Mockito.mock(
                        org.springframework.batch.core.job.parameters.JobParametersIncrementer.class));
        BatchScheduler running = started();

        assertThatThrownBy(() -> running.scheduleJob("job", HOURLY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobParametersIncrementer");
        assertThat(running.listScheduledJobs()).isEmpty();
    }

    // ------------------------------------------------------------------
    // One run at a time in a process
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a slot firing while the job still runs here is skipped without touching the lock")
    void overlapIsSkipped() throws Exception {
        var running = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        slotStartedThen(call -> {
            running.countDown();
            awaitLatch(finish);
            return ended(BatchStatus.COMPLETED);
        });
        BatchScheduler slots = scheduler(lock);

        CompletableFuture<SlotOutcome> first = CompletableFuture.supplyAsync(() -> slots.runSlot("job", job, NOON));
        awaitLatch(running);

        assertThat(slots.runningJobs()).containsExactly("job");
        assertThat(slots.runSlot("job", job, NOON.plusSeconds(60))).isEqualTo(SlotOutcome.OVERLAPPING);

        verify(lock, times(1)).tryLock(eq("job"), any(Duration.class));
        verify(jobOperator, times(1)).start(eq(job), any(JobParameters.class));
        verify(lock, never()).unlock("job");

        finish.countDown();

        assertThat(first.get(PATIENCE_MS, TimeUnit.MILLISECONDS)).isEqualTo(SlotOutcome.RAN);
        assertThat(slots.runningJobs()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Waiting for a run on an asynchronous executor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a run started on an asynchronous executor is polled to its end while the lock stays held")
    void pollsAnAsynchronousRun() throws Exception {
        slotStartedThen(call -> execution(BatchStatus.STARTED));
        when(jobRepository.getJobExecution(1L))
                .thenReturn(execution(BatchStatus.STARTED))
                .thenReturn(ended(BatchStatus.COMPLETED));
        BatchScheduler slots = scheduler(lock);

        assertThat(slots.runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        InOrder order = inOrder(jobOperator, jobRepository, lock);
        order.verify(jobOperator).start(eq(job), any(JobParameters.class));
        order.verify(jobRepository, times(2)).getJobExecution(1L);
        order.verify(lock).unlock("job");
    }

    @Test
    @DisplayName("an execution that disappears from the repository ends the wait")
    void executionDisappears() throws Exception {
        slotStartedThen(call -> execution(BatchStatus.STARTED));
        when(jobRepository.getJobExecution(1L)).thenReturn(null);

        assertThat(scheduler(lock).runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(lock).unlock("job");
    }

    @Test
    @DisplayName("being interrupted while waiting keeps the interrupt and releases the lock")
    void interruptedWhileWaiting() throws Exception {
        slotStartedThen(call -> execution(BatchStatus.STARTED));
        BatchScheduler slots = scheduler(lock);

        Thread.currentThread().interrupt();
        try {
            assertThat(slots.runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        verify(lock).unlock("job");
        assertThat(slots.runningJobs()).isEmpty();
    }

    @Test
    @DisplayName("a failed run still counts as having run: the slot is not retried")
    void failedRunRan() throws Exception {
        slotStartedThen(call -> ended(BatchStatus.FAILED));

        assertThat(scheduler(lock).runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);
    }

    @Test
    @DisplayName("another instance claiming the slot during the start counts as already run")
    void claimedElsewhere() throws Exception {
        BatchScheduler slots = scheduler(lock);

        when(jobOperator.start(eq(job), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException("complete"))
                .thenThrow(new DataIntegrityViolationException("unique constraint"))
                .thenThrow(new OptimisticLockingFailureException("version"));

        assertThat(slots.runSlot("job", job, NOON)).isEqualTo(SlotOutcome.ALREADY_RAN);
        assertThat(slots.runSlot("job", job, NOON)).isEqualTo(SlotOutcome.ALREADY_RAN);
        assertThat(slots.runSlot("job", job, NOON)).isEqualTo(SlotOutcome.ALREADY_RAN);
        verify(lock, times(3)).unlock("job");
    }

    // ------------------------------------------------------------------
    // Lock renewal
    // ------------------------------------------------------------------

    private CountDownLatch stopRequestedFor(JobExecution execution) throws Exception {
        var stopRequested = new CountDownLatch(1);
        when(jobRepository.getLastJobExecution(instance)).thenReturn(execution);
        doAnswer(call -> {
            stopRequested.countDown();
            return true;
        }).when(jobOperator).stop(execution);
        return stopRequested;
    }

    @Test
    @DisplayName("the lock is renewed with its lease for as long as the run lasts, and not after")
    void renewsWhileRunning() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        var renewed = new CountDownLatch(3);
        when(lock.renew("job", LEASE)).thenAnswer(call -> {
            renewed.countDown();
            return true;
        });
        slotStartedThen(call -> {
            awaitLatch(renewed);
            return ended(BatchStatus.COMPLETED);
        });
        BatchScheduler running = started();

        assertThat(running.runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(jobOperator, never()).stop(any(JobExecution.class));
        verify(lock).unlock("job");

        drainRenewals();
        clearInvocations(lock);
        drainRenewals();
        verify(lock, never()).renew(any(), any());
    }

    @Test
    @DisplayName("a lock another instance took over stops the run, once, and renewing ends")
    void lostLockStopsTheRun() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        instanceExistsOnceStarted();
        JobExecution running = execution(BatchStatus.STARTED);
        CountDownLatch stopRequested = stopRequestedFor(running);
        when(lock.renew("job", LEASE)).thenReturn(false);
        slotStartedThen(call -> {
            awaitLatch(stopRequested);
            drainRenewals();
            return ended(BatchStatus.STOPPED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(jobOperator, times(1)).stop(running);
        verify(lock, times(1)).renew("job", LEASE);
    }

    @Test
    @DisplayName("a database failure while renewing is tolerated until the lease has run out, then the run stops")
    void renewalFailureToleratedUntilTheLeaseEnds() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        instanceExistsOnceStarted();
        JobExecution running = execution(BatchStatus.STARTED);
        CountDownLatch stopRequested = stopRequestedFor(running);
        var failures = new CountDownLatch(3);
        when(lock.renew("job", LEASE)).thenAnswer(call -> {
            failures.countDown();
            throw new DataAccessResourceFailureException("database down");
        });
        slotStartedThen(call -> {
            clock.set(NOON.plus(LEASE).minusSeconds(1));
            awaitLatch(failures);
            verify(jobOperator, never()).stop(any(JobExecution.class));

            clock.set(NOON.plus(LEASE));
            awaitLatch(stopRequested);
            return ended(BatchStatus.STOPPED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(jobOperator, times(1)).stop(running);
    }

    @Test
    @DisplayName("the lease that counts is the one from the last successful renewal")
    void successfulRenewalRestartsTheLease() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        instanceExistsOnceStarted();
        JobExecution running = execution(BatchStatus.STARTED);
        CountDownLatch stopRequested = stopRequestedFor(running);
        Instant renewedAt = NOON.plusSeconds(100);
        var attempts = new AtomicInteger();
        var failures = new CountDownLatch(3);
        when(lock.renew("job", LEASE)).thenAnswer(call -> {
            if (attempts.getAndIncrement() == 0) {
                clock.set(renewedAt);
                return true;
            }
            // Past the first lease, inside the one the renewal bought.
            clock.setIfBefore(renewedAt.plus(LEASE).minusSeconds(1));
            failures.countDown();
            throw new DataAccessResourceFailureException("database down");
        });
        slotStartedThen(call -> {
            awaitLatch(failures);
            verify(jobOperator, never()).stop(any(JobExecution.class));

            clock.set(renewedAt.plus(LEASE));
            awaitLatch(stopRequested);
            return ended(BatchStatus.STOPPED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);
    }

    @Test
    @DisplayName("a lost lock with nothing left running stops nothing")
    void lostLockAfterTheRunEnded() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        instanceExistsOnceStarted();
        var lost = new CountDownLatch(1);
        when(jobRepository.getLastJobExecution(instance)).thenAnswer(call -> {
            lost.countDown();
            return ended(BatchStatus.COMPLETED);
        });
        when(lock.renew("job", LEASE)).thenReturn(false);
        slotStartedThen(call -> {
            awaitLatch(lost);
            drainRenewals();
            return ended(BatchStatus.COMPLETED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(jobOperator, never()).stop(any(JobExecution.class));
    }

    @Test
    @DisplayName("a lost lock before the slot's JobInstance exists has nothing to stop")
    void lostLockWithoutAnInstance() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        var lost = new CountDownLatch(1);
        when(lock.renew("job", LEASE)).thenAnswer(call -> {
            lost.countDown();
            return false;
        });
        slotStartedThen(call -> {
            awaitLatch(lost);
            drainRenewals();
            return ended(BatchStatus.COMPLETED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(jobOperator, never()).stop(any(JobExecution.class));
        verify(jobRepository, never()).getLastJobExecution(any(JobInstance.class));
    }

    @Test
    @DisplayName("a run that ended just before the stop request is not an error")
    void stopRacesTheEnd() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        instanceExistsOnceStarted();
        JobExecution running = execution(BatchStatus.STARTED);
        when(jobRepository.getLastJobExecution(instance)).thenReturn(running);
        var stopAttempted = new CountDownLatch(1);
        doAnswer(call -> {
            stopAttempted.countDown();
            throw new JobExecutionNotRunningException("already ended");
        }).when(jobOperator).stop(running);
        when(lock.renew("job", LEASE)).thenReturn(false);
        slotStartedThen(call -> {
            awaitLatch(stopAttempted);
            return ended(BatchStatus.COMPLETED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);

        verify(lock).unlock("job");
    }

    @Test
    @DisplayName("a stop request that fails leaves the renewal thread alive for other jobs")
    void stopRequestFails() throws Exception {
        settings.setLockRenewInterval(Duration.ofMillis(1));
        instanceExistsOnceStarted();
        var attempted = new CountDownLatch(1);
        when(jobRepository.getLastJobExecution(instance)).thenAnswer(call -> {
            attempted.countDown();
            throw new DataAccessResourceFailureException("database down");
        });
        when(lock.renew("job", LEASE)).thenReturn(false);
        slotStartedThen(call -> {
            awaitLatch(attempted);
            // The single renewal thread still takes work.
            drainRenewals();
            return ended(BatchStatus.COMPLETED);
        });

        assertThat(started().runSlot("job", job, NOON)).isEqualTo(SlotOutcome.RAN);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("stop asks the running job to stop and waits for it to end")
    void stopWaitsForTheRun() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));
        instanceExistsOnceStarted();
        JobExecution running = execution(BatchStatus.STARTED);
        CountDownLatch stopRequested = stopRequestedFor(running);
        var runIsOn = new CountDownLatch(1);
        slotStartedThen(call -> {
            runIsOn.countDown();
            awaitLatch(stopRequested);
            return ended(BatchStatus.STOPPED);
        });
        BatchScheduler scheduled = started();
        awaitLatch(runIsOn);

        scheduled.stop();

        verify(jobOperator).stop(running);
        verify(lock).unlock("job");
        assertThat(scheduled.isRunning()).isFalse();
        assertThat(scheduled.runningJobs()).isEmpty();
        assertThat(scheduled.nextFireTime("job")).isEmpty();
    }

    @Test
    @DisplayName("a run that outlasts the shutdown timeout is interrupted")
    void shutdownTimeout() throws Exception {
        shutdownTimeout = Duration.ofMillis(10);
        settings.setJobs(Map.of("job", HOURLY));
        var runIsOn = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        slotStartedThen(call -> {
            runIsOn.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException ex) {
                interrupted.countDown();
                throw ex;
            }
            return ended(BatchStatus.COMPLETED);
        });
        BatchScheduler running = started();
        awaitLatch(runIsOn);

        running.stop();

        awaitLatch(interrupted);
        verify(lock, timeout(PATIENCE_MS)).unlock("job");
        assertThat(running.isRunning()).isFalse();
    }

    @Test
    @DisplayName("a stop interrupted while waiting keeps the interrupt and ends the runs")
    void interruptedWhileStopping() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));
        var runIsOn = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        slotStartedThen(call -> {
            runIsOn.countDown();
            finish.await();
            return ended(BatchStatus.COMPLETED);
        });
        BatchScheduler running = started();
        awaitLatch(runIsOn);

        Thread.currentThread().interrupt();
        try {
            running.stop();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            finish.countDown();
        }
        assertThat(running.isRunning()).isFalse();
    }

    @Test
    @DisplayName("start and stop are idempotent")
    void idempotentLifecycle() {
        BatchScheduler lifecycle = scheduler(lock);

        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();

        lifecycle.start();
        lifecycle.start();
        verify(lock, times(1)).verify();

        lifecycle.stop();
        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    @DisplayName("a stopped scheduler can start again and arms its schedules anew")
    void restarts() {
        settings.setJobs(Map.of("job", "0 0 13 * * *"));
        BatchScheduler lifecycle = started();
        lifecycle.stop();
        assertThat(lifecycle.nextFireTime("job")).isEmpty();

        lifecycle.start();

        assertThat(lifecycle.nextFireTime("job")).contains(NOON.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("without a lock the scheduler starts, fires and never asks for one")
    void noLock() throws Exception {
        settings.setJobs(Map.of("job", HOURLY));
        BatchScheduler noLock = scheduler(null);

        noLock.start();

        verify(jobOperator, timeout(PATIENCE_MS)).start(eq(job), slot(NOON));
        awaitNextFire(NOON.plus(Duration.ofHours(1)));
        verify(lock, never()).tryLock(any(), any());
    }

    @Test
    @DisplayName("a job scheduled before start is resolved at start, and an unknown one fails it")
    void unknownJobFailsTheStart() {
        when(jobResolver.resolve("ghost")).thenThrow(new IllegalArgumentException("No Job named [ghost]"));
        BatchScheduler lifecycle = scheduler(lock);
        lifecycle.scheduleJob("ghost", HOURLY);

        assertThatThrownBy(lifecycle::start).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
        assertThat(lifecycle.isRunning()).isFalse();
    }

    /** A clock the test moves. */
    private static final class MovableClock extends Clock {

        private volatile Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        private void set(Instant instant) {
            now = instant;
        }

        private synchronized void setIfBefore(Instant instant) {
            if (now.isBefore(instant)) {
                now = instant;
            }
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
