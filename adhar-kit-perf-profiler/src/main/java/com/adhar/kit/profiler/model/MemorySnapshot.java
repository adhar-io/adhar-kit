package com.adhar.kit.profiler.model;

import java.time.Instant;
import java.util.List;

/**
 * Point-in-time snapshot of JVM memory and thread state.
 */
public record MemorySnapshot(
        long heapUsedBytes,
        long heapMaxBytes,
        long heapCommittedBytes,
        long nonHeapUsedBytes,
        long nonHeapCommittedBytes,
        int activeThreadCount,
        int daemonThreadCount,
        List<GcInfo> gcStats,
        Instant capturedAt
) {
    /**
     * Garbage collector statistics.
     */
    public record GcInfo(
            String name,
            long collectionCount,
            long collectionTimeMs
    ) {}
}
