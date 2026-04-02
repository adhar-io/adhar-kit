package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.eventsourcing.core.DomainEvent;

import java.util.List;

/**
 * Persistence abstraction for domain events.
 *
 * <p>Implementations must guarantee that events for a given aggregate are
 * stored atomically and that optimistic concurrency is enforced via the
 * {@code expectedVersion} parameter.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface EventStore {

    /**
     * Persists a batch of events for the given aggregate.
     *
     * @param aggregateId     the aggregate identifier
     * @param events          events to persist
     * @param expectedVersion the version the aggregate was at before these events;
     *                        used for optimistic concurrency control
     * @throws ConcurrencyException if the current stored version does not match {@code expectedVersion}
     */
    void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion);

    /**
     * Retrieves all events for the given aggregate, ordered by version.
     *
     * @param aggregateId the aggregate identifier
     * @return ordered list of domain events
     */
    List<DomainEvent> getEvents(String aggregateId);

    /**
     * Retrieves events for the given aggregate that occurred after the specified version.
     *
     * @param aggregateId the aggregate identifier
     * @param version     only events with a version greater than this value are returned
     * @return ordered list of domain events after the given version
     */
    List<DomainEvent> getEventsAfterVersion(String aggregateId, int version);
}
