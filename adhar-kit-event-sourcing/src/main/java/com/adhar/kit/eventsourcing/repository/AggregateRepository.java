package com.adhar.kit.eventsourcing.repository;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.core.AggregateRoot;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.store.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Generic repository that persists and reconstitutes event-sourced aggregates.
 *
 * <p>Loading an aggregate replays its full event history from the
 * {@link EventStore}. Saving an aggregate persists uncommitted events
 * and publishes them via the {@link EventBus}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class AggregateRepository {

    private final EventStore eventStore;
    private final EventBus eventBus;

    /**
     * Loads an aggregate by replaying all stored events.
     *
     * @param aggregateId the aggregate identifier
     * @param type        the aggregate class (must have a no-arg constructor)
     * @param <T>         aggregate type
     * @return the reconstituted aggregate
     * @throws IllegalStateException if no events exist for the given aggregate ID
     */
    public <T extends AggregateRoot> T load(String aggregateId, Class<T> type) {
        List<DomainEvent> events = eventStore.getEvents(aggregateId);
        if (events.isEmpty()) {
            throw new IllegalStateException("No events found for aggregate: " + aggregateId);
        }

        try {
            T aggregate = type.getDeclaredConstructor().newInstance();
            for (DomainEvent event : events) {
                aggregate.applyEvent(event);
            }
            aggregate.markEventsAsCommitted();
            return aggregate;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to instantiate aggregate: " + type.getName(), ex);
        }
    }

    /**
     * Persists all uncommitted events from the aggregate and publishes them.
     *
     * @param aggregate the aggregate whose uncommitted events should be saved
     */
    public void save(AggregateRoot aggregate) {
        List<DomainEvent> uncommitted = aggregate.getUncommittedEvents();
        if (uncommitted.isEmpty()) {
            return;
        }

        int expectedVersion = aggregate.getVersion() - uncommitted.size();
        eventStore.saveEvents(aggregate.getAggregateId(), uncommitted, expectedVersion);

        for (DomainEvent event : uncommitted) {
            eventBus.publish(event);
        }

        aggregate.markEventsAsCommitted();
        log.debug("Saved {} events for aggregate '{}'", uncommitted.size(), aggregate.getAggregateId());
    }
}
