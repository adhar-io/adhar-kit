package com.adhar.kit.profiler.model;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of aggregated method statistics captured when a rolling
 * time window rolled over. A bounded number of these are retained by the
 * {@link com.adhar.kit.profiler.registry.ProfilingRegistry} to allow querying
 * recent history without holding on to per-call data indefinitely.
 */
public record WindowSnapshot(
        Instant windowStart,
        Instant windowEnd,
        Map<String, ProfilingReport.MethodStats> methodStatistics,
        long totalCalls
) {
}
