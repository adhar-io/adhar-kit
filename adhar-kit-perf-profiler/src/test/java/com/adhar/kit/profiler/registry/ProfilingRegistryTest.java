package com.adhar.kit.profiler.registry;

import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;
import com.adhar.kit.profiler.model.WindowSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProfilingRegistryTest {

    private ProfilingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProfilingRegistry();
    }

    @Test
    @DisplayName("record adds an entry and it appears in the report")
    void recordAddsEntry() {
        ProfilingResult result = new ProfilingResult(
                "doWork", "com.example.Service", 42L, true, null, Instant.now()
        );

        registry.record(result);

        ProfilingReport report = registry.getReport();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
        assertThat(report.methodCallCounts()).containsKey("com.example.Service.doWork");
        assertThat(report.methodCallCounts().get("com.example.Service.doWork")).isEqualTo(1);
    }

    @Test
    @DisplayName("getReport returns correct aggregated statistics for multiple calls")
    void getReportReturnsAggregatedStats() {
        String methodName = "process";
        String className = "com.example.Handler";
        Instant now = Instant.now();

        registry.record(new ProfilingResult(methodName, className, 10L, true, null, now));
        registry.record(new ProfilingResult(methodName, className, 30L, true, null, now));
        registry.record(new ProfilingResult(methodName, className, 20L, false, "RuntimeException", now));

        ProfilingReport report = registry.getReport();
        String key = className + "." + methodName;

        assertThat(report.totalProfiledCalls()).isEqualTo(3);
        assertThat(report.methodCallCounts().get(key)).isEqualTo(3);

        ProfilingReport.MethodStats stats = report.methodStatistics().get(key);
        assertThat(stats).isNotNull();
        assertThat(stats.averageMs()).isEqualTo(20.0);
        assertThat(stats.minMs()).isEqualTo(10L);
        assertThat(stats.maxMs()).isEqualTo(30L);

        // Error rate: 1 out of 3
        assertThat(report.errorRates().get(key)).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("getHotspots returns methods sorted by average latency descending")
    void getHotspotsReturnsSortedByLatencyDescending() {
        Instant now = Instant.now();

        // Fast method: avg 5ms
        registry.record(new ProfilingResult("fast", "svc", 5L, true, null, now));
        // Slow method: avg 100ms
        registry.record(new ProfilingResult("slow", "svc", 100L, true, null, now));
        // Medium method: avg 50ms
        registry.record(new ProfilingResult("medium", "svc", 50L, true, null, now));

        List<MethodProfile> hotspots = registry.getHotspots(3);

        assertThat(hotspots).hasSize(3);
        assertThat(hotspots.get(0).name()).isEqualTo("svc.slow");
        assertThat(hotspots.get(1).name()).isEqualTo("svc.medium");
        assertThat(hotspots.get(2).name()).isEqualTo("svc.fast");

        // Verify descending order
        for (int i = 0; i < hotspots.size() - 1; i++) {
            assertThat(hotspots.get(i).averageTimeMs())
                    .isGreaterThanOrEqualTo(hotspots.get(i + 1).averageTimeMs());
        }
    }

    @Test
    @DisplayName("getHotspots limits results to topN")
    void getHotspotsLimitsToTopN() {
        Instant now = Instant.now();

        registry.record(new ProfilingResult("a", "svc", 10L, true, null, now));
        registry.record(new ProfilingResult("b", "svc", 20L, true, null, now));
        registry.record(new ProfilingResult("c", "svc", 30L, true, null, now));

        List<MethodProfile> hotspots = registry.getHotspots(2);
        assertThat(hotspots).hasSize(2);
    }

    @Test
    @DisplayName("reset clears all entries and resets the window")
    void resetClearsAllEntries() {
        Instant now = Instant.now();
        registry.record(new ProfilingResult("op", "svc", 10L, true, null, now));
        assertThat(registry.getReport().totalProfiledCalls()).isEqualTo(1);

        registry.reset();

        ProfilingReport report = registry.getReport();
        assertThat(report.totalProfiledCalls()).isZero();
        assertThat(report.methodCallCounts()).isEmpty();
        assertThat(report.topSlowestMethods()).isEmpty();
    }

    @Test
    @DisplayName("concurrent recording is thread-safe")
    void concurrentRecordingIsThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < recordsPerThread; i++) {
                        registry.record(new ProfilingResult(
                                "method" + (i % 5),
                                "thread" + threadIndex,
                                (long) (Math.random() * 100),
                                true,
                                null,
                                Instant.now()
                        ));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        ProfilingReport report = registry.getReport(Integer.MAX_VALUE);
        assertThat(report.totalProfiledCalls()).isEqualTo((long) threadCount * recordsPerThread);
    }

    @Test
    @DisplayName("recording a very large number of samples keeps the histogram's memory footprint bounded")
    void memoryFootprintStaysBoundedUnderHighVolume() {
        String className = "svc";
        String methodName = "hot";
        String key = className + "." + methodName;
        Instant now = Instant.now();

        // Warm up with a modest number of samples and capture the footprint.
        for (int i = 0; i < 1_000; i++) {
            registry.record(new ProfilingResult(methodName, className, (i % 500) + 1, true, null, now));
        }
        long footprintAfter1k = registry.estimatedHistogramFootprintBytes(key);
        assertThat(footprintAfter1k).isPositive();

        // Record several orders of magnitude more samples - a growing List<Long> would blow up
        // memory here; a bounded histogram's footprint must not grow with sample count.
        for (int i = 0; i < 2_000_000; i++) {
            registry.record(new ProfilingResult(methodName, className, (i % 500) + 1, true, null, now));
        }
        long footprintAfter2m = registry.estimatedHistogramFootprintBytes(key);

        assertThat(footprintAfter2m).isEqualTo(footprintAfter1k);
        // Sanity bound: a bounded histogram over this range/precision should be well under 1MB,
        // regardless of the 2,001,000 samples recorded above.
        assertThat(footprintAfter2m).isLessThan(1_000_000);

        ProfilingReport report = registry.getReport(Integer.MAX_VALUE);
        assertThat(report.methodCallCounts().get(key)).isEqualTo(2_001_000L);
    }

    @Test
    @DisplayName("percentiles computed from the bounded histogram are accurate within tolerance")
    void percentilesAreAccurateForKnownDistribution() {
        String className = "svc";
        String methodName = "uniform";
        String key = className + "." + methodName;
        Instant now = Instant.now();

        // Known uniform distribution 1..10000ms; exact p95=9500, p99=9900 by rank.
        int n = 10_000;
        for (int i = 1; i <= n; i++) {
            registry.record(new ProfilingResult(methodName, className, i, true, null, now));
        }

        ProfilingReport.MethodStats stats = registry.getReport(Integer.MAX_VALUE).methodStatistics().get(key);
        assertThat(stats).isNotNull();

        // HdrHistogram at 2 significant digits has ~1% relative error; allow generous tolerance.
        assertThat(stats.p95Ms()).isCloseTo(9500.0, within(200.0));
        assertThat(stats.p99Ms()).isCloseTo(9900.0, within(200.0));
        assertThat(stats.minMs()).isEqualTo(1L);
        assertThat(stats.maxMs()).isEqualTo(n);
    }

    @Test
    @DisplayName("getMethodStats returns stats for a tracked method and empty for an unknown one")
    void getMethodStatsReturnsOptional() {
        registry.record(new ProfilingResult("op", "svc", 42L, true, null, Instant.now()));

        assertThat(registry.getMethodStats("svc.op")).isPresent();
        assertThat(registry.getMethodStats("svc.op").get().averageMs()).isEqualTo(42.0);
        assertThat(registry.getMethodStats("svc.unknown")).isEmpty();
    }

    @Test
    @DisplayName("rolling window rolls over into bounded history after the configured duration")
    void windowRollsOverIntoHistory() throws InterruptedException {
        ProfilingRegistry windowed = new ProfilingRegistry(Duration.ofMillis(30), 2);

        windowed.record(new ProfilingResult("op", "svc", 10L, true, null, Instant.now()));
        assertThat(windowed.getWindowHistory()).isEmpty();

        Thread.sleep(60);
        // Triggers rollover: current stats are snapshotted into history and cleared.
        windowed.record(new ProfilingResult("op", "svc", 20L, true, null, Instant.now()));

        List<WindowSnapshot> history = windowed.getWindowHistory();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().totalCalls()).isEqualTo(1);
        assertThat(history.getFirst().methodStatistics()).containsKey("svc.op");

        // The new window only contains the post-rollover record.
        assertThat(windowed.getReport().totalProfiledCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("window history is bounded to the configured number of historical windows")
    void windowHistoryIsBounded() throws InterruptedException {
        ProfilingRegistry windowed = new ProfilingRegistry(Duration.ofMillis(20), 2);

        for (int i = 0; i < 5; i++) {
            windowed.record(new ProfilingResult("op", "svc", 10L, true, null, Instant.now()));
            Thread.sleep(30);
        }
        // One more record to force a final rollover check.
        windowed.record(new ProfilingResult("op", "svc", 10L, true, null, Instant.now()));

        assertThat(windowed.getWindowHistory()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("an invalid window configuration falls back to sensible defaults")
    void invalidWindowConfigurationFallsBackToDefaults() {
        ProfilingRegistry defaulted = new ProfilingRegistry(Duration.ZERO, -1);

        defaulted.record(new ProfilingResult("op", "svc", 10L, true, null, Instant.now()));

        // Should behave like the default registry: no rollover happens immediately.
        assertThat(defaulted.getWindowHistory()).isEmpty();
        assertThat(defaulted.getReport().totalProfiledCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("reset clears window history as well as stats")
    void resetClearsWindowHistory() throws InterruptedException {
        ProfilingRegistry windowed = new ProfilingRegistry(Duration.ofMillis(20), 3);
        windowed.record(new ProfilingResult("op", "svc", 10L, true, null, Instant.now()));
        Thread.sleep(40);
        windowed.record(new ProfilingResult("op", "svc", 10L, true, null, Instant.now()));

        assertThat(windowed.getWindowHistory()).isNotEmpty();

        windowed.reset();

        assertThat(windowed.getWindowHistory()).isEmpty();
        assertThat(windowed.getReport().totalProfiledCalls()).isZero();
    }
}
