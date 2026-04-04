package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.commons.event.AdharCloudEvent;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple in-process {@link EventBus} implementation.
 *
 * <p>Handlers are stored in a {@link ConcurrentHashMap} keyed by event type.
 * Publishing dispatches synchronously to all registered handlers for the
 * matching event type.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SimpleEventBus implements EventBus {

    private final ConcurrentMap<String, List<Consumer<DomainEvent>>> handlers = new ConcurrentHashMap<>();

    @Override
    public void publish(DomainEvent event) {
        var subscribers = handlers.get(event.eventType());
        if (subscribers != null) {
            for (Consumer<DomainEvent> handler : subscribers) {
                try {
                    handler.accept(event);
                } catch (Exception ex) {
                    log.error("Error handling event '{}' for aggregate '{}': {}",
                            event.eventType(), event.aggregateId(), ex.getMessage(), ex);
                }
            }
        }
    }

    @Override
    public void subscribe(String eventType, Consumer<DomainEvent> handler) {
        handlers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Wraps a {@link DomainEvent} in an {@link AdharCloudEvent} envelope and publishes it.
     *
     * <p>The CloudEvent is constructed with source {@code "adhar-kit/event-sourcing"} and a
     * type derived from the domain event's {@code eventType()} prefixed with
     * {@code "com.adhar.eventsourcing."}. The aggregate ID is used as the CloudEvent subject.</p>
     *
     * @param event the domain event to wrap and publish
     */
    public void publishAsCloudEvent(DomainEvent event) {
        String cloudEventType = "com.adhar.eventsourcing." + event.eventType();
        AdharCloudEvent<DomainEvent> cloudEvent = AdharCloudEvent.of(
                "adhar-kit/event-sourcing",
                cloudEventType,
                event.aggregateId(),
                event
        );
        log.debug("Publishing CloudEvent [type={}, subject={}] for domain event '{}'",
                cloudEvent.type(), cloudEvent.subject(), event.eventType());
        publish(event);
    }
}
