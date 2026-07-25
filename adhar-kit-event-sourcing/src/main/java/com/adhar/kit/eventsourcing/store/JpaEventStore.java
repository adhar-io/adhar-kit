package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed {@link EventStore} implementation.
 *
 * <p>Persists domain events to the {@code domain_events} table via
 * {@link EventEntryRepository}. Optimistic concurrency is checked by
 * comparing the latest stored version against the expected version. Events read back are
 * passed through an {@link UpcasterChain} so older payload versions are transparently
 * migrated.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@ConditionalOnClass(EntityManager.class)
public class JpaEventStore implements EventStore {

    private final EventEntryRepository repository;
    private final UpcasterChain upcasterChain;

    public JpaEventStore(EventEntryRepository repository) {
        this(repository, UpcasterChain.empty());
    }

    public JpaEventStore(EventEntryRepository repository, UpcasterChain upcasterChain) {
        this.repository = repository;
        this.upcasterChain = upcasterChain;
    }

    @Override
    public void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {
        var existing = repository.findByAggregateIdOrderByVersionAsc(aggregateId);
        int currentVersion = existing.isEmpty() ? 0 : existing.getLast().getVersion();

        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException(aggregateId, expectedVersion, currentVersion);
        }

        Instant storedAt = Instant.now();
        List<EventEntry> entries = events.stream()
                .map(event -> new EventEntry(
                        null,
                        event.aggregateId(),
                        event.aggregateType(),
                        event.version(),
                        event.eventType(),
                        event.payload(),
                        event.occurredAt(),
                        storedAt
                ))
                .toList();

        repository.saveAll(entries);
    }

    @Override
    public List<DomainEvent> getEvents(String aggregateId) {
        return upcasterChain.applyAll(repository.findByAggregateIdOrderByVersionAsc(aggregateId).stream()
                .map(this::toDomainEvent)
                .toList());
    }

    @Override
    public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {
        return upcasterChain.applyAll(
                repository.findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(aggregateId, version).stream()
                        .map(this::toDomainEvent)
                        .toList());
    }

    @Override
    public List<DomainEvent> getAllEvents() {
        return upcasterChain.applyAll(repository.findAllByOrderByStoredAtAsc().stream()
                .map(this::toDomainEvent)
                .toList());
    }

    private DomainEvent toDomainEvent(EventEntry entry) {
        return new DomainEvent(
                entry.getId().toString(),
                entry.getAggregateId(),
                entry.getAggregateType(),
                entry.getVersion(),
                entry.getEventType(),
                entry.getPayload(),
                entry.getOccurredAt()
        );
    }
}
