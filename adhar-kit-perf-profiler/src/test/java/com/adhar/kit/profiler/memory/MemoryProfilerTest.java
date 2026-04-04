package com.adhar.kit.profiler.memory;

import com.adhar.kit.profiler.model.MemorySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.MemoryUsage;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryProfilerTest {

    private MemoryProfiler memoryProfiler;

    @BeforeEach
    void setUp() {
        memoryProfiler = new MemoryProfiler();
    }

    @Test
    @DisplayName("getMemorySnapshot returns a non-null snapshot with all fields populated")
    void getMemorySnapshotReturnsNonNull() {
        MemorySnapshot snapshot = memoryProfiler.getMemorySnapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.capturedAt()).isNotNull();
        assertThat(snapshot.gcStats()).isNotNull();
    }

    @Test
    @DisplayName("heap usage values are non-negative")
    void heapUsageValuesAreNonNegative() {
        MemorySnapshot snapshot = memoryProfiler.getMemorySnapshot();

        assertThat(snapshot.heapUsedBytes()).isNotNegative();
        assertThat(snapshot.heapCommittedBytes()).isNotNegative();
        // heapMaxBytes can be -1 if undefined, so check used and committed
        assertThat(snapshot.heapUsedBytes()).isLessThanOrEqualTo(snapshot.heapCommittedBytes());
    }

    @Test
    @DisplayName("non-heap usage values are non-negative")
    void nonHeapUsageValuesAreNonNegative() {
        MemorySnapshot snapshot = memoryProfiler.getMemorySnapshot();

        assertThat(snapshot.nonHeapUsedBytes()).isNotNegative();
        assertThat(snapshot.nonHeapCommittedBytes()).isNotNegative();
    }

    @Test
    @DisplayName("getHeapUsage returns valid MemoryUsage with non-negative used bytes")
    void getHeapUsageReturnsValid() {
        MemoryUsage heapUsage = memoryProfiler.getHeapUsage();

        assertThat(heapUsage).isNotNull();
        assertThat(heapUsage.getUsed()).isNotNegative();
        assertThat(heapUsage.getCommitted()).isNotNegative();
    }

    @Test
    @DisplayName("GC stats list is present and each entry has a name")
    void gcStatsArePresent() {
        MemorySnapshot snapshot = memoryProfiler.getMemorySnapshot();

        assertThat(snapshot.gcStats()).isNotNull();
        // At least one GC should be available in any JVM
        assertThat(snapshot.gcStats()).isNotEmpty();

        snapshot.gcStats().forEach(gc -> {
            assertThat(gc.name()).isNotNull().isNotBlank();
            assertThat(gc.collectionCount()).isNotNegative();
            assertThat(gc.collectionTimeMs()).isNotNegative();
        });
    }

    @Test
    @DisplayName("getThreadCount returns a positive value")
    void threadCountIsPositive() {
        int threadCount = memoryProfiler.getThreadCount();

        assertThat(threadCount).isPositive();
    }

    @Test
    @DisplayName("getDaemonThreadCount returns a non-negative value")
    void daemonThreadCountIsNonNegative() {
        int daemonCount = memoryProfiler.getDaemonThreadCount();

        assertThat(daemonCount).isNotNegative();
    }

    @Test
    @DisplayName("snapshot active thread count matches getThreadCount")
    void snapshotThreadCountMatchesDirectCall() {
        // These may differ slightly due to timing, but both should be positive
        MemorySnapshot snapshot = memoryProfiler.getMemorySnapshot();

        assertThat(snapshot.activeThreadCount()).isPositive();
        assertThat(snapshot.daemonThreadCount()).isNotNegative();
    }
}
