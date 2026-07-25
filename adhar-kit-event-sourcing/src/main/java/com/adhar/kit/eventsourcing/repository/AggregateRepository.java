package com.adhar.kit.eventsourcing.repository;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.core.AggregateRoot;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.snapshot.Snapshot;
import com.adhar.kit.eventsourcing.snapshot.SnapshotStore;
import com.adhar.kit.eventsourcing.store.EventStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Generic repository that persists and reconstitutes event-sourced aggregates.
 *
 * <p>Loading an aggregate replays events from the {@link EventStore}. When a
 * {@link SnapshotStore} is configured and the aggregate supports snapshotting (see
 * {@link AggregateRoot#createSnapshotState()}), loading restores the aggregate from its
 * latest snapshot and replays only the events recorded after that snapshot's version. If
 * no snapshot exists, or the aggregate does not override the snapshot hooks, loading
 * transparently falls back to replaying the full event stream.</p>
 *
 * <p>Saving an aggregate persists uncommitted events, publishes them via the
 * {@link EventBus}, and - when a {@link SnapshotStore} and a positive
 * {@code snapshotInterval} are configured - captures a new snapshot whenever the
 * aggregate's version crosses a multiple of the interval.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AggregateRepository {

    private final EventStore eventStore;
    private final EventBus eventBus;
    private final SnapshotStore snapshotStore;
    private final int snapshotInterval;

    /**
     * Creates a repository without snapshot support (existing behavior: {@code load}
     * always replays the full event stream).
     *
     * @param eventStore the event store
     * @param eventBus   the event bus
     */
    public AggregateRepository(EventStore eventStore, EventBus eventBus) {
        this(eventStore, eventBus, null, 0);
    }

    /**
     * Creates a repository with snapshot support.
     *
     * @param eventStore       the event store
     * @param eventBus         the event bus
     * @param snapshotStore    the snapshot store, or {@code null} to disable snapshotting
     * @param snapshotInterval number of events between snapshots; snapshotting is disabled
     *                         if less than or equal to {@code 0}
     */
    public AggregateRepository(EventStore eventStore, EventBus eventBus, SnapshotStore snapshotStore, int snapshotInterval) {
        this.eventStore = eventStore;
        this.eventBus = eventBus;
        this.snapshotStore = snapshotStore;
        this.snapshotInterval = snapshotInterval;
    }

    /**
     * Loads an aggregate, restoring from the latest snapshot (if available and supported
     * by the aggregate) and replaying only subsequent events; otherwise replays the full
     * event stream.
     *
     * @param aggregateId the aggregate identifier
     * @param type        the aggregate class (must have a no-arg constructor)
     * @param <T>         aggregate type
     * @return the reconstituted aggregate
     * @throws IllegalStateException if no events (and no snapshot) exist for the given aggregate ID
     */
    public <T extends AggregateRoot> T load(String aggregateId, Class<T> type) {
        T aggregate = instantiate(type);

        boolean restoredFromSnapshot = false;
        int fromVersion = 0;

        if (snapshotStore != null) {
            Optional<Snapshot> snapshotOpt = snapshotStore.findLatest(aggregateId);
            if (snapshotOpt.isPresent()) {
                Snapshot snapshot = snapshotOpt.get();
                try {
                    aggregate.applySnapshot(aggregateId, snapshot.payload(), snapshot.version());
                    fromVersion = snapshot.version();
                    restoredFromSnapshot = true;
                    log.debug("Restored aggregate '{}' from snapshot at version {}", aggregateId, fromVersion);
                } catch (UnsupportedOperationException ex) {
                    log.debug("Aggregate type {} does not support snapshot restore; falling back to full replay",
                            type.getName());
                }
            }
        }

        List<DomainEvent> events = restoredFromSnapshot
                ? eventStore.getEventsAfterVersion(aggregateId, fromVersion)
                : eventStore.getEvents(aggregateId);

        if (events.isEmpty() && !restoredFromSnapshot) {
            throw new IllegalStateException("No events found for aggregate: " + aggregateId);
        }

        for (DomainEvent event : events) {
            aggregate.applyEvent(event);
        }
        aggregate.markEventsAsCommitted();
        return aggregate;
    }

    /**
     * Persists all uncommitted events from the aggregate, publishes them, and captures a
     * snapshot if the aggregate's version has crossed a snapshot interval boundary.
     *
     * @param aggregate the aggregate whose uncommitted events should be saved
     */
    public void save(AggregateRoot aggregate) {
        List<DomainEvent> uncommitted = aggregate.getUncommittedEvents();
        if (uncommitted.isEmpty()) {
            return;
        }

        int previousVersion = aggregate.getVersion() - uncommitted.size();
        eventStore.saveEvents(aggregate.getAggregateId(), uncommitted, previousVersion);

        for (DomainEvent event : uncommitted) {
            eventBus.publish(event);
        }

        aggregate.markEventsAsCommitted();
        log.debug("Saved {} events for aggregate '{}'", uncommitted.size(), aggregate.getAggregateId());

        maybeCreateSnapshot(aggregate, previousVersion);
    }

    private void maybeCreateSnapshot(AggregateRoot aggregate, int previousVersion) {
        if (snapshotStore == null || snapshotInterval <= 0) {
            return;
        }

        int currentVersion = aggregate.getVersion();
        boolean crossedBoundary = (currentVersion / snapshotInterval) > (previousVersion / snapshotInterval);
        if (!crossedBoundary) {
            return;
        }

        try {
            String state = aggregate.createSnapshotState();
            Snapshot snapshot = new Snapshot(
                    aggregate.getAggregateId(),
                    aggregate.getClass().getName(),
                    currentVersion,
                    state,
                    Instant.now()
            );
            snapshotStore.save(snapshot);
            log.debug("Created snapshot for aggregate '{}' at version {}", aggregate.getAggregateId(), currentVersion);
        } catch (UnsupportedOperationException ex) {
            log.debug("Aggregate type {} does not support snapshotting; skipping snapshot",
                    aggregate.getClass().getName());
        }
    }

    private <T extends AggregateRoot> T instantiate(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to instantiate aggregate: " + type.getName(), ex);
        }
    }
}
