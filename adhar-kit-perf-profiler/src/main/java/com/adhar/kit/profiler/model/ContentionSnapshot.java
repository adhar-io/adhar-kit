package com.adhar.kit.profiler.model;

import java.time.Instant;
import java.util.List;

/**
 * Point-in-time snapshot of thread contention, listing the most contended live threads.
 *
 * <p>Blocked/waited counts are always available; blocked/waited times are {@code -1} unless
 * JVM-wide thread contention time monitoring is enabled. Delta fields report the change since
 * the previous snapshot taken by the same collector (equal to the cumulative values on the
 * first snapshot of a thread).
 */
public record ContentionSnapshot(
        boolean monitoringSupported,
        boolean timeMonitoringEnabled,
        List<ContendedThread> topContendedThreads,
        Instant capturedAt
) {
    /**
     * Contention statistics for a single thread.
     */
    public record ContendedThread(
            long threadId,
            String threadName,
            String threadState,
            long blockedCount,
            long blockedTimeMs,
            long waitedCount,
            long waitedTimeMs,
            long blockedCountDelta,
            long blockedTimeMsDelta,
            long waitedCountDelta,
            long waitedTimeMsDelta
    ) {}
}
