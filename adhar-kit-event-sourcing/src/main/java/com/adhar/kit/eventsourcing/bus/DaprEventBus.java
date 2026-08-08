package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@link EventBus} implementation that publishes domain events through the Dapr
 * pub/sub building block, so the actual broker (Kafka, RabbitMQ, Redis Streams,
 * Azure Service Bus, ...) is whatever the sidecar's pub/sub component is.
 *
 * <p>Events are serialized with the same envelope codec as {@link KafkaEventBus}
 * ({@link DomainEventKafkaSerde}, a transport-neutral JSON serde despite its
 * name) and published keyed by aggregate id ({@code partitionKey} metadata) so
 * partitioned components preserve per-aggregate ordering.</p>
 *
 * <p><b>Consuming remote events:</b> Dapr delivers subscriptions over HTTP to
 * the application, which cannot be initiated from this class. Wire a
 * {@code @DaprSubscribe} handler (from {@code adhar-kit-dapr}) for the
 * configured topic and pass the raw CloudEvent data string to
 * {@link #dispatch(String)}; locally registered {@link #subscribe} handlers are
 * then invoked exactly like {@link SimpleEventBus}/{@link KafkaEventBus}
 * consumers.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprEventBus implements EventBus {

    private final DaprFacade daprFacade;
    private final DomainEventKafkaSerde serde;
    private final String pubsubName;
    private final String topic;
    private final ConcurrentMap<String, List<Consumer<DomainEvent>>> handlers = new ConcurrentHashMap<>();

    /**
     * Creates the Dapr-backed event bus.
     *
     * @param daprFacade the Dapr facade used for publishing
     * @param serde      the domain-event envelope serde
     * @param pubsubName the Dapr pub/sub component name
     * @param topic      the topic domain events are published to
     */
    public DaprEventBus(DaprFacade daprFacade, DomainEventKafkaSerde serde,
                        String pubsubName, String topic) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.serde = Objects.requireNonNull(serde, "serde must not be null");
        this.pubsubName = Objects.requireNonNull(pubsubName, "pubsubName must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
    }

    @Override
    public void publish(DomainEvent event) {
        String payload = serde.serialize(event);
        log.debug("Publishing domain event '{}' for aggregate '{}' to Dapr pubsub '{}' topic '{}'",
                event.eventType(), event.aggregateId(), pubsubName, topic);
        daprFacade.publishEvent(pubsubName, topic, payload,
                Map.of("partitionKey", event.aggregateId()));
    }

    @Override
    public void subscribe(String eventType, Consumer<DomainEvent> handler) {
        handlers.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Dispatches an incoming serialized domain event (e.g. the data of a Dapr
     * CloudEvent delivered to a {@code @DaprSubscribe} handler) to locally
     * registered handlers.
     *
     * @param message the serialized domain-event envelope
     */
    public void dispatch(String message) {
        DomainEvent event = serde.deserialize(message);
        List<Consumer<DomainEvent>> registered = handlers.get(event.eventType());
        if (registered == null || registered.isEmpty()) {
            log.debug("No local handlers for domain event type '{}'", event.eventType());
            return;
        }
        for (Consumer<DomainEvent> handler : registered) {
            handler.accept(event);
        }
    }
}
