package com.adhar.kit.profiler;

import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Unified facade for the performance profiler module.
 * Provides convenient access to method profiling, memory snapshots,
 * and runtime diagnostics through a single entry point.
 */
@Slf4j
public class ProfilerFacade {

    private static volatile ProfilerFacade instance;

    private final ProfilingRegistry registry;
    private final MemoryProfiler memoryProfiler;
    private volatile boolean enabled = true;

    /**
     * Creates a ProfilerFacade with the given registry and memory profiler.
     *
     * @param registry       the profiling registry, or {@code null} to use a default instance
     * @param memoryProfiler the memory profiler, or {@code null} to use a default instance
     */
    public ProfilerFacade(ProfilingRegistry registry, MemoryProfiler memoryProfiler) {
        this.registry = registry != null ? registry : new ProfilingRegistry();
        this.memoryProfiler = memoryProfiler != null ? memoryProfiler : new MemoryProfiler();
        log.debug("ProfilerFacade initialized");
    }

    /**
     * Returns the singleton instance, creating one with default dependencies if necessary.
     */
    public static ProfilerFacade getInstance() {
        if (instance == null) {
            synchronized (ProfilerFacade.class) {
                if (instance == null) {
                    instance = new ProfilerFacade(null, null);
                }
            }
        }
        return instance;
    }

    /**
     * Returns a full profiling report from the registry.
     */
    public ProfilingReport getReport() {
        return registry.getReport();
    }

    /**
     * Returns the top N slowest methods (hotspots) by average execution time.
     *
     * @param topN the number of hotspots to return
     * @return list of method profiles sorted by average time descending
     */
    public List<MethodProfile> getHotspots(int topN) {
        return registry.getHotspots(topN);
    }

    /**
     * Returns a point-in-time snapshot of JVM memory, GC, and thread state.
     */
    public MemorySnapshot getMemorySnapshot() {
        return memoryProfiler.getMemorySnapshot();
    }

    /**
     * Returns the current heap memory usage in megabytes.
     */
    public double getHeapUsageMb() {
        return memoryProfiler.getHeapUsage().getUsed() / (1024.0 * 1024.0);
    }

    /**
     * Returns the current number of active threads.
     */
    public int getThreadCount() {
        return memoryProfiler.getThreadCount();
    }

    /**
     * Returns profiling statistics for a specific method.
     *
     * @param methodName the fully qualified method name (e.g. "com.example.MyClass.myMethod")
     * @return an optional containing the method profile if found, empty otherwise
     */
    public Optional<MethodProfile> getMethodStats(String methodName) {
        return getReport().topSlowestMethods().stream()
                .filter(profile -> profile.name().equals(methodName))
                .findFirst()
                .or(() -> {
                    // Search across all profiled methods via a broader report
                    ProfilingReport report = registry.getReport(Integer.MAX_VALUE);
                    return report.topSlowestMethods().stream()
                            .filter(profile -> profile.name().equals(methodName))
                            .findFirst();
                });
    }

    /**
     * Clears all accumulated profiling data and resets the profiling window.
     */
    public void reset() {
        registry.reset();
        log.info("Profiling data has been reset");
    }

    /**
     * Manually profiles a block of code, recording timing in the registry.
     *
     * @param name      a descriptive name for the profiled operation
     * @param operation the operation to execute and measure
     * @param <T>       the return type of the operation
     * @return the result of the operation
     */
    public <T> T profile(String name, Supplier<T> operation) {
        if (!enabled) {
            return operation.get();
        }

        long start = System.currentTimeMillis();
        boolean success = true;
        String errorType = null;
        try {
            return operation.get();
        } catch (Exception e) {
            success = false;
            errorType = e.getClass().getName();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            ProfilingResult result = new ProfilingResult(
                    name,
                    "manual",
                    duration,
                    success,
                    errorType,
                    Instant.now()
            );
            registry.record(result);
            log.trace("Profiled [{}] completed in {} ms (success={})", name, duration, success);
        }
    }

    /**
     * Manually profiles a void block of code, recording timing in the registry.
     *
     * @param name      a descriptive name for the profiled operation
     * @param operation the operation to execute and measure
     */
    public void profile(String name, Runnable operation) {
        profile(name, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * Returns whether profiling is currently enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables profiling.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Profiling enabled set to {}", enabled);
    }

    /**
     * Returns a health summary including the number of profiled methods and current memory usage.
     */
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();

        ProfilingReport report = getReport();
        health.put("profiledMethodCount", report.methodCallCounts().size());
        health.put("totalProfiledCalls", report.totalProfiledCalls());

        MemorySnapshot snapshot = getMemorySnapshot();
        health.put("heapUsedMb", snapshot.heapUsedBytes() / (1024.0 * 1024.0));
        health.put("heapMaxMb", snapshot.heapMaxBytes() / (1024.0 * 1024.0));
        health.put("activeThreadCount", snapshot.activeThreadCount());
        health.put("enabled", enabled);

        return health;
    }
}
