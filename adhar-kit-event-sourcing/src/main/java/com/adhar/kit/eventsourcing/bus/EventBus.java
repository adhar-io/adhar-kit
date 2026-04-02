package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;

import java.util.function.Consumer;

/**
 * Abstraction for publishing and subscribing to domain events.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface EventBus {

    /**
     * Publishes a domain event to all registered handlers for its event type.
     *
     * @param event the domain event to publish
     */
    void publish(DomainEvent event);

    /**
     * Subscribes a handler for a specific event type.
     *
     * @param eventType the event type to subscribe to
     * @param handler   the handler that processes matching events
     */
    void subscribe(String eventType, Consumer<DomainEvent> handler);
}
