package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.eventsourcing.core.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link EventStore} implementation intended for development and testing.
 *
 * <p>Events are stored in a {@link ConcurrentHashMap} keyed by aggregate ID.
 * Optimistic concurrency is enforced by checking the expected version before appending.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemoryEventStore implements EventStore {

    private final ConcurrentMap<String, List<DomainEvent>> store = new ConcurrentHashMap<>();

    @Override
    public void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {
        var eventStream = store.computeIfAbsent(aggregateId, _ -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (eventStream) {
            int currentVersion = eventStream.isEmpty() ? 0 : eventStream.getLast().version();
            if (currentVersion != expectedVersion) {
                throw new ConcurrencyException(aggregateId, expectedVersion, currentVersion);
            }
            eventStream.addAll(events);
        }
    }

    @Override
    public List<DomainEvent> getEvents(String aggregateId) {
        var events = store.get(aggregateId);
        return events == null ? List.of() : List.copyOf(events);
    }

    @Override
    public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {
        return getEvents(aggregateId).stream()
                .filter(e -> e.version() > version)
                .toList();
    }
}
