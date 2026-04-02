package com.adhar.kit.profiler.model;

import java.time.Instant;

/**
 * Immutable result of a profiled method execution.
 */
public record ProfilingResult(
        String methodName,
        String className,
        long durationMs,
        boolean success,
        String errorType,
        Instant timestamp
) {
    public boolean isSlow(long thresholdMs) {
        return durationMs > thresholdMs;
    }
}
