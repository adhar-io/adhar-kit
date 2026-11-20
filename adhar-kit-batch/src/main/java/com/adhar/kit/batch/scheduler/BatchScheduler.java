package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.JobParametersResolver;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Embedded scheduler for a service that also runs a small job (design 0025 §5.16, §5.20). The
 * platform path for batch is a Kubernetes CronJob per job; this one runs inside the service.
 *
 * <p>It is the one public API of in-service scheduling, over one of two engines selected by
 * {@code adhar.batch.scheduler.engine} (D24): {@code simple} ({@link SimpleSchedulerEngine}, the
 * default: cron schedules on the service's threads with a lock row per job) or {@code quartz}
 * (a clustered Quartz JDBC job store in the batch schema, with failover of an in-flight run,
 * misfire policies, calendars and runtime triggers). Both identify a slot by {@link SlotIdentity},
 * so a job keeps its JobInstances, restart points and run names when its service changes
 * engine.</p>
 *
 * <p>It is a {@link SmartLifecycle} in the last phase, so it stops first: running jobs stop before
 * anything they use, and under {@code quartz} before the Quartz scheduler itself.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchScheduler implements SmartLifecycle {

    /** Identifying parameter carrying a slot's nominal fire time. */
    public static final String SCHEDULED_TIME_PARAMETER = "adhar.scheduled-time";

    /** Non-identifying provenance parameter naming what started the run. */
    public static final String TRIGGER_PARAMETER = "adhar.trigger";

    /** {@value #TRIGGER_PARAMETER} of runs started by this scheduler. */
    public static final String EMBEDDED_TRIGGER = "embedded";

    /** Non-identifying provenance parameter: the run's name (naming v1), on both engines. */
    public static final String RUN_PARAMETER = "adhar.run";

    /** Non-identifying provenance parameter: the trigger that fired, {@code <group>.<name>}. */
    public static final String TRIGGER_KEY_PARAMETER = "adhar.trigger.key";

    private final SchedulerEngine engine;

    /**
     * @param engine the engine that fires the schedules
     */
    public BatchScheduler(SchedulerEngine engine) {
        Assert.notNull(engine, "engine must not be null");
        this.engine = engine;
    }

    /**
     * A scheduler on the engine {@code simple}, for jobs that declare no parameters.
     *
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
        this(new SimpleSchedulerEngine(jobOperator, jobRepository, jobResolver, null, new JobParametersResolver(),
                lock, settings, shutdownTimeout, clock));
    }

    /**
     * @return the engine, as {@code adhar.batch.scheduler.engine} selected it
     */
    public SchedulerEngine engine() {
        return engine;
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
        engine.scheduleJob(jobName, cronExpression);
    }

    /**
     * Removes a schedule. A run already in progress finishes.
     *
     * @param jobName the name it was scheduled under
     * @return {@code true} if it was scheduled
     */
    public boolean cancelScheduledJob(String jobName) {
        return engine.cancelScheduledJob(jobName);
    }

    /**
     * @return the scheduled job names, sorted
     */
    public Set<String> listScheduledJobs() {
        return engine.listScheduledJobs();
    }

    /**
     * @param jobName the name a job was scheduled under
     * @return the nominal time of its next armed fire, while the scheduler runs
     */
    public Optional<Instant> nextFireTime(String jobName) {
        return engine.nextFireTime(jobName);
    }

    /**
     * @return the names of the jobs a scheduled run is executing right now
     */
    public Set<String> runningJobs() {
        return engine.runningJobs();
    }

    /**
     * Runs one slot of a job now on the engine {@code simple}, applying the misfire threshold,
     * the lock and the JobInstance check. The engine's threads call it at the slot's time.
     *
     * @param scheduleName the name the job was scheduled under (for logs)
     * @param job          the job
     * @param nominal      the slot's nominal fire time
     * @return what happened
     */
    SlotOutcome runSlot(String scheduleName, Job job, Instant nominal) {
        if (!(engine instanceof SimpleSchedulerEngine simple)) {
            throw new IllegalStateException("runSlot drives the engine simple only, not " + engine.name());
        }
        return simple.runSlot(scheduleName, job, nominal);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void start() {
        engine.start();
    }

    @Override
    public void stop() {
        engine.stop();
    }

    @Override
    public boolean isRunning() {
        return engine.isRunning();
    }

    @Override
    public int getPhase() {
        // The highest phase stops first: running jobs stop before anything they use, and under
        // the engine quartz before the Quartz scheduler (phase Integer.MAX_VALUE - 1).
        return Integer.MAX_VALUE;
    }
}
