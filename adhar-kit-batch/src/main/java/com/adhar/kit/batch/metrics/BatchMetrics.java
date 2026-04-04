package com.adhar.kit.batch.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Collects and exposes batch job execution metrics using Micrometer.
 *
 * <p>Tracks per-job and per-step metrics including execution counts, success/failure
 * rates, and durations. All metrics are registered with the provided {@link MeterRegistry}
 * and can be exported to any Micrometer-supported monitoring system.</p>
 *
 * <p><b>Metric Names:</b></p>
 * <ul>
 *   <li>{@code adhar.batch.job.executions} - Total job executions (tagged by job name and status)</li>
 *   <li>{@code adhar.batch.job.duration} - Job execution duration timer</li>
 *   <li>{@code adhar.batch.step.reads} - Step read count</li>
 *   <li>{@code adhar.batch.step.writes} - Step write count</li>
 *   <li>{@code adhar.batch.step.skips} - Step skip count</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class BatchMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, JobMetricsAccumulator> jobMetrics = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@code BatchMetrics} collector.
     *
     * @param meterRegistry the Micrometer meter registry for recording metrics
     */
    public BatchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a job execution with its duration and outcome.
     *
     * @param jobName    the name of the batch job
     * @param durationMs the execution duration in milliseconds
     * @param success    whether the execution completed successfully
     */
    public void recordJobExecution(String jobName, long durationMs, boolean success) {
        var status = success ? "success" : "failure";

        Counter.builder("adhar.batch.job.executions")
                .tag("job", jobName)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer.builder("adhar.batch.job.duration")
                .tag("job", jobName)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        jobMetrics.computeIfAbsent(jobName, _ -> new JobMetricsAccumulator())
                .record(durationMs, success);

        log.debug("Recorded job execution: job={}, duration={}ms, status={}", jobName, durationMs, status);
    }

    /**
     * Records step-level execution metrics.
     *
     * @param stepName   the name of the batch step
     * @param readCount  the number of items read
     * @param writeCount the number of items written
     * @param skipCount  the number of items skipped
     */
    public void recordStepExecution(String stepName, long readCount, long writeCount, long skipCount) {
        Counter.builder("adhar.batch.step.reads")
                .tag("step", stepName)
                .register(meterRegistry)
                .increment(readCount);

        Counter.builder("adhar.batch.step.writes")
                .tag("step", stepName)
                .register(meterRegistry)
                .increment(writeCount);

        Counter.builder("adhar.batch.step.skips")
                .tag("step", stepName)
                .register(meterRegistry)
                .increment(skipCount);

        log.debug("Recorded step execution: step={}, reads={}, writes={}, skips={}",
                stepName, readCount, writeCount, skipCount);
    }

    /**
     * Returns aggregated statistics for the given job.
     *
     * @param jobName the name of the batch job
     * @return the job statistics, or a zero-value record if no executions have been recorded
     */
    public BatchJobStats getJobStats(String jobName) {
        var accumulator = jobMetrics.get(jobName);
        if (accumulator == null) {
            return new BatchJobStats(0, 0, 0, 0.0, null);
        }
        return accumulator.toStats();
    }

    /**
     * Internal accumulator for per-job metrics, safe for concurrent access.
     */
    private static class JobMetricsAccumulator {
        private final AtomicLong totalExecutions = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final DoubleAdder totalDurationMs = new DoubleAdder();
        private volatile Instant lastExecutionTime;

        void record(long durationMs, boolean success) {
            totalExecutions.incrementAndGet();
            totalDurationMs.add(durationMs);
            lastExecutionTime = Instant.now();
            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }

        BatchJobStats toStats() {
            long total = totalExecutions.get();
            double avgDuration = total > 0 ? totalDurationMs.sum() / total : 0.0;
            return new BatchJobStats(
                    total,
                    successCount.get(),
                    failureCount.get(),
                    avgDuration,
                    lastExecutionTime
            );
        }
    }
}
