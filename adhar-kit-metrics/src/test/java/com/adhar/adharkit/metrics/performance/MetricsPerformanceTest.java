package com.adhar.adharkit.metrics.performance;

import com.adhar.adharkit.metrics.annotation.Timed;
import com.adhar.adharkit.metrics.config.AdharMetricsAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for metrics collection to ensure low overhead.
 */
@SpringBootTest(classes = {
    AdharMetricsAutoConfiguration.class,
    MetricsPerformanceTest.PerformanceTestService.class
})
@TestPropertySource(properties = {
    "adhar.metrics.enabled=true",
    "adhar.metrics.prometheus.enabled=true"
})
class MetricsPerformanceTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PerformanceTestService performanceTestService;

    @Test
    void testHighVolumeMetricsCollection() throws InterruptedException {
        int numberOfCalls = 10000;
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfCalls);

        long startTime = System.currentTimeMillis();

        // Execute high volume of metric collections
        IntStream.range(0, numberOfCalls).forEach(i -> {
            executor.submit(() -> {
                try {
                    performanceTestService.highVolumeMethod();
                } finally {
                    latch.countDown();
                }
            });
        });

        // Wait for all calls to complete
        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify performance metrics
        Timer timer = meterRegistry.find("performance.high.volume").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(numberOfCalls);

        // Verify low overhead (should complete in reasonable time)
        long totalTime = endTime - startTime;
        assertThat(totalTime).isLessThan(10000); // Should complete within 10 seconds

        System.out.println("High volume test completed: " + numberOfCalls + " calls in " + totalTime + "ms");
        System.out.println("Average time per call: " + (totalTime / (double) numberOfCalls) + "ms");
    }

    @Test
    void testConcurrentMetricsAccess() throws InterruptedException {
        int numberOfThreads = 50;
        int callsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // Submit concurrent tasks
        CompletableFuture<?>[] futures = new CompletableFuture[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        performanceTestService.concurrentMethod(threadId, j);
                    }
                } finally {
                    latch.countDown();
                }
            }, executor);
        }

        // Wait for completion
        latch.await(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(futures).join();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify all metrics were recorded correctly
        Timer timer = meterRegistry.find("performance.concurrent").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(numberOfThreads * callsPerThread);

        // Verify thread-specific metrics
        for (int i = 0; i < numberOfThreads; i++) {
            Timer threadTimer = meterRegistry.find("performance.concurrent")
                .tag("thread", String.valueOf(i))
                .timer();
            assertThat(threadTimer).isNotNull();
            assertThat(threadTimer.count()).isEqualTo(callsPerThread);
        }
    }

    @Test
    void testMemoryUsageWithMetrics() {
        // Get initial memory
        Runtime runtime = Runtime.getRuntime();
        System.gc(); // Suggest garbage collection
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Execute many operations with metrics
        for (int i = 0; i < 1000; i++) {
            performanceTestService.memoryTestMethod(i);
        }

        // Get final memory
        System.gc(); // Suggest garbage collection
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Verify metrics were collected
        Timer timer = meterRegistry.find("performance.memory.test").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1000);

        // Verify reasonable memory usage (should not cause significant memory leak)
        System.out.println("Memory increase: " + (memoryIncrease / 1024) + " KB");
        assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024); // Less than 10MB increase
    }

    @Test
    void testMetricsOverheadBenchmark() {
        int iterations = 100000;

        // Benchmark without metrics
        long startWithoutMetrics = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            performanceTestService.methodWithoutMetrics();
        }
        long timeWithoutMetrics = System.nanoTime() - startWithoutMetrics;

        // Benchmark with metrics
        long startWithMetrics = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            performanceTestService.methodWithMetrics();
        }
        long timeWithMetrics = System.nanoTime() - startWithMetrics;

        // Calculate overhead
        double overheadPercentage = ((double) (timeWithMetrics - timeWithoutMetrics) / timeWithoutMetrics) * 100;

        System.out.println("Time without metrics: " + TimeUnit.NANOSECONDS.toMillis(timeWithoutMetrics) + "ms");
        System.out.println("Time with metrics: " + TimeUnit.NANOSECONDS.toMillis(timeWithMetrics) + "ms");
        System.out.println("Overhead: " + String.format("%.2f", overheadPercentage) + "%");

        // Verify metrics were collected
        Timer timer = meterRegistry.find("performance.with.metrics").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(iterations);

        // Verify overhead is reasonable (should be less than 50% for this simple case)
        assertThat(overheadPercentage).isLessThan(50.0);
    }

    @Test
    void testLongRunningOperationMetrics() throws InterruptedException {
        // Start a long-running operation
        CompletableFuture<Void> longTask = CompletableFuture.runAsync(() -> {
            performanceTestService.longRunningMethod();
        });

        // Wait a bit and check that long task timer is active
        Thread.sleep(100);

        var longTaskTimer = meterRegistry.find("performance.long.running").longTaskTimer();
        assertThat(longTaskTimer).isNotNull();
        assertThat(longTaskTimer.activeTasks()).isGreaterThan(0);

        // Wait for completion
        longTask.join();

        // Verify task completed and metrics recorded
        assertThat(longTaskTimer.activeTasks()).isEqualTo(0);
        Timer completedTimer = meterRegistry.find("performance.long.running.completed").timer();
        assertThat(completedTimer).isNotNull();
        assertThat(completedTimer.count()).isEqualTo(1);
    }

    /**
     * Service for performance testing with various metrics scenarios.
     */
    @Service
    static class PerformanceTestService {

        @Timed("performance.high.volume")
        public void highVolumeMethod() {
            // Minimal work to test metrics overhead
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += i;
            }
        }

        @Timed(value = "performance.concurrent", extraTags = {"thread", "#{#threadId}"})
        public void concurrentMethod(int threadId, int iteration) {
            // Small amount of work per call
            Math.sqrt(threadId * iteration);
        }

        @Timed("performance.memory.test")
        public void memoryTestMethod(int iteration) {
            // Create some temporary objects
            String temp = "test-" + iteration;
            temp.toUpperCase();
        }

        public void methodWithoutMetrics() {
            // Simple method without any metrics
            int result = 42 * 2;
        }

        @Timed("performance.with.metrics")
        public void methodWithMetrics() {
            // Same work as methodWithoutMetrics but with metrics
            int result = 42 * 2;
        }

        @Timed("performance.long.running.completed")
        public void longRunningMethod() {
            try {
                // Simulate long-running task
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
