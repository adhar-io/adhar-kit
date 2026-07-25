package com.adhar.adharkit.messaging.metrics;

import com.adhar.kit.messaging.metrics.MessagingMetrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MessagingMetrics}, using a real {@link SimpleMeterRegistry} (no
 * server, no Docker) so the recorded meters can be asserted directly.
 */
class MessagingMetricsTest {

    private SimpleMeterRegistry registry;
    private MessagingMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MessagingMetrics(registry);
    }

    @Test
    void recordsPublishCounter() {
        metrics.recordPublish("order-events");
        metrics.recordPublish("order-events");

        assertEquals(2.0, registry.counter("adhar.messaging.publish", "destination", "order-events").count());
    }

    @Test
    void recordsPublishFailureCounter() {
        metrics.recordPublishFailure("order-events");

        assertEquals(1.0, registry.counter("adhar.messaging.publish.failure", "destination", "order-events").count());
    }

    @Test
    void recordsConsumeCounter() {
        metrics.recordConsume("order-events");

        assertEquals(1.0, registry.counter("adhar.messaging.consume", "destination", "order-events").count());
    }

    @Test
    void recordsConsumeFailureCounter() {
        metrics.recordConsumeFailure("order-events");

        assertEquals(1.0, registry.counter("adhar.messaging.consume.failure", "destination", "order-events").count());
    }

    @Test
    void recordsRetryCounter() {
        metrics.recordRetry("order-events");
        metrics.recordRetry("order-events");
        metrics.recordRetry("order-events");

        assertEquals(3.0, registry.counter("adhar.messaging.retry", "destination", "order-events").count());
    }

    @Test
    void recordsDlqCounter() {
        metrics.recordDlq("order-events");

        assertEquals(1.0, registry.counter("adhar.messaging.dlq", "destination", "order-events").count());
    }

    @Test
    void recordsDuplicateCounter() {
        metrics.recordDuplicate("order-events");

        assertEquals(1.0, registry.counter("adhar.messaging.duplicate", "destination", "order-events").count());
    }

    @Test
    void tagsMissingDestinationAsUnknown() {
        metrics.recordPublish(null);

        assertEquals(1.0, registry.counter("adhar.messaging.publish", "destination", "unknown").count());
    }

    @Test
    void recordsLatencyTimer() throws InterruptedException {
        Timer.Sample sample = metrics.startTimer();
        Thread.sleep(5);
        metrics.stopTimer(sample, "order-events");

        Timer timer = registry.timer("adhar.messaging.latency", "destination", "order-events");
        assertEquals(1L, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    void constructorRejectsNullRegistry() {
        assertThrows(NullPointerException.class, () -> new MessagingMetrics(null));
    }
}
