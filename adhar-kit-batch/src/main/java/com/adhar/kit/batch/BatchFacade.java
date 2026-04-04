package com.adhar.kit.batch;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.metrics.BatchJobStats;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unified facade for the Adhar Batch module.
 *
 * <p>Provides a single entry point for scheduling, cancelling, and monitoring
 * batch jobs. Delegates to {@link BatchScheduler}, {@link BatchMetrics}, and
 * {@link BatchProperties} internally.</p>
 *
 * <p>All dependencies are optional -- the facade gracefully degrades when a
 * component is not available (e.g. metrics recording is silently skipped when
 * no {@link BatchMetrics} instance is configured).</p>
 *
 * <p>A thread-safe singleton is available via {@link #getInstance()} after
 * the instance has been initialised through the constructor.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class BatchFacade {

    private static volatile BatchFacade instance;

    private final BatchScheduler scheduler;
    private final BatchMetrics metrics;
    private final BatchProperties properties;

    /**
     * Creates a new {@code BatchFacade} and publishes it as the singleton instance.
     *
     * @param scheduler  the batch scheduler, may be {@code null}
     * @param metrics    the batch metrics collector, may be {@code null}
     * @param properties the batch configuration properties, may be {@code null}
     */
    public BatchFacade(BatchScheduler scheduler, BatchMetrics metrics, BatchProperties properties) {
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.properties = properties;
        instance = this;
        log.info("BatchFacade initialised [scheduler={}, metrics={}, properties={}]",
                scheduler != null, metrics != null, properties != null);
    }

    /**
     * Returns the singleton {@code BatchFacade} instance.
     *
     * @return the singleton instance
     * @throws IllegalStateException if the facade has not been initialised
     */
    public static BatchFacade getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BatchFacade has not been initialised");
        }
        return instance;
    }

    // ----------------------------------------------------------------
    // Scheduling
    // ----------------------------------------------------------------

    /**
     * Schedules a batch job to run according to the given cron expression.
     *
     * @param jobName        the Spring bean name of the job to schedule
     * @param cronExpression the cron expression defining the schedule
     * @throws IllegalStateException if no scheduler is available
     */
    public void scheduleJob(String jobName, String cronExpression) {
        requireScheduler();
        scheduler.scheduleJob(jobName, cronExpression);
    }

    /**
     * Cancels a previously scheduled job.
     *
     * @param jobName the name of the job to cancel
     * @return {@code true} if the job was found and cancelled, {@code false} otherwise
     * @throws IllegalStateException if no scheduler is available
     */
    public boolean cancelJob(String jobName) {
        requireScheduler();
        return scheduler.cancelScheduledJob(jobName);
    }

    /**
     * Returns an unmodifiable set of all currently scheduled job names.
     *
     * @return the set of scheduled job names, or an empty set if no scheduler is available
     */
    public Set<String> listScheduledJobs() {
        if (scheduler == null) {
            return Collections.emptySet();
        }
        return scheduler.listScheduledJobs();
    }

    // ----------------------------------------------------------------
    // Metrics
    // ----------------------------------------------------------------

    /**
     * Returns aggregated statistics for the given job.
     *
     * @param jobName the name of the batch job
     * @return the job statistics, or a zero-value record if no executions have been recorded
     *         or no metrics component is available
     */
    public BatchJobStats getJobStats(String jobName) {
        if (metrics == null) {
            log.warn("BatchMetrics is not available; returning empty stats for job [{}]", jobName);
            return new BatchJobStats(0, 0, 0, 0.0, null);
        }
        return metrics.getJobStats(jobName);
    }

    /**
     * Records a job execution with its duration and outcome.
     *
     * @param jobName    the name of the batch job
     * @param durationMs the execution duration in milliseconds
     * @param success    whether the execution completed successfully
     */
    public void recordJobExecution(String jobName, long durationMs, boolean success) {
        if (metrics == null) {
            log.warn("BatchMetrics is not available; skipping recording for job [{}]", jobName);
            return;
        }
        metrics.recordJobExecution(jobName, durationMs, success);
    }

    // ----------------------------------------------------------------
    // Properties
    // ----------------------------------------------------------------

    /**
     * Returns the configured default chunk size for step processing.
     *
     * @return the default chunk size, or {@code 100} if properties are not available
     */
    public int getDefaultChunkSize() {
        return properties != null ? properties.getDefaultChunkSize() : 100;
    }

    /**
     * Returns the configured default page size for paginated item readers.
     *
     * @return the default page size, or {@code 50} if properties are not available
     */
    public int getDefaultPageSize() {
        return properties != null ? properties.getDefaultPageSize() : 50;
    }

    /**
     * Checks whether the batch module is enabled.
     *
     * @return {@code true} if enabled (or if properties are not available, defaults to {@code true})
     */
    public boolean isEnabled() {
        return properties == null || properties.isEnabled();
    }

    // ----------------------------------------------------------------
    // Health
    // ----------------------------------------------------------------

    /**
     * Returns a health snapshot of the batch subsystem.
     *
     * @return an unmodifiable map containing scheduler status and job count
     */
    public Map<String, Object> health() {
        var info = new LinkedHashMap<String, Object>();
        info.put("enabled", isEnabled());
        info.put("schedulerAvailable", scheduler != null);
        info.put("metricsAvailable", metrics != null);
        info.put("scheduledJobCount", scheduler != null ? scheduler.listScheduledJobs().size() : 0);
        info.put("scheduledJobs", scheduler != null ? scheduler.listScheduledJobs() : Collections.emptySet());
        return Collections.unmodifiableMap(info);
    }

    // ----------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException("BatchScheduler is not available");
        }
    }
}
