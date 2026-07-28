package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;

import java.util.List;

/**
 * A durable overflow store for analytics batches that could not be delivered
 * (retries exhausted or the in-memory retry queue is full). Implementations
 * persist batches so they survive a process restart and can be re-loaded and
 * re-sent later.
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public interface SpillStore {

    /**
     * Persists a single failed batch.
     */
    void write(List<CaptureEvent> batch);

    /**
     * Loads every persisted batch and removes them from the store, so a caller
     * can re-enqueue them for delivery exactly once on startup.
     *
     * @return the previously-persisted batches (never {@code null})
     */
    List<List<CaptureEvent>> loadAndClear();
}
