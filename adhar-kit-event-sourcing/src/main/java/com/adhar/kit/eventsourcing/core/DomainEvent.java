package com.adhar.kit.eventsourcing.core;

import java.time.Instant;

/**
 * Immutable representation of a domain event.
 *
 * <p>Each event captures a state change that occurred on an aggregate,
 * including the serialized payload and metadata for ordering and replay.</p>
 *
 * @param eventId       unique identifier for the event
 * @param aggregateId   identifier of the aggregate that produced this event
 * @param aggregateType fully qualified type name of the aggregate
 * @param version       sequential version within the aggregate stream
 * @param eventType     type discriminator for deserialization
 * @param payload       JSON-serialized event data
 * @param occurredAt    timestamp when the event was produced
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record DomainEvent(
        String eventId,
        String aggregateId,
        String aggregateType,
        int version,
        String eventType,
        String payload,
        Instant occurredAt
) {
}
