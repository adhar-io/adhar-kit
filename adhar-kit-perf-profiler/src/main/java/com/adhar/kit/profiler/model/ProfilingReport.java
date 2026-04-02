package com.adhar.kit.profiler.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated profiling report containing method-level performance statistics.
 */
public record ProfilingReport(
        List<MethodProfile> topSlowestMethods,
        Map<String, Long> methodCallCounts,
        Map<String, MethodStats> methodStatistics,
        Map<String, Double> errorRates,
        Instant windowStart,
        Instant windowEnd,
        long totalProfiledCalls
) {
    /**
     * Detailed duration statistics for a profiled method.
     */
    public record MethodStats(
            double averageMs,
            long minMs,
            long maxMs,
            double p95Ms,
            double p99Ms
    ) {}
}
