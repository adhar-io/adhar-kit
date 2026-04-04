package com.adhar.kit.profiler.registry;

import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
}
