package com.adhar.kit.messaging.outbox;

import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled relay that drains the transactional {@link OutboxStore}, publishing pending
 * entries through the regular {@link MessagePublisher}.
 * <p>
 * Each pass fetches a batch of {@link OutboxStatus#PENDING}/{@link OutboxStatus#FAILED}
 * entries and attempts to publish them:
 * <ul>
 *   <li>on success the entry is marked {@link OutboxStatus#PUBLISHED};</li>
 *   <li>on failure its attempt counter is incremented and it is marked
 *       {@link OutboxStatus#FAILED} (eligible for retry on the next pass), or
 *       {@link OutboxStatus#DEAD} once {@code max-attempts} is reached.</li>
 * </ul>
 * The relay manages its own single-threaded {@link ScheduledExecutorService}, so it works
 * without {@code @EnableScheduling} on the host application. Call {@link #relayOnce()}
 * directly (e.g. from tests) to run a single synchronous pass.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore store;
    private final MessagePublisher publisher;
    private final OutboxPayloadCodec codec;
    private final MessagingMetrics metrics;
    private final int batchSize;
    private final int maxAttempts;
    private final long relayIntervalMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;

    /**
     * Creates a relay.
     *
     * @param store      the outbox store to drain
     * @param publisher  the publisher used to relay entries to the broker
     * @param codec      codec used to rebuild the payload object before publishing
     * @param properties outbox configuration (batch size, max attempts, interval)
     * @param metrics    optional metrics recorder, or {@code null}
     */
    public OutboxRelay(OutboxStore store, MessagePublisher publisher, OutboxPayloadCodec codec,
                       OutboxProperties properties, MessagingMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.codec = Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(properties, "properties");
        this.metrics = metrics;
        this.batchSize = Math.max(1, properties.getBatchSize());
        this.maxAttempts = Math.max(1, properties.getMaxAttempts());
        this.relayIntervalMs = Math.max(1, properties.getRelayIntervalMs());
    }

    /**
     * Starts the background relay loop. Idempotent: a second call while already running is
     * a no-op.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "adhar-outbox-relay");
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduler.scheduleWithFixedDelay(this::safeRelay, relayIntervalMs, relayIntervalMs, TimeUnit.MILLISECONDS);
        log.info("Outbox relay started (intervalMs={}, batchSize={}, maxAttempts={})",
                relayIntervalMs, batchSize, maxAttempts);
    }

    /**
     * Stops the background relay loop, shutting down the internal scheduler. Idempotent.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledExecutorService current = this.scheduler;
        this.scheduler = null;
        if (current != null) {
            current.shutdownNow();
        }
        log.info("Outbox relay stopped");
    }

    private void safeRelay() {
        try {
            relayOnce();
        } catch (Exception e) {
            log.warn("Outbox relay pass failed - will retry on next interval", e);
        }
    }

    /**
     * Runs a single relay pass synchronously.
     *
     * @return the number of entries successfully published in this pass
     */
    public int relayOnce() {
        List<OutboxEntry> pending = store.fetchPending(batchSize);
        int published = 0;
        for (OutboxEntry entry : pending) {
            if (relay(entry)) {
                published++;
            }
        }
        if (published > 0) {
            log.debug("Outbox relay published {} of {} pending entries", published, pending.size());
        }
        return published;
    }

    private boolean relay(OutboxEntry entry) {
        entry.setAttempts(entry.getAttempts() + 1);
        entry.setLastAttemptAt(Instant.now());
        boolean published = false;
        try {
            Object payload = codec.deserialize(entry.getPayload(), entry.getPayloadType());
            boolean ok = StringUtils.hasText(entry.getRoutingKey())
                    ? publisher.publish(entry.getDestination(), entry.getRoutingKey(), payload)
                    : publisher.publish(entry.getDestination(), payload);
            if (ok) {
                entry.setStatus(OutboxStatus.PUBLISHED);
                entry.setLastError(null);
                published = true;
            } else {
                markFailure(entry, "publisher returned false");
            }
        } catch (Exception e) {
            markFailure(entry, e.getMessage() != null ? e.getMessage() : e.toString());
            log.warn("Failed to relay outbox entry {} to {} (attempt {}/{})",
                    entry.getId(), entry.getDestination(), entry.getAttempts(), maxAttempts, e);
        }
        store.update(entry);
        recordMetric(entry, published);
        return published;
    }

    private void markFailure(OutboxEntry entry, String reason) {
        entry.setLastError(reason);
        entry.setStatus(entry.getAttempts() >= maxAttempts ? OutboxStatus.DEAD : OutboxStatus.FAILED);
    }

    private void recordMetric(OutboxEntry entry, boolean published) {
        if (metrics == null) {
            return;
        }
        if (published) {
            metrics.recordPublish(entry.getDestination());
        } else {
            metrics.recordPublishFailure(entry.getDestination());
        }
    }
}
