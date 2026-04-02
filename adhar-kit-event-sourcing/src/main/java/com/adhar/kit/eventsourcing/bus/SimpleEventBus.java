package com.adhar.kit.eventsourcing.bus;

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
}
