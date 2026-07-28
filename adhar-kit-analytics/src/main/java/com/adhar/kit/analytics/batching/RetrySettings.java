package com.adhar.kit.analytics.batching;

/**
 * Configuration for {@link BatchingEventSender}'s failed-batch retry buffer.
 *
 * <p>When {@link #enabled()} is {@code true}, batches that fail to send are
 * placed on a bounded, in-memory retry queue and re-attempted with exponential
 * backoff up to {@link #maxAttempts()} times; once attempts are exhausted (or
 * the queue is full) the batch is handed to an optional {@link SpillStore}, or
 * dropped if none is configured.</p>
 *
 * @param enabled              whether the retry buffer is active
 * @param maxAttempts          maximum delivery attempts per batch (>= 1)
 * @param initialBackoffMillis backoff before the first retry
 * @param multiplier           exponential backoff multiplier per attempt
 * @param maxBackoffMillis     backoff ceiling
 * @param maxBatches           maximum number of batches held in the retry queue
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public record RetrySettings(boolean enabled, int maxAttempts, long initialBackoffMillis,
                            double multiplier, long maxBackoffMillis, int maxBatches) {

    public RetrySettings {
        if (enabled) {
            maxAttempts = Math.max(1, maxAttempts);
            initialBackoffMillis = Math.max(1, initialBackoffMillis);
            multiplier = multiplier <= 0 ? 2.0 : multiplier;
            maxBackoffMillis = Math.max(initialBackoffMillis, maxBackoffMillis);
            maxBatches = Math.max(1, maxBatches);
        }
    }

    /** A disabled configuration: failed batches are logged and dropped (legacy behaviour). */
    public static RetrySettings disabled() {
        return new RetrySettings(false, 0, 0, 1.0, 0, 0);
    }

    /** A sensible default: 3 attempts, 500ms base backoff doubling up to 30s, 1000 batches. */
    public static RetrySettings defaults() {
        return new RetrySettings(true, 3, 500, 2.0, 30_000, 1_000);
    }
}
