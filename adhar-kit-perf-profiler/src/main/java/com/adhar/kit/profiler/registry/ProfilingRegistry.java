package com.adhar.kit.profiler.registry;

import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;
import com.adhar.kit.profiler.model.WindowSnapshot;
import org.HdrHistogram.Histogram;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Thread-safe registry that tracks all profiling results and computes
 * aggregated statistics per method.
 *
 * <p>Duration data is kept in a bounded {@link Histogram} per method (not a growing list),
 * so memory stays flat regardless of how many calls are recorded. The registry also keeps a
 * rolling time window: current-window stats are periodically snapshotted into a small,
 * bounded history so long-running services don't retain per-call data forever while still
 * exposing recent trend data.
 */
public class ProfilingRegistry {

    /** Default rolling window length used when none is configured. */
    public static final Duration DEFAULT_WINDOW_DURATION = Duration.ofMinutes(5);
    /** Default number of completed windows retained in history. */
    public static final int DEFAULT_HISTORY_WINDOWS = 5;

    // HdrHistogram sizing: tracks 1ms..1hr with 2 significant decimal digits. The footprint of a
    // Histogram is fixed by this range/precision at construction time and does NOT grow with the
    // number of recorded values, which is what bounds memory usage here.
    private static final long HISTOGRAM_LOWEST_TRACKABLE_MS = 1;
    private static final long HISTOGRAM_HIGHEST_TRACKABLE_MS = Duration.ofHours(1).toMillis();
    private static final int HISTOGRAM_SIGNIFICANT_DIGITS = 2;

    private final ConcurrentHashMap<String, MutableMethodStats> stats = new ConcurrentHashMap<>();
    private final Duration windowDuration;
    private final int historyWindows;
    private final ReentrantLock rolloverLock = new ReentrantLock();
    private final Deque<WindowSnapshot> windowHistory = new ArrayDeque<>();
    private volatile Instant windowStart = Instant.now();

    public ProfilingRegistry() {
        this(DEFAULT_WINDOW_DURATION, DEFAULT_HISTORY_WINDOWS);
    }

    /**
     * @param windowDuration length of the rolling aggregation window before a rollover occurs
     * @param historyWindows number of completed windows to retain (bounded, oldest evicted first)
     */
    public ProfilingRegistry(Duration windowDuration, int historyWindows) {
        this.windowDuration = (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative())
                ? DEFAULT_WINDOW_DURATION
                : windowDuration;
        this.historyWindows = historyWindows <= 0 ? DEFAULT_HISTORY_WINDOWS : historyWindows;
    }

    /**
     * Record a profiling result into the registry.
     */
    public void record(ProfilingResult result) {
        maybeRollover();
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
        maybeRollover();
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
                windowStart,
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
     * Returns aggregated duration statistics (avg/min/max/p95/p99) for a single method key
     * (in {@code className.methodName} form), or empty if the method has not been recorded
     * in the current window.
     */
    public Optional<ProfilingReport.MethodStats> getMethodStats(String methodKey) {
        MutableMethodStats s = stats.get(methodKey);
        return s == null ? Optional.empty() : Optional.of(s.toMethodStats());
    }

    /**
     * Returns the bounded, most-recent-first list of completed rolling-window snapshots.
     * The list never exceeds the configured {@code historyWindows} size.
     */
    public List<WindowSnapshot> getWindowHistory() {
        rolloverLock.lock();
        try {
            return List.copyOf(windowHistory);
        } finally {
            rolloverLock.unlock();
        }
    }

    /**
     * Reset all profiling statistics and window history.
     */
    public void reset() {
        stats.clear();
        rolloverLock.lock();
        try {
            windowHistory.clear();
            windowStart = Instant.now();
        } finally {
            rolloverLock.unlock();
        }
    }

    /**
     * Checks whether the current rolling window has expired and, if so, snapshots it into the
     * bounded history and starts a new window. Cheap to call frequently: the common case is a
     * single volatile read plus a duration comparison, with the lock only taken when a rollover
     * is actually due (double-checked to avoid duplicate rollovers under concurrency).
     */
    private void maybeRollover() {
        if (Duration.between(windowStart, Instant.now()).compareTo(windowDuration) < 0) {
            return;
        }
        rolloverLock.lock();
        try {
            if (Duration.between(windowStart, Instant.now()).compareTo(windowDuration) < 0) {
                return;
            }
            Instant completedStart = windowStart;
            Instant completedEnd = Instant.now();

            Map<String, ProfilingReport.MethodStats> snapshotStats = new LinkedHashMap<>();
            long totalCalls = 0;
            for (MutableMethodStats s : stats.values()) {
                snapshotStats.put(s.name, s.toMethodStats());
                totalCalls += s.callCount;
            }

            windowHistory.addFirst(new WindowSnapshot(completedStart, completedEnd, snapshotStats, totalCalls));
            while (windowHistory.size() > historyWindows) {
                windowHistory.removeLast();
            }

            stats.clear();
            windowStart = Instant.now();
        } finally {
            rolloverLock.unlock();
        }
    }

    /**
     * Package-private hook used by tests to prove the histogram's memory footprint stays
     * bounded regardless of how many samples have been recorded.
     */
    long estimatedHistogramFootprintBytes(String methodKey) {
        MutableMethodStats s = stats.get(methodKey);
        return s == null ? -1L : s.estimatedHistogramFootprintBytes();
    }

    private List<MethodProfile> buildProfiles() {
        return stats.values().stream()
                .map(MutableMethodStats::toMethodProfile)
                .toList();
    }

    /**
     * Internal mutable accumulator for method-level statistics.
     * Writes always happen inside {@code ConcurrentHashMap.compute()} (so at most one writer
     * per key at a time), but reads (report generation, rollover snapshotting) can race with an
     * in-flight write from another thread, so all access is additionally synchronized on the
     * instance itself.
     */
    private static final class MutableMethodStats {
        final String name;
        long callCount;
        long totalTimeMs;
        long minTimeMs = Long.MAX_VALUE;
        long maxTimeMs = Long.MIN_VALUE;
        long errorCount;
        Instant lastExecutionTime = Instant.now();
        // Bounded histogram for percentile calculations - fixed footprint, independent of sample count.
        final Histogram histogram = new Histogram(
                HISTOGRAM_LOWEST_TRACKABLE_MS, HISTOGRAM_HIGHEST_TRACKABLE_MS, HISTOGRAM_SIGNIFICANT_DIGITS);

        MutableMethodStats(String name) {
            this.name = name;
        }

        synchronized void record(long durationMs, boolean isError) {
            callCount++;
            totalTimeMs += durationMs;
            minTimeMs = Math.min(minTimeMs, durationMs);
            maxTimeMs = Math.max(maxTimeMs, durationMs);
            if (isError) {
                errorCount++;
            }
            histogram.recordValue(Math.max(durationMs, 0));
            lastExecutionTime = Instant.now();
        }

        synchronized MethodProfile toMethodProfile() {
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

        synchronized ProfilingReport.MethodStats toMethodStats() {
            if (callCount == 0) {
                return new ProfilingReport.MethodStats(0, 0, 0, 0, 0);
            }
            double avg = (double) totalTimeMs / callCount;
            double p95 = histogram.getValueAtPercentile(95.0);
            double p99 = histogram.getValueAtPercentile(99.0);
            return new ProfilingReport.MethodStats(avg, minTimeMs, maxTimeMs, p95, p99);
        }

        synchronized long estimatedHistogramFootprintBytes() {
            return histogram.getEstimatedFootprintInBytes();
        }
    }
}
