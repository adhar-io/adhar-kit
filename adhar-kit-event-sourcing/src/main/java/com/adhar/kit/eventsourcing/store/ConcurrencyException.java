package com.adhar.kit.eventsourcing.store;

/**
 * Thrown when an optimistic concurrency conflict is detected while saving events.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ConcurrencyException extends RuntimeException {

    public ConcurrencyException(String aggregateId, int expectedVersion, int actualVersion) {
        super("Concurrency conflict for aggregate '%s': expected version %d but found %d"
                .formatted(aggregateId, expectedVersion, actualVersion));
    }
}
