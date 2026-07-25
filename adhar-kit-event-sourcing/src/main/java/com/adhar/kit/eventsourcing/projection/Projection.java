package com.adhar.kit.eventsourcing.projection;

import com.adhar.kit.eventsourcing.core.DomainEvent;

import java.util.Set;

/**
 * A read-model builder that reacts to domain events.
 *
 * <p>Projections are registered with a {@link ProjectionManager}, which subscribes them
 * to the {@link com.adhar.kit.eventsourcing.bus.EventBus} for live updates and can
 * {@link ProjectionManager#rebuild rebuild} them from scratch by replaying the full event
 * store.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface Projection {

    /**
     * Returns the unique name of this projection, used as its checkpoint key.
     *
     * @return the projection name
     */
    String getName();

    /**
     * Returns the set of {@link DomainEvent#eventType()} values this projection reacts to.
     *
     * @return the interested event types
     */
    Set<String> interestedEventTypes();

    /**
     * Handles a single domain event, updating the projection's read model.
     *
     * <p>Implementations should let exceptions propagate; the {@link ProjectionManager}
     * isolates and logs failures so that one bad event does not affect other projections
     * or stop event dispatch.</p>
     *
     * @param event the domain event to handle
     */
    void handle(DomainEvent event);
}
