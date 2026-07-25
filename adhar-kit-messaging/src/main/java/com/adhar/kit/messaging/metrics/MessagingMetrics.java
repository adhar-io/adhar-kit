package com.adhar.kit.messaging.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;

/**
 * Micrometer-backed metrics for the messaging module.
 * <p>
 * Records publish/consume counts, failures, retries, dead-letter routing, and consume
 * latency, all tagged by {@code destination}. Instances are only created when
 * {@code micrometer-core} is on the classpath and a {@link MeterRegistry} bean is
 * available - see {@code com.adhar.kit.messaging.config.MessagingAutoConfiguration}.
 * <p>
 * This class has no Spring dependency itself so it can be constructed directly
 * (e.g. in tests) with a {@link io.micrometer.core.instrument.simple.SimpleMeterRegistry}.
 */
public class MessagingMetrics {

    /** Prefix shared by every meter this class records, kept as a single source of truth. */
    private static final String PREFIX = "adhar.messaging.";

    private final MeterRegistry registry;

    public MessagingMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Records a successful publish to the given destination.
     *
     * @param destination the topic/exchange/queue published to
     */
    public void recordPublish(String destination) {
        registry.counter(PREFIX + "publish", "destination", safe(destination)).increment();
    }

    /**
     * Records a failed publish attempt to the given destination.
     *
     * @param destination the topic/exchange/queue that failed to publish
     */
    public void recordPublishFailure(String destination) {
        registry.counter(PREFIX + "publish.failure", "destination", safe(destination)).increment();
    }

    /**
     * Records a successfully consumed (and handled) message.
     *
     * @param destination the topic/exchange/queue consumed from
     */
    public void recordConsume(String destination) {
        registry.counter(PREFIX + "consume", "destination", safe(destination)).increment();
    }

    /**
     * Records a message that failed processing (after any retries were exhausted).
     *
     * @param destination the topic/exchange/queue consumed from
     */
    public void recordConsumeFailure(String destination) {
        registry.counter(PREFIX + "consume.failure", "destination", safe(destination)).increment();
    }

    /**
     * Records a single retry attempt for the given destination.
     *
     * @param destination the topic/exchange/queue being retried
     */
    public void recordRetry(String destination) {
        registry.counter(PREFIX + "retry", "destination", safe(destination)).increment();
    }

    /**
     * Records a message routed to the dead-letter destination.
     *
     * @param destination the *original* topic/exchange/queue (not the DLQ destination)
     */
    public void recordDlq(String destination) {
        registry.counter(PREFIX + "dlq", "destination", safe(destination)).increment();
    }

    /**
     * Records a message that was skipped because it had already been processed
     * (deduplication hit).
     *
     * @param destination the topic/exchange/queue consumed from
     */
    public void recordDuplicate(String destination) {
        registry.counter(PREFIX + "duplicate", "destination", safe(destination)).increment();
    }

    /**
     * Starts a latency timer sample. Pair with {@link #stopTimer(Timer.Sample, String)}.
     *
     * @return a started timer sample
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /**
     * Stops a latency timer sample started via {@link #startTimer()}, recording the
     * elapsed duration against the given destination.
     *
     * @param sample      the sample returned by {@link #startTimer()}
     * @param destination the topic/exchange/queue the timed operation applied to
     */
    public void stopTimer(Timer.Sample sample, String destination) {
        sample.stop(registry.timer(PREFIX + "latency", "destination", safe(destination)));
    }

    private String safe(String destination) {
        return destination != null ? destination : "unknown";
    }
}
