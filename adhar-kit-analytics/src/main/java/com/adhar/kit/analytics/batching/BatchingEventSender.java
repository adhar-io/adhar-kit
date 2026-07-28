package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    // --- Retry / offline spill ---
    private final RetrySettings retry;
    private final SpillStore spillStore;
    private final DelayQueue<RetryBatch> retryQueue = new DelayQueue<>();
    private final AtomicInteger retryQueueSize = new AtomicInteger();
    private final AtomicLong retriedCount = new AtomicLong();
    private final AtomicLong retryDroppedCount = new AtomicLong();
    private final AtomicLong spilledCount = new AtomicLong();

    /**
     * Legacy constructor: batching only, with the retry buffer disabled (failed
     * batches are logged and dropped).
     */
    public BatchingEventSender(PostHogClient client, int batchSize, Duration flushInterval,
                                int queueCapacity, AnalyticsProperties.OverflowPolicy overflowPolicy) {
        this(client, batchSize, flushInterval, queueCapacity, overflowPolicy, RetrySettings.disabled(), null);
    }

    /**
     * Full constructor adding a bounded, exponential-backoff retry buffer for
     * failed batches and an optional {@link SpillStore} for offline durability.
     *
     * <p>If {@code spillStore} is non-null and retry is enabled, any batches it
     * holds from a previous run are re-loaded and re-queued for delivery on
     * construction (startup replay).</p>
     */
    public BatchingEventSender(PostHogClient client, int batchSize, Duration flushInterval,
                                int queueCapacity, AnalyticsProperties.OverflowPolicy overflowPolicy,
                                RetrySettings retry, SpillStore spillStore) {
        this.client = client;
        this.batchSize = Math.max(1, batchSize);
        this.queue = new LinkedBlockingDeque<>(Math.max(1, queueCapacity));
        this.overflowPolicy = overflowPolicy != null ? overflowPolicy : AnalyticsProperties.OverflowPolicy.DROP_OLDEST;
        this.retry = retry != null ? retry : RetrySettings.disabled();
        this.spillStore = spillStore;

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "posthog-batch-flush");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);

        long intervalMillis = Math.max(50, flushInterval == null ? 10_000 : flushInterval.toMillis());
        this.scheduler.scheduleAtFixedRate(this::safeFlush, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);

        if (this.retry.enabled()) {
            if (this.spillStore != null) {
                for (List<CaptureEvent> batch : this.spillStore.loadAndClear()) {
                    scheduleRetry(batch, 1);
                }
            }
            long retryPoll = Math.max(25, Math.min(this.retry.initialBackoffMillis(), 500));
            this.scheduler.scheduleAtFixedRate(this::processRetries, retryPoll, retryPoll, TimeUnit.MILLISECONDS);
        }
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
                    if (retry.enabled()) {
                        log.warn("Analytics batch of {} events failed; scheduling retry", chunk.size(), e);
                        scheduleRetry(new ArrayList<>(chunk), 1);
                    } else {
                        log.error("Failed to send analytics batch of {} events", chunk.size(), e);
                    }
                }
            }
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * Processes retry batches whose backoff has elapsed. On repeated failure a
     * batch is re-scheduled with a longer backoff until {@code maxAttempts} is
     * reached, after which it is spilled (if a {@link SpillStore} is configured)
     * or dropped.
     */
    private void processRetries() {
        RetryBatch batch;
        while ((batch = retryQueue.poll()) != null) {
            retryQueueSize.decrementAndGet();
            try {
                client.batch(batch.events);
                retriedCount.incrementAndGet();
            } catch (Exception e) {
                log.warn("Retry attempt {} failed for analytics batch of {} events",
                        batch.attempt, batch.events.size());
                scheduleRetry(batch.events, batch.attempt + 1);
            }
        }
    }

    private void scheduleRetry(List<CaptureEvent> batch, int attempt) {
        if (!retry.enabled() || batch == null || batch.isEmpty()) {
            return;
        }
        if (attempt > retry.maxAttempts()) {
            spillOrDrop(batch);
            return;
        }
        if (retryQueueSize.get() >= retry.maxBatches()) {
            spillOrDrop(batch);
            return;
        }
        long delay = backoffMillis(attempt);
        retryQueue.put(new RetryBatch(batch, attempt, System.currentTimeMillis() + delay));
        retryQueueSize.incrementAndGet();
    }

    private long backoffMillis(int attempt) {
        double raw = retry.initialBackoffMillis() * Math.pow(retry.multiplier(), attempt - 1);
        return (long) Math.min(retry.maxBackoffMillis(), raw);
    }

    private void spillOrDrop(List<CaptureEvent> batch) {
        if (spillStore != null) {
            try {
                spillStore.write(batch);
                spilledCount.incrementAndGet();
                return;
            } catch (Exception e) {
                log.error("Failed to spill analytics batch of {} events to disk", batch.size(), e);
            }
        }
        retryDroppedCount.incrementAndGet();
        log.warn("Dropping analytics batch of {} events (retries exhausted, spill {})",
                batch.size(), spillStore != null ? "failed" : "disabled");
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

        // Persist any batches still awaiting retry so they survive a restart.
        if (retry.enabled()) {
            List<RetryBatch> remaining = new ArrayList<>(retryQueue);
            retryQueue.clear();
            retryQueueSize.set(0);
            for (RetryBatch batch : remaining) {
                spillOrDrop(batch.events);
            }
        }
    }

    public int pendingCount() {
        return queue.size();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    /** Number of batches currently awaiting retry. */
    public int retryPendingCount() {
        return retryQueueSize.get();
    }

    /** Number of batches successfully delivered on a retry attempt. */
    public long retriedCount() {
        return retriedCount.get();
    }

    /** Number of batches written to the offline spill store. */
    public long spilledCount() {
        return spilledCount.get();
    }

    /** Number of batches dropped after retries were exhausted with no spill store. */
    public long retryDroppedCount() {
        return retryDroppedCount.get();
    }

    /**
     * A failed batch awaiting its next retry attempt. Ordered by ready time so
     * the {@link DelayQueue} releases due batches first.
     */
    private static final class RetryBatch implements Delayed {
        private final List<CaptureEvent> events;
        private final int attempt;
        private final long readyAtMillis;

        RetryBatch(List<CaptureEvent> events, int attempt, long readyAtMillis) {
            this.events = events;
            this.attempt = attempt;
            this.readyAtMillis = readyAtMillis;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(readyAtMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(this.readyAtMillis, ((RetryBatch) other).readyAtMillis);
        }
    }
}
