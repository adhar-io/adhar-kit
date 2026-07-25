package com.adhar.kit.messaging.dedup;

/**
 * Tracks message ids that have already been processed, so an idempotent consumer can
 * skip re-processing a duplicate delivery (e.g. after a redelivery caused by a broker
 * rebalance, a missed acknowledgment, or a retried publish).
 * <p>
 * Implementations must be safe for concurrent use, since messages for a given
 * subscription may be delivered on multiple threads.
 */
public interface ProcessedMessageStore {

    /**
     * Atomically checks whether the given message id has already been processed and,
     * if not, marks it as processed.
     *
     * @param messageId the message id to check (typically the CloudEvents {@code ce-id}
     *                  header, or a broker-specific message id)
     * @return {@code true} if this is the first time the id has been seen (and it has now
     *         been marked as processed), {@code false} if it was already processed
     *         (i.e. this delivery is a duplicate)
     */
    boolean markIfNotProcessed(String messageId);
}
