package com.adhar.kit.profiler.model;

import java.time.Instant;

/**
 * Aggregated statistics for a single profiled method.
 */
public record MethodProfile(
        String name,
        long callCount,
        long totalTimeMs,
        long minTimeMs,
        long maxTimeMs,
        long errorCount,
        Instant lastExecutionTime
) {
    public double averageTimeMs() {
        return callCount == 0 ? 0.0 : (double) totalTimeMs / callCount;
    }

    public double errorRate() {
        return callCount == 0 ? 0.0 : (double) errorCount / callCount;
    }
}
