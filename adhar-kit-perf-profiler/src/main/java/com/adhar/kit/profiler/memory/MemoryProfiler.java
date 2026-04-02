package com.adhar.kit.profiler.memory;

import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MemorySnapshot.GcInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.List;

/**
 * Captures JVM memory, GC, and thread snapshots using {@link ManagementFactory}.
 */
public class MemoryProfiler {

    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;

    public MemoryProfiler() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    /**
     * Returns current heap memory usage.
     */
    public MemoryUsage getHeapUsage() {
        return memoryMXBean.getHeapMemoryUsage();
    }

    /**
     * Returns current non-heap memory usage.
     */
    public MemoryUsage getNonHeapUsage() {
        return memoryMXBean.getNonHeapMemoryUsage();
    }

    /**
     * Returns GC statistics for all available garbage collectors.
     */
    public List<GcInfo> getGcStats() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(gc -> new GcInfo(
                        gc.getName(),
                        gc.getCollectionCount(),
                        gc.getCollectionTime()
                ))
                .toList();
    }

    /**
     * Returns the current number of live threads.
     */
    public int getThreadCount() {
        return threadMXBean.getThreadCount();
    }

    /**
     * Returns the current number of daemon threads.
     */
    public int getDaemonThreadCount() {
        return threadMXBean.getDaemonThreadCount();
    }

    /**
     * Captures a full memory snapshot including heap, non-heap, GC stats, and threads.
     */
    public MemorySnapshot getMemorySnapshot() {
        MemoryUsage heap = getHeapUsage();
        MemoryUsage nonHeap = getNonHeapUsage();

        return new MemorySnapshot(
                heap.getUsed(),
                heap.getMax(),
                heap.getCommitted(),
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                getThreadCount(),
                getDaemonThreadCount(),
                getGcStats(),
                Instant.now()
        );
    }
}
