package com.adhar.kit.eventsourcing.snapshot;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link SnapshotStore} implementation intended for development and testing.
 *
 * <p>Only the latest snapshot per aggregate is retained; saving a new snapshot for the
 * same aggregate overwrites the previous one.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemorySnapshotStore implements SnapshotStore {

    private final ConcurrentMap<String, Snapshot> store = new ConcurrentHashMap<>();

    @Override
    public void save(Snapshot snapshot) {
        store.put(snapshot.aggregateId(), snapshot);
    }

    @Override
    public Optional<Snapshot> findLatest(String aggregateId) {
        return Optional.ofNullable(store.get(aggregateId));
    }
}
