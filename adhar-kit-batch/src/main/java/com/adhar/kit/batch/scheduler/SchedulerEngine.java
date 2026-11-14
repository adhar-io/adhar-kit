package com.adhar.kit.batch.scheduler;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * An engine behind {@link BatchScheduler} (design 0025 §5.20.2, D24): {@code simple}
 * ({@link SimpleSchedulerEngine}, the default) or {@code quartz}
 * ({@code com.adhar.kit.batch.scheduler.quartz.QuartzSchedulerEngine}), selected by
 * {@code adhar.batch.scheduler.engine}. {@code BatchScheduler} stays the one public API; an
 * engine implements it. Both derive a fire's parameters from {@link SlotIdentity} and report
 * {@link SlotOutcome}s, so a job keeps its JobInstances when its service changes engine.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public interface SchedulerEngine {

    /**
     * @return the engine's name as {@code adhar.batch.scheduler.engine} spells it
     */
    String name();

    /**
     * Schedules a job on a Spring cron expression, replacing any schedule it already has.
     *
     * @param jobName        the job's bean name or {@code Job#getName()}
     * @param cronExpression six fields, seconds first, in {@code adhar.batch.scheduler.time-zone}
     * @throws IllegalArgumentException for an invalid expression, an unknown job, or one with an incrementer
     */
    void scheduleJob(String jobName, String cronExpression);

    /**
     * @param jobName the name it was scheduled under
     * @return {@code true} if it was scheduled
     */
    boolean cancelScheduledJob(String jobName);

    /**
     * @return the scheduled job names, sorted
     */
    Set<String> listScheduledJobs();

    /**
     * @param jobName the name a job was scheduled under
     * @return the nominal time of its next fire, while the engine runs
     */
    Optional<Instant> nextFireTime(String jobName);

    /**
     * @return the names of the jobs a scheduled run of THIS process is executing right now
     */
    Set<String> runningJobs();

    /**
     * Starts firing.
     */
    void start();

    /**
     * Stops firing and stops this process's runs at their next chunk boundary, waiting up to
     * {@code adhar.batch.shutdown.timeout}.
     */
    void stop();

    /**
     * @return whether the engine fires
     */
    boolean isRunning();
}
