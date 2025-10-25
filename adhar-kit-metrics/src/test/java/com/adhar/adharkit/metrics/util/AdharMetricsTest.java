package com.adhar.adharkit.metrics.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AdharMetrics} utility class.
 */
class AdharMetricsTest {

    private MeterRegistry meterRegistry;
    private AdharMetrics adharMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adharMetrics = new AdharMetrics(meterRegistry);
    }

    @Test
    void testCounterCreation() {
        // Test counter creation without tags
        Counter counter = adharMetrics.counter("test.counter");
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo("test.counter");

        // Test counter creation with tags
        Counter counterWithTags = adharMetrics.counter("test.counter.tagged", "tag1", "value1", "tag2", "value2");
        assertThat(counterWithTags).isNotNull();
        assertThat(counterWithTags.getId().getTag("tag1")).isEqualTo("value1");
        assertThat(counterWithTags.getId().getTag("tag2")).isEqualTo("value2");
    }

    @Test
    void testCounterIncrement() {
        Counter counter = adharMetrics.counter("test.increment");

        // Test increment without tags
        adharMetrics.increment("test.increment");
        assertThat(counter.count()).isEqualTo(1.0);

        // Test increment with value
        adharMetrics.increment("test.increment", 5.0);
        assertThat(counter.count()).isEqualTo(6.0);

        // Test increment with tags
        adharMetrics.increment("test.increment.tagged", "env", "test");
        Counter taggedCounter = meterRegistry.find("test.increment.tagged").tag("env", "test").counter();
        assertThat(taggedCounter).isNotNull();
        assertThat(taggedCounter.count()).isEqualTo(1.0);
    }

    @Test
    void testTimerCreation() {
        // Test timer creation without tags
        Timer timer = adharMetrics.timer("test.timer");
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("test.timer");

        // Test timer creation with tags
        Timer timerWithTags = adharMetrics.timer("test.timer.tagged", "service", "api");
        assertThat(timerWithTags).isNotNull();
        assertThat(timerWithTags.getId().getTag("service")).isEqualTo("api");
    }

    @Test
    void testTimerRecord() {
        Timer timer = adharMetrics.timer("test.record");

        // Test recording duration
        adharMetrics.recordTime("test.record", Duration.ofMillis(100));
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);

        // Test recording with tags
        adharMetrics.recordTime("test.record.tagged", Duration.ofMillis(50), "method", "POST");
        Timer taggedTimer = meterRegistry.find("test.record.tagged").tag("method", "POST").timer();
        assertThat(taggedTimer).isNotNull();
        assertThat(taggedTimer.count()).isEqualTo(1);
    }

    @Test
    void testTimerSample() {
        Timer.Sample sample = adharMetrics.startTimer();
        assertThat(sample).isNotNull();

        // Simulate some work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Timer timer = adharMetrics.timer("test.sample");
        long duration = sample.stop(timer);
        assertThat(duration).isGreaterThan(0);
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void testGaugeCreation() {
        // Test gauge with supplier
        Gauge gauge = adharMetrics.gauge("test.gauge", () -> 42.0);
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.0);

        // Test gauge with object and function
        TestObject testObj = new TestObject(100);
        Gauge objectGauge = adharMetrics.gauge("test.object.gauge", testObj, TestObject::getValue);
        assertThat(objectGauge).isNotNull();
        assertThat(objectGauge.value()).isEqualTo(100.0);

        // Test gauge with tags
        Gauge taggedGauge = adharMetrics.gauge("test.tagged.gauge", () -> 99.0, "type", "memory");
        assertThat(taggedGauge).isNotNull();
        assertThat(taggedGauge.value()).isEqualTo(99.0);
        assertThat(taggedGauge.getId().getTag("type")).isEqualTo("memory");
    }

    @Test
    void testDistributionSummary() {
        // Test distribution summary creation
        var summary = adharMetrics.summary("test.summary");
        assertThat(summary).isNotNull();

        // Test recording values
        adharMetrics.recordValue("test.summary", 10.0);
        adharMetrics.recordValue("test.summary", 20.0);
        adharMetrics.recordValue("test.summary", 30.0);

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.totalAmount()).isEqualTo(60.0);

        // Test with tags
        adharMetrics.recordValue("test.tagged.summary", 50.0, "region", "us-east");
        var taggedSummary = meterRegistry.find("test.tagged.summary").tag("region", "us-east").summary();
        assertThat(taggedSummary).isNotNull();
        assertThat(taggedSummary.count()).isEqualTo(1);
        assertThat(taggedSummary.totalAmount()).isEqualTo(50.0);
    }

    @Test
    void testLongTaskTimer() {
        var longTaskTimer = adharMetrics.longTaskTimer("test.longtask");
        assertThat(longTaskTimer).isNotNull();

        // Start a long task
        var sample = longTaskTimer.start();
        assertThat(sample).isNotNull();
        assertThat(longTaskTimer.activeTasks()).isEqualTo(1);

        // Stop the task
        sample.stop();
        assertThat(longTaskTimer.activeTasks()).isEqualTo(0);
    }

    @Test
    void testFunctionCounter() {
        TestObject testObj = new TestObject(0);

        var functionCounter = adharMetrics.functionCounter("test.function.counter", testObj, TestObject::getIncrementCount);
        assertThat(functionCounter).isNotNull();

        // Increment the object's counter
        testObj.increment();
        testObj.increment();

        // The function counter should reflect the current value
        assertThat(functionCounter.count()).isEqualTo(2.0);
    }

    @Test
    void testFunctionTimer() {
        TestObject testObj = new TestObject(0);

        var functionTimer = adharMetrics.functionTimer("test.function.timer", testObj, TestObject::getCallCount, TestObject::getTotalTime, TimeUnit.MILLISECONDS);
        assertThat(functionTimer).isNotNull();

        // Simulate some calls
        testObj.simulateCall(100);
        testObj.simulateCall(200);

        assertThat(functionTimer.count()).isEqualTo(2.0);
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300.0);
    }

    @Test
    void testMetricsWithDescription() {
        Counter counter = adharMetrics.counter("test.described", "A test counter with description");
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getDescription()).isEqualTo("A test described counter");
    }

    @Test
    void testMetricsWithTagsMap() {
        Map<String, String> tags = Map.of(
            "service", "user-service",
            "version", "1.0.0",
            "environment", "test"
        );

        Counter counter = adharMetrics.counter("test.map.tags", tags);
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("service")).isEqualTo("user-service");
        assertThat(counter.getId().getTag("version")).isEqualTo("1.0.0");
        assertThat(counter.getId().getTag("environment")).isEqualTo("test");
    }

    @Test
    void testInvalidMetricNames() {
        // Test that invalid metric names are handled gracefully
        assertThatThrownBy(() -> adharMetrics.counter(null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adharMetrics.counter(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        // Test concurrent access to metrics
        Thread[] threads = new Thread[10];

        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    adharMetrics.increment("test.concurrent", "thread", String.valueOf(threadId));
                    adharMetrics.recordTime("test.concurrent.timer", Duration.ofMillis(j), "thread", String.valueOf(threadId));
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify that all metrics were recorded correctly
        for (int i = 0; i < threads.length; i++) {
            Counter counter = meterRegistry.find("test.concurrent").tag("thread", String.valueOf(i)).counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(100.0);

            Timer timer = meterRegistry.find("test.concurrent.timer").tag("thread", String.valueOf(i)).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(100);
        }
    }

    /**
     * Test object for gauge and function metric testing.
     */
    private static class TestObject {
        private double value;
        private int incrementCount = 0;
        private int callCount = 0;
        private double totalTime = 0.0;

        public TestObject(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public void increment() {
            incrementCount++;
        }

        public double getIncrementCount() {
            return incrementCount;
        }

        public void simulateCall(double time) {
            callCount++;
            totalTime += time;
        }

        public double getCallCount() {
            return callCount;
        }

        public double getTotalTime() {
            return totalTime;
        }
    }
}
