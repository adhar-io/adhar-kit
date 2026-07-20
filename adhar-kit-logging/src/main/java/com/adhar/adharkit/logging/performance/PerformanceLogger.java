package com.adhar.adharkit.logging.performance;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.slf4j.event.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance logging: per-operation timing with slow-operation detection and in-memory
 * aggregated statistics (count, failures, min/max/avg).
 *
 * <p>Executions slower than {@code adhar.logging.performance.slow-threshold-ms} are published as
 * WARN {@link AppLogEventType#PERFORMANCE} events; with
 * {@code adhar.logging.performance.log-all-operations=true} every execution is published (DEBUG).
 * Aggregated statistics are kept per operation name and can be emitted on demand via
 * {@link #logSummary()} (e.g. from a scheduled task) or inspected via {@link #snapshot()}.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * try (PerformanceTimer timer = performanceLogger.start("payment.authorize")) {
 *     gateway.authorize(payment);
 * }
 * }</pre>
 *
 * <p>Or declaratively with
 * {@link com.adhar.adharkit.logging.annotation.TrackPerformance @TrackPerformance}.</p>
 */
public class PerformanceLogger {

    private final AdharLoggingProperties properties;
    private final AppLogEventPublisher publisher;
    private final ConcurrentHashMap<String, OperationStats> stats = new ConcurrentHashMap<>();

    /**
     * Creates the performance logger.
     *
     * @param properties logging properties (performance section)
     * @param publisher  event pipeline
     */
    public PerformanceLogger(AdharLoggingProperties properties, AppLogEventPublisher publisher) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
    }

    /**
     * Starts a timer for the given operation. Close it to record the measurement.
     *
     * @param operation operation name
     * @return the running timer
     */
    public PerformanceTimer start(String operation) {
        return new PerformanceTimer(this, operation);
    }

    /**
     * Records one execution using the globally configured slow threshold.
     *
     * @param operation  operation name
     * @param durationMs elapsed milliseconds
     * @param success    whether the execution succeeded
     */
    public void record(String operation, long durationMs, boolean success) {
        record(operation, durationMs, success, properties.getPerformance().getSlowThresholdMs());
    }

    /**
     * Records one execution with an explicit slow threshold (used by
     * {@code @TrackPerformance(slowThresholdMs = ...)} overrides).
     *
     * @param operation       operation name
     * @param durationMs      elapsed milliseconds
     * @param success         whether the execution succeeded
     * @param slowThresholdMs threshold above which the execution is reported as slow
     */
    public void record(String operation, long durationMs, boolean success, long slowThresholdMs) {
        if (!properties.getPerformance().isEnabled()) {
            return;
        }
        stats.computeIfAbsent(operation, k -> new OperationStats()).record(durationMs, success);

        boolean slow = durationMs >= slowThresholdMs;
        if (slow || properties.getPerformance().isLogAllOperations()) {
            publisher.publish(AppLogEvent.builder()
                    .type(AppLogEventType.PERFORMANCE)
                    .category("performance")
                    .name(operation)
                    .message(slow ? "Slow operation detected" : "Operation timed")
                    .outcome(success ? AppLogEventOutcome.SUCCESS : AppLogEventOutcome.FAILURE)
                    .severity(slow ? Level.WARN : Level.DEBUG)
                    .durationMs(durationMs)
                    .metadata("slow", slow)
                    .metadata("thresholdMs", slowThresholdMs)
                    .build());
        }
    }

    /**
     * A snapshot of the aggregated statistics per operation name.
     *
     * @return operation name to statistics snapshot
     */
    public Map<String, OperationStats.Snapshot> snapshot() {
        Map<String, OperationStats.Snapshot> result = new LinkedHashMap<>(stats.size());
        stats.forEach((name, s) -> result.put(name, s.snapshot()));
        return result;
    }

    /**
     * Publishes one PERFORMANCE summary event per tracked operation with its aggregated
     * statistics. Intended to be called periodically (scheduler) or at shutdown.
     */
    public void logSummary() {
        if (!properties.getPerformance().isEnabled()) {
            return;
        }
        snapshot().forEach((operation, snap) -> publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.PERFORMANCE)
                .category("performance-summary")
                .name(operation)
                .message("Performance summary")
                .outcome(AppLogEventOutcome.SUCCESS)
                .metadata("count", snap.count())
                .metadata("failures", snap.failures())
                .metadata("totalMs", snap.totalMs())
                .metadata("minMs", snap.minMs())
                .metadata("maxMs", snap.maxMs())
                .metadata("avgMs", snap.avgMs())
                .build()));
    }

    /**
     * Clears all aggregated statistics.
     */
    public void reset() {
        stats.clear();
    }
}
