package com.adhar.kit.eventsourcing.snapshot;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link SnapshotStore} implementation.
 *
 * <p>Persists snapshots to the {@code aggregate_snapshots} table via
 * {@link SnapshotEntryRepository}. The latest snapshot for an aggregate is the one with
 * the highest {@code version}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@ConditionalOnClass(EntityManager.class)
public class JpaSnapshotStore implements SnapshotStore {

    private final SnapshotEntryRepository repository;

    public JpaSnapshotStore(SnapshotEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Snapshot snapshot) {
        SnapshotEntry entry = new SnapshotEntry(
                UUID.randomUUID(),
                snapshot.aggregateId(),
                snapshot.aggregateType(),
                snapshot.version(),
                snapshot.payload(),
                snapshot.timestamp()
        );
        repository.save(entry);
    }

    @Override
    public Optional<Snapshot> findLatest(String aggregateId) {
        return repository.findTopByAggregateIdOrderByVersionDesc(aggregateId)
                .map(entry -> new Snapshot(
                        entry.getAggregateId(),
                        entry.getAggregateType(),
                        entry.getVersion(),
                        entry.getPayload(),
                        entry.getCapturedAt()
                ));
    }
}
