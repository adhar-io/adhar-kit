package com.adhar.kit.eventsourcing.upcast;

import com.adhar.kit.eventsourcing.core.DomainEvent;

/**
 * Migrates an older version of a domain event's payload to a newer schema.
 *
 * <p>Upcasters let aggregates evolve their event schemas over time without requiring
 * a data migration: old payloads stored in the event store are transparently upgraded
 * as they are read back, before being applied to an aggregate or handled by a projection.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface EventUpcaster {

    /**
     * Indicates whether this upcaster can migrate the given event type/version.
     *
     * @param eventType the event's type discriminator
     * @param version   the aggregate version the event was recorded at (used as a proxy
     *                  for the event schema version in this simplified model)
     * @return {@code true} if this upcaster applies to the given event type and version
     */
    boolean supports(String eventType, int version);

    /**
     * Produces an upgraded {@link DomainEvent} from an older one.
     *
     * <p>Implementations typically return a new {@code DomainEvent} with a migrated
     * {@code payload} (and optionally a new {@code eventType}), preserving identity fields
     * such as {@code eventId}, {@code aggregateId} and {@code version}.</p>
     *
     * @param event the event to upcast
     * @return the migrated event
     */
    DomainEvent upcast(DomainEvent event);
}
