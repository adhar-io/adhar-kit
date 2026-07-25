package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffers analytics events in a bounded, thread-safe queue and flushes them
 * to PostHog's {@code /batch/} endpoint either when the configured batch
 * size is reached or on a fixed schedule (whichever comes first).
 *
 * <p><b>Overflow policy</b>: when the queue is full, behavior is governed by
 * {@link AnalyticsProperties.OverflowPolicy}:</p>
 * <ul>
 *   <li>{@code DROP_OLDEST} (default) - the oldest buffered event is evicted
 *       to make room for the newest one. This favors availability/latency of
 *       the calling thread over completeness of very old events, which is
 *       usually the right tradeoff for fire-and-forget analytics.</li>
 *   <li>{@code BLOCK} - the calling thread blocks until space frees up. Use
 *       this when losing events is worse than backpressure on callers.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class BatchingEventSender {

    private final PostHogClient client;
    private final int batchSize;
    private final LinkedBlockingDeque<CaptureEvent> queue;
    private final ScheduledExecutorService scheduler;
    private final AnalyticsProperties.OverflowPolicy overflowPolicy;
    private final AtomicLong droppedCount = new AtomicLong();
    private final ReentrantLock flushLock = new ReentrantLock();
    private volatile boolean shutdown = false;

    public BatchingEventSender(PostHogClient client, int batchSize, Duration flushInterval,
                                int queueCapacity, AnalyticsProperties.OverflowPolicy overflowPolicy) {
        this.client = client;
        this.batchSize = Math.max(1, batchSize);
        this.queue = new LinkedBlockingDeque<>(Math.max(1, queueCapacity));
        this.overflowPolicy = overflowPolicy != null ? overflowPolicy : AnalyticsProperties.OverflowPolicy.DROP_OLDEST;

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "posthog-batch-flush");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);

        long intervalMillis = Math.max(50, flushInterval == null ? 10_000 : flushInterval.toMillis());
        this.scheduler.scheduleAtFixedRate(this::safeFlush, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Enqueues an event for later delivery. May trigger an immediate flush if
     * the configured batch size threshold has been reached.
     */
    public void enqueue(CaptureEvent event) {
        if (event == null || shutdown) {
            return;
        }

        if (overflowPolicy == AnalyticsProperties.OverflowPolicy.BLOCK) {
            try {
                queue.putLast(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while enqueuing analytics event '{}'; event dropped", event.event());
                return;
            }
        } else {
            while (!queue.offerLast(event)) {
                CaptureEvent dropped = queue.pollFirst();
                if (dropped == null) {
                    // Race with a concurrent drain; retry.
                    continue;
                }
                droppedCount.incrementAndGet();
                log.warn("Analytics event queue full ({} capacity); dropped oldest event '{}'",
                        queue.remainingCapacity() + queue.size(), dropped.event());
            }
        }

        if (queue.size() >= batchSize) {
            safeFlush();
        }
    }

    /**
     * Drains the queue and sends it to PostHog in chunks of at most
     * {@code batchSize} events. Safe to call concurrently; overlapping calls
     * are coalesced (a flush already in progress will pick up newly queued
     * events on its next scheduled run).
     */
    public void flush() {
        safeFlush();
    }

    private void safeFlush() {
        if (!flushLock.tryLock()) {
            return;
        }
        try {
            List<CaptureEvent> drained = new ArrayList<>(queue.size());
            queue.drainTo(drained);
            if (drained.isEmpty()) {
                return;
            }
            for (int i = 0; i < drained.size(); i += batchSize) {
                List<CaptureEvent> chunk = drained.subList(i, Math.min(i + batchSize, drained.size()));
                try {
                    client.batch(chunk);
                } catch (Exception e) {
                    log.error("Failed to send analytics batch of {} events", chunk.size(), e);
                }
            }
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * Stops the scheduled flush and performs a final, synchronous flush so
     * that no buffered events are lost on shutdown.
     */
    public void shutdown() {
        shutdown = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        flush();
    }

    public int pendingCount() {
        return queue.size();
    }

    public long droppedCount() {
        return droppedCount.get();
    }
}
