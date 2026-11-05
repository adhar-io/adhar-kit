package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.operator.JobResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded cron scheduler for a service that also runs a small job (design 0025 §5.16). The
 * platform path for batch is a Kubernetes CronJob per job; this one runs inside the service.
 *
 * <p>Every slot of a schedule has a deterministic identity: the job runs with the identifying
 * parameter {@value #SCHEDULED_TIME_PARAMETER} = the slot's nominal fire time (ISO-8601 UTC,
 * whole seconds) and the non-identifying {@value #TRIGGER_PARAMETER} = {@value #EMBEDDED_TRIGGER}.
 * A slot is therefore one JobInstance, however many instances of the service fire it, and it runs
 * exactly once:</p>
 * <ol>
 *   <li>A fire later than {@code adhar.batch.scheduler.misfire-threshold} is skipped and logged.</li>
 *   <li>The fire takes the job's {@link SchedulerLock} and holds it, renewed every
 *       {@code lock-renew-interval}, until the run has ended; another instance firing the same
 *       slot finds the lock held and skips it. A run that loses its lock stops at the next chunk
 *       boundary rather than overlap another instance's run.</li>
 *   <li>Holding the lock, it skips a slot whose JobInstance already exists (run elsewhere,
 *       earlier, or still running).</li>
 *   <li>It starts the job synchronously through {@link JobOperator#start(Job, JobParameters)} and
 *       waits for the end even when the operator's task executor is asynchronous.</li>
 * </ol>
 * <p>One job never runs twice at once in a process. When a run overruns, the slots that passed
 * meanwhile are skipped except the latest, which still runs if it is within the misfire
 * threshold (a Kubernetes CronJob's behaviour). When the scheduler starts, the latest slot due
 * within the misfire threshold runs once, so a slot is not lost to a restart; the JobInstance
 * check keeps that from running a slot twice. A job with a {@code JobParametersIncrementer}
 * cannot be scheduled: Spring Batch would ignore the slot identity and start a new instance on
 * every fire.</p>
 *
 * <p>On shutdown ({@link SmartLifecycle}, the first phase to stop) it stops firing, asks every
 * running job to stop at its next chunk boundary and waits up to
 * {@code adhar.batch.shutdown.timeout} for the runs to record STOPPED.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchScheduler implements SmartLifecycle {

    /** Identifying parameter carrying a slot's nominal fire time. */
    public static final String SCHEDULED_TIME_PARAMETER = "adhar.scheduled-time";

    /** Non-identifying provenance parameter naming what started the run. */
    public static final String TRIGGER_PARAMETER = "adhar.trigger";

    /** {@value #TRIGGER_PARAMETER} of runs started by this scheduler. */
    public static final String EMBEDDED_TRIGGER = "embedded";

    /** How often a run started on an asynchronous task executor is polled for its end. */
    static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final JobResolver jobResolver;
    private final SchedulerLock lock;
    private final Clock clock;
    private final ZoneId zone;
    private final Duration misfireThreshold;
    private final Duration lockLease;
    private final Duration lockRenewInterval;
    private final Duration shutdownTimeout;
    private final int poolSize;

    private final Map<String, ScheduledJob> schedules = new ConcurrentHashMap<>();
    private final Map<String, SlotRun> running = new ConcurrentHashMap<>();
    private final Object lifecycleMonitor = new Object();
    private volatile ScheduledThreadPoolExecutor executor;
    private volatile ScheduledExecutorService renewer;
    private volatile boolean started;

    /**
     * @param jobOperator     starts and stops the jobs
     * @param jobRepository   answers whether a slot already has a JobInstance
     * @param jobResolver     finds a {@link Job} by name
     * @param lock            the lock shared by the service's instances, {@code null} for none
     * @param settings        {@code adhar.batch.scheduler.*}
     * @param shutdownTimeout {@code adhar.batch.shutdown.timeout}
     * @param clock           the time source
     */
    public BatchScheduler(JobOperator jobOperator, JobRepository jobRepository, JobResolver jobResolver,
                          SchedulerLock lock, BatchProperties.Scheduler settings, Duration shutdownTimeout,
                          Clock clock) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.jobResolver = jobResolver;
        this.lock = lock;
        this.clock = clock;
        this.zone = settings.getTimeZone();
        this.misfireThreshold = settings.getMisfireThreshold();
        this.lockLease = settings.getLockLease();
        this.lockRenewInterval = settings.getLockRenewInterval();
        this.poolSize = settings.getPoolSize();
        this.shutdownTimeout = shutdownTimeout;
        settings.getJobs().forEach(this::register);
    }

    /**
     * The parameters of a scheduled slot: its identity and its provenance.
     *
     * @param nominal the slot's nominal fire time
     * @return {@value #SCHEDULED_TIME_PARAMETER} (identifying) and {@value #TRIGGER_PARAMETER}
     */
    public static JobParameters slotParameters(Instant nominal) {
        return new JobParametersBuilder()
                .addString(SCHEDULED_TIME_PARAMETER,
                        DateTimeFormatter.ISO_INSTANT.format(nominal.truncatedTo(ChronoUnit.SECONDS)), true)
                .addString(TRIGGER_PARAMETER, EMBEDDED_TRIGGER, false)
                .toJobParameters();
    }

    /**
     * Schedules a job, replacing any schedule it already has. A schedule registered while the
     * scheduler runs starts with its next slot.
     *
     * @param jobName        the job's bean name or {@code Job#getName()}
     * @param cronExpression six fields, seconds first, in {@code adhar.batch.scheduler.time-zone}
     * @throws IllegalArgumentException for an invalid cron expression, and (once the scheduler
     *                                  runs) for an unknown job or one with an incrementer
     */
    public void scheduleJob(String jobName, String cronExpression) {
        register(jobName, cronExpression);
    }

    private void register(String jobName, String cronExpression) {
        Assert.hasText(jobName, "jobName must not be empty");
        CronExpression expression;
        try {
            expression = CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid cron expression '" + cronExpression + "' for job ["
                    + jobName + "]: " + ex.getMessage() + " (six fields, seconds first)", ex);
        }
        ScheduledJob scheduled = new ScheduledJob(jobName, cronExpression, expression, zone);
        synchronized (lifecycleMonitor) {
            if (started) {
                scheduled.resolve(jobResolver);
            }
            ScheduledJob previous = schedules.put(jobName, scheduled);
            if (previous != null) {
                previous.remove();
            }
            if (started) {
                arm(scheduled, clock.instant(), false);
            }
        }
        log.info("Scheduled job [{}] with cron '{}' in {}", jobName, cronExpression, zone);
    }

    /**
     * Removes a schedule. A run already in progress finishes.
     *
     * @param jobName the name it was scheduled under
     * @return {@code true} if it was scheduled
     */
    public boolean cancelScheduledJob(String jobName) {
        ScheduledJob scheduled = schedules.remove(jobName);
        if (scheduled == null) {
            return false;
        }
        scheduled.remove();
        log.info("Cancelled the schedule of job [{}]", jobName);
        return true;
    }

    /**
     * @return the scheduled job names, sorted
     */
    public Set<String> listScheduledJobs() {
        return Collections.unmodifiableSet(new TreeSet<>(schedules.keySet()));
    }

    /**
     * @param jobName the name a job was scheduled under
     * @return the nominal time of its next armed fire, while the scheduler runs
     */
    public Optional<Instant> nextFireTime(String jobName) {
        ScheduledJob scheduled = schedules.get(jobName);
        return scheduled == null ? Optional.empty() : Optional.ofNullable(scheduled.nextFire);
    }

    /**
     * @return the names of the jobs a scheduled run is executing right now
     */
    public Set<String> runningJobs() {
        return Collections.unmodifiableSet(new TreeSet<>(running.keySet()));
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (started) {
                return;
            }
            if (lock != null) {
                lock.verify();
            }
            schedules.values().forEach(scheduled -> scheduled.resolve(jobResolver));
            ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(poolSize,
                    threadFactory("adhar-batch-scheduler-"));
            pool.setRemoveOnCancelPolicy(true);
            pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor = pool;
            renewer = Executors.newSingleThreadScheduledExecutor(threadFactory("adhar-batch-lock-renewal-"));
            started = true;
            Instant now = clock.instant();
            schedules.values().forEach(scheduled -> arm(scheduled, now, true));
        }
        log.info("Embedded batch scheduler started: {} job(s) in {}, lock {}, misfire threshold {}",
                schedules.size(), zone, lock != null ? lock.getClass().getSimpleName() : "none", misfireThreshold);
    }

    @Override
    public void stop() {
        ScheduledThreadPoolExecutor pool;
        List<SlotRun> inFlight;
        synchronized (lifecycleMonitor) {
            if (!started) {
                return;
            }
            started = false;
            schedules.values().forEach(ScheduledJob::disarm);
            pool = executor;
            pool.shutdown();
            inFlight = new ArrayList<>(running.values());
        }
        inFlight.forEach(run -> requestStop(run, "the application is shutting down"));
        boolean finished = awaitTermination(pool, shutdownTimeout);
        if (!finished) {
            log.warn("{} scheduled run(s) did not stop within adhar.batch.shutdown.timeout ({}): {}",
                    running.size(), shutdownTimeout, running.keySet());
            pool.shutdownNow();
        }
        renewer.shutdownNow();
        log.info("Embedded batch scheduler stopped");
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public int getPhase() {
        // The highest phase stops first: running jobs stop before anything they use.
        return Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------
    // Firing
    // ------------------------------------------------------------------

    private void arm(ScheduledJob scheduled, Instant now, boolean catchUp) {
        if (catchUp) {
            Instant due = latestSlotWithinThreshold(scheduled, now);
            if (due != null) {
                log.info("Job [{}]: slot {} is due within the misfire threshold; running it now", scheduled.name, due);
                submit(scheduled, due, now);
                return;
            }
        }
        Instant next = scheduled.next(now);
        if (next == null) {
            log.warn("The cron expression '{}' of job [{}] has no future fire time", scheduled.cron, scheduled.name);
            return;
        }
        submit(scheduled, next, now);
    }

    private void armAfter(ScheduledJob scheduled, Instant previous) {
        Instant now = clock.instant();
        Instant next = scheduled.next(previous);
        Instant latestDue = null;
        int due = 0;
        while (next != null && !next.isAfter(now)) {
            latestDue = next;
            due++;
            next = scheduled.next(next);
        }
        if (due > 1) {
            log.warn("Job [{}]: {} slot(s) passed while its run for {} was in progress; only the latest one ({}) is "
                    + "considered", scheduled.name, due - 1, previous, latestDue);
        }
        if (latestDue != null) {
            submit(scheduled, latestDue, now);
        } else if (next != null) {
            submit(scheduled, next, now);
        } else {
            log.warn("The cron expression '{}' of job [{}] has no future fire time", scheduled.cron, scheduled.name);
        }
    }

    private Instant latestSlotWithinThreshold(ScheduledJob scheduled, Instant now) {
        Instant latest = null;
        Instant slot = scheduled.next(now.minus(misfireThreshold));
        while (slot != null && !slot.isAfter(now)) {
            latest = slot;
            slot = scheduled.next(slot);
        }
        return latest;
    }

    private void submit(ScheduledJob scheduled, Instant nominal, Instant now) {
        synchronized (scheduled) {
            if (scheduled.removed || !started) {
                return;
            }
            long delay = Math.max(0, Duration.between(now, nominal).toMillis());
            scheduled.nextFire = nominal;
            scheduled.future = executor.schedule(() -> fire(scheduled, nominal), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void fire(ScheduledJob scheduled, Instant nominal) {
        if (scheduled.removed || !started) {
            return;
        }
        try {
            runSlot(scheduled.name, scheduled.job, nominal);
        } catch (RuntimeException ex) {
            log.error("Scheduled run of job [{}] for slot {} failed: {}", scheduled.name, nominal, ex.getMessage(), ex);
        } finally {
            if (!scheduled.removed && started) {
                armAfter(scheduled, nominal);
            }
        }
    }

    /**
     * Runs one slot of a job now, applying the misfire threshold, the lock and the JobInstance
     * check. The scheduler's threads call it at the slot's time.
     *
     * @param scheduleName the name the job was scheduled under (for logs)
     * @param job          the job
     * @param nominal      the slot's nominal fire time
     * @return what happened
     */
    SlotOutcome runSlot(String scheduleName, Job job, Instant nominal) {
        Duration late = Duration.between(nominal, clock.instant());
        if (late.compareTo(misfireThreshold) > 0) {
            log.warn("Skipping slot {} of job [{}]: it fired {}s late, beyond adhar.batch.scheduler.misfire-threshold "
                    + "({})", nominal, scheduleName, late.toSeconds(), misfireThreshold);
            return SlotOutcome.MISFIRED;
        }
        String jobName = job.getName();
        JobParameters parameters = slotParameters(nominal);
        SlotRun run = new SlotRun(jobName, parameters, clock.instant());
        if (running.putIfAbsent(jobName, run) != null) {
            log.info("Skipping slot {} of job [{}]: its previous run is still in progress", nominal, scheduleName);
            return SlotOutcome.OVERLAPPING;
        }
        boolean locked = false;
        ScheduledFuture<?> renewal = null;
        try {
            if (lock != null) {
                locked = lock.tryLock(jobName, lockLease);
                if (!locked) {
                    log.info("Skipping slot {} of job [{}]: another instance holds its lock", nominal, scheduleName);
                    return SlotOutcome.LOCKED_ELSEWHERE;
                }
                ScheduledExecutorService renewals = renewer;
                if (renewals != null) {
                    long every = lockRenewInterval.toMillis();
                    renewal = renewals.scheduleWithFixedDelay(() -> renew(run), every, every, TimeUnit.MILLISECONDS);
                }
            }
            JobInstance existing = jobRepository.getJobInstance(jobName, parameters);
            if (existing != null) {
                log.info("Skipping slot {} of job [{}]: it already has JobInstance {}", nominal, scheduleName,
                        existing.getInstanceId());
                return SlotOutcome.ALREADY_RAN;
            }
            JobExecution execution = jobOperator.start(job, parameters);
            execution = awaitEnd(execution);
            BatchStatus status = execution.getStatus();
            if (status == BatchStatus.COMPLETED) {
                log.info("Scheduled run of job [{}] for slot {} completed (execution {})", scheduleName, nominal,
                        execution.getId());
            } else {
                log.warn("Scheduled run of job [{}] for slot {} ended {} (execution {}, exit code {})", scheduleName,
                        nominal, status, execution.getId(), execution.getExitStatus().getExitCode());
            }
            return SlotOutcome.RAN;
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException ex) {
            log.info("Skipping slot {} of job [{}]: another instance ran or runs it", nominal, scheduleName);
            return SlotOutcome.ALREADY_RAN;
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            // Two instances without a shared lock created the slot's JobInstance at the same time.
            log.info("Skipping slot {} of job [{}]: another instance claimed it first", nominal, scheduleName);
            return SlotOutcome.ALREADY_RAN;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for the run of job [{}] for slot {} to end", scheduleName, nominal);
            return SlotOutcome.RAN;
        } catch (Exception ex) {
            log.error("Scheduled run of job [{}] for slot {} could not start: {}", scheduleName, nominal,
                    ex.getMessage(), ex);
            return SlotOutcome.FAILED_TO_START;
        } finally {
            if (renewal != null) {
                renewal.cancel(false);
            }
            if (locked) {
                lock.unlock(jobName);
            }
            running.remove(jobName, run);
        }
    }

    private JobExecution awaitEnd(JobExecution execution) throws InterruptedException {
        JobExecution current = execution;
        while (current.isRunning()) {
            Thread.sleep(POLL_INTERVAL.toMillis());
            JobExecution refreshed = jobRepository.getJobExecution(current.getId());
            if (refreshed == null) {
                return current;
            }
            current = refreshed;
        }
        return current;
    }

    private void renew(SlotRun run) {
        if (run.lost) {
            return;
        }
        try {
            if (lock.renew(run.jobName, lockLease)) {
                run.lastRenewal = clock.instant();
                return;
            }
            lockLost(run, "another instance took it over after its lease expired");
        } catch (DataAccessException ex) {
            Duration unrenewed = Duration.between(run.lastRenewal, clock.instant());
            if (unrenewed.compareTo(lockLease) >= 0) {
                lockLost(run, "it could not be renewed for " + unrenewed.toSeconds() + "s: " + ex.getMessage());
            } else {
                log.warn("Could not renew the scheduler lock of job [{}], retrying: {}", run.jobName, ex.getMessage());
            }
        }
    }

    private void lockLost(SlotRun run, String reason) {
        run.lost = true;
        log.error("Job [{}] lost its scheduler lock ({}); stopping its run so two instances never run it at once",
                run.jobName, reason);
        requestStop(run, "its scheduler lock was lost");
    }

    private void requestStop(SlotRun run, String reason) {
        try {
            JobInstance instance = jobRepository.getJobInstance(run.jobName, run.parameters);
            JobExecution last = instance != null ? jobRepository.getLastJobExecution(instance) : null;
            if (last != null && last.isRunning()) {
                log.info("Stopping execution {} of job [{}]: {}", last.getId(), run.jobName, reason);
                jobOperator.stop(last);
            }
        } catch (JobExecutionNotRunningException ex) {
            log.debug("Execution of job [{}] had already ended", run.jobName);
        } catch (RuntimeException ex) {
            log.warn("Could not stop the running execution of job [{}]: {}", run.jobName, ex.getMessage());
        }
    }

    private static boolean awaitTermination(ScheduledThreadPoolExecutor pool, Duration timeout) {
        try {
            return pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * What a slot did.
     */
    enum SlotOutcome {
        /** The job ran (whatever its status). */
        RAN,
        /** Fired later than the misfire threshold. */
        MISFIRED,
        /** The job was still running in this process. */
        OVERLAPPING,
        /** Another instance held the job's lock. */
        LOCKED_ELSEWHERE,
        /** The slot's JobInstance already existed. */
        ALREADY_RAN,
        /** The job could not be started. */
        FAILED_TO_START
    }

    /**
     * One registered schedule.
     */
    private static final class ScheduledJob {

        private final String name;
        private final String cron;
        private final CronExpression expression;
        private final ZoneId zone;
        private volatile Job job;
        private volatile ScheduledFuture<?> future;
        private volatile Instant nextFire;
        private volatile boolean removed;

        private ScheduledJob(String name, String cron, CronExpression expression, ZoneId zone) {
            this.name = name;
            this.cron = cron;
            this.expression = expression;
            this.zone = zone;
        }

        private void resolve(JobResolver resolver) {
            Job resolved = resolver.resolve(name);
            if (resolved.getJobParametersIncrementer() != null) {
                throw new IllegalArgumentException("Job [" + name + "] has a JobParametersIncrementer, so it cannot "
                        + "be scheduled: Spring Batch would ignore each slot's identity (" + SCHEDULED_TIME_PARAMETER
                        + ") and start a new JobInstance on every fire, and a double fire could not be recognised. "
                        + "Remove the incrementer from the job.");
            }
            this.job = resolved;
        }

        private Instant next(Instant after) {
            ZonedDateTime next = expression.next(after.atZone(zone));
            return next == null ? null : next.toInstant();
        }

        private synchronized void disarm() {
            ScheduledFuture<?> armed = future;
            if (armed != null) {
                armed.cancel(false);
            }
            nextFire = null;
        }

        private synchronized void remove() {
            removed = true;
            disarm();
        }
    }

    /**
     * One slot being run by this process.
     */
    private static final class SlotRun {

        private final String jobName;
        private final JobParameters parameters;
        private volatile Instant lastRenewal;
        private volatile boolean lost;

        private SlotRun(String jobName, JobParameters parameters, Instant start) {
            this.jobName = jobName;
            this.parameters = parameters;
            this.lastRenewal = start;
        }
    }
}
