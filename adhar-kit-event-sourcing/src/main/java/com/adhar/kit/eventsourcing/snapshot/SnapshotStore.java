package com.adhar.kit.eventsourcing.snapshot;

import java.util.Optional;

/**
 * Persistence abstraction for aggregate {@link Snapshot}s.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface SnapshotStore {

    /**
     * Persists a snapshot.
     *
     * @param snapshot the snapshot to persist
     */
    void save(Snapshot snapshot);

    /**
     * Retrieves the most recent snapshot for the given aggregate, if any.
     *
     * @param aggregateId the aggregate identifier
     * @return the latest snapshot, or empty if none has been captured
     */
    Optional<Snapshot> findLatest(String aggregateId);
}
