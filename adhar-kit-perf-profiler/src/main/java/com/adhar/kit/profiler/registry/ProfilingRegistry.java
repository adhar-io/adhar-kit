package com.adhar.kit.profiler.registry;

import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Thread-safe registry that tracks all profiling results and computes
 * aggregated statistics per method.
 */
public class ProfilingRegistry {

    private final ConcurrentHashMap<String, MutableMethodStats> stats = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> windowStart = new AtomicReference<>(Instant.now());

    /**
     * Record a profiling result into the registry.
     */
    public void record(ProfilingResult result) {
        String key = result.className() + "." + result.methodName();
        stats.compute(key, (k, existing) -> {
            if (existing == null) {
                existing = new MutableMethodStats(key);
            }
            existing.record(result.durationMs(), !result.success());
            return existing;
        });
    }

    /**
     * Generate a full profiling report.
     */
    public ProfilingReport getReport() {
        return getReport(10);
    }

    /**
     * Generate a full profiling report with the specified number of top slowest methods.
     */
    public ProfilingReport getReport(int topN) {
        Instant now = Instant.now();
        List<MethodProfile> allProfiles = buildProfiles();

        List<MethodProfile> topSlowest = allProfiles.stream()
                .sorted(Comparator.comparingDouble(MethodProfile::averageTimeMs).reversed())
                .limit(topN)
                .toList();

        Map<String, Long> callCounts = allProfiles.stream()
                .collect(Collectors.toMap(MethodProfile::name, MethodProfile::callCount));

        Map<String, ProfilingReport.MethodStats> methodStatistics = new LinkedHashMap<>();
        for (MutableMethodStats s : stats.values()) {
            methodStatistics.put(s.name, s.toMethodStats());
        }

        Map<String, Double> errorRates = allProfiles.stream()
                .collect(Collectors.toMap(MethodProfile::name, MethodProfile::errorRate));

        long totalCalls = allProfiles.stream().mapToLong(MethodProfile::callCount).sum();

        return new ProfilingReport(
                topSlowest,
                callCounts,
                methodStatistics,
                errorRates,
                windowStart.get(),
                now,
                totalCalls
        );
    }

    /**
     * Return the top N methods by average execution time (hotspots).
     */
    public List<MethodProfile> getHotspots(int topN) {
        return buildProfiles().stream()
                .sorted(Comparator.comparingDouble(MethodProfile::averageTimeMs).reversed())
                .limit(topN)
                .toList();
    }

    /**
     * Reset all profiling statistics.
     */
    public void reset() {
        stats.clear();
        windowStart.set(Instant.now());
    }

    private List<MethodProfile> buildProfiles() {
        return stats.values().stream()
                .map(MutableMethodStats::toMethodProfile)
                .toList();
    }

    /**
     * Internal mutable accumulator for method-level statistics.
     * All mutation happens inside ConcurrentHashMap.compute(), so field access is safe.
     */
    private static class MutableMethodStats {
        final String name;
        long callCount;
        long totalTimeMs;
        long minTimeMs = Long.MAX_VALUE;
        long maxTimeMs = Long.MIN_VALUE;
        long errorCount;
        Instant lastExecutionTime = Instant.now();
        // Keep sorted durations for percentile calculations
        final List<Long> durations = new ArrayList<>();

        MutableMethodStats(String name) {
            this.name = name;
        }

        void record(long durationMs, boolean isError) {
            callCount++;
            totalTimeMs += durationMs;
            minTimeMs = Math.min(minTimeMs, durationMs);
            maxTimeMs = Math.max(maxTimeMs, durationMs);
            if (isError) {
                errorCount++;
            }
            durations.add(durationMs);
            lastExecutionTime = Instant.now();
        }

        MethodProfile toMethodProfile() {
            return new MethodProfile(
                    name,
                    callCount,
                    totalTimeMs,
                    callCount == 0 ? 0 : minTimeMs,
                    callCount == 0 ? 0 : maxTimeMs,
                    errorCount,
                    lastExecutionTime
            );
        }

        ProfilingReport.MethodStats toMethodStats() {
            if (callCount == 0) {
                return new ProfilingReport.MethodStats(0, 0, 0, 0, 0);
            }
            List<Long> sorted = durations.stream().sorted().toList();
            double avg = (double) totalTimeMs / callCount;
            double p95 = percentile(sorted, 0.95);
            double p99 = percentile(sorted, 0.99);
            return new ProfilingReport.MethodStats(avg, minTimeMs, maxTimeMs, p95, p99);
        }

        private static double percentile(List<Long> sortedValues, double percentile) {
            if (sortedValues.isEmpty()) return 0.0;
            int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
            index = Math.max(0, Math.min(index, sortedValues.size() - 1));
            return sortedValues.get(index);
        }
    }
}
