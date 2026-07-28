package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@link EventBus} implementation that publishes and consumes domain events over Apache Kafka
 * using Spring Kafka's {@link KafkaTemplate} and {@link KafkaListener} infrastructure.
 *
 * <p>Published events are serialized to JSON via {@link DomainEventKafkaSerde} (which routes
 * through the shared {@code EventTypeRegistry}) and written to a single configured topic, keyed by
 * aggregate id so that all events for one aggregate land on the same partition and preserve order.
 * Incoming records are deserialized and dispatched to locally registered handlers keyed by event
 * type, mirroring {@link SimpleEventBus} semantics so projections, catch-up subscriptions and sagas
 * behave identically regardless of transport.</p>
 *
 * <p>This bean is only created when {@code spring-kafka} is on the classpath and a
 * {@code KafkaTemplate} bean is available, keeping the dependency strictly optional.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class KafkaEventBus implements EventBus {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DomainEventKafkaSerde serde;
    private final String topic;
    private final ConcurrentMap<String, List<Consumer<DomainEvent>>> handlers = new ConcurrentHashMap<>();

    public KafkaEventBus(KafkaTemplate<String, String> kafkaTemplate, DomainEventKafkaSerde serde, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.serde = serde;
        this.topic = topic;
    }

    @Override
    public void publish(DomainEvent event) {
        String payload = serde.serialize(event);
        log.debug("Publishing domain event '{}' for aggregate '{}' to Kafka topic '{}'",
                event.eventType(), event.aggregateId(), topic);
        kafkaTemplate.send(topic, event.aggregateId(), payload);
    }

    @Override
    public void subscribe(String eventType, Consumer<DomainEvent> handler) {
        handlers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Kafka listener entry point invoked by the Spring Kafka container for every consumed record.
     *
     * <p>The message is deserialized back into a {@link DomainEvent} and dispatched to all locally
     * registered handlers for its event type. It is also directly invokable in tests to simulate a
     * consumed record without a running broker.</p>
     *
     * @param message the raw JSON message body
     */
    @KafkaListener(
            topics = "${adhar.event-sourcing.kafka.topic:adhar.event-sourcing.events}",
            groupId = "${adhar.event-sourcing.kafka.group-id:adhar-event-sourcing}")
    public void onMessage(String message) {
        DomainEvent event;
        try {
            event = serde.deserialize(message);
        } catch (RuntimeException ex) {
            log.error("Failed to deserialize domain event from Kafka topic '{}': {}", topic, ex.getMessage(), ex);
            return;
        }
        dispatch(event);
    }

    private void dispatch(DomainEvent event) {
        List<Consumer<DomainEvent>> subscribers = handlers.get(event.eventType());
        if (subscribers == null) {
            return;
        }
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
