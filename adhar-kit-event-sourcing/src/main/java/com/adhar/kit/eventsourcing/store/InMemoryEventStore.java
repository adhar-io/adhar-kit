package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link EventStore} implementation intended for development and testing.
 *
 * <p>Events are stored in a {@link ConcurrentHashMap} keyed by aggregate ID.
 * Optimistic concurrency is enforced by checking the expected version before appending.
 * Events read back via {@link #getEvents}, {@link #getEventsAfterVersion} and
 * {@link #getAllEvents} are passed through an {@link UpcasterChain} so older payload
 * versions are transparently migrated.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemoryEventStore implements EventStore {

    private final ConcurrentMap<String, List<DomainEvent>> store = new ConcurrentHashMap<>();
    private final List<DomainEvent> insertionOrder = Collections.synchronizedList(new ArrayList<>());
    private final UpcasterChain upcasterChain;

    public InMemoryEventStore() {
        this(UpcasterChain.empty());
    }

    public InMemoryEventStore(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

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
        insertionOrder.addAll(events);
    }

    @Override
    public List<DomainEvent> getEvents(String aggregateId) {
        var events = store.get(aggregateId);
        return events == null ? List.of() : upcasterChain.applyAll(List.copyOf(events));
    }

    @Override
    public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {
        return getEvents(aggregateId).stream()
                .filter(e -> e.version() > version)
                .toList();
    }

    @Override
    public List<DomainEvent> getAllEvents() {
        return upcasterChain.applyAll(List.copyOf(insertionOrder));
    }
}
