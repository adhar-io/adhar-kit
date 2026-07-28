package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventBus")
class KafkaEventBusTest {

    private static final String TOPIC = "test.events";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DomainEventKafkaSerde serde;
    private KafkaEventBus eventBus;

    @BeforeEach
    void setUp() {
        serde = new DomainEventKafkaSerde(new ObjectMapper(), new EventTypeRegistry());
        eventBus = new KafkaEventBus(kafkaTemplate, serde, TOPIC);
    }

    private DomainEvent event(String type, String aggregateId) {
        return new DomainEvent("evt-1", aggregateId, "OrderAggregate", 1, type, "{\"k\":\"v\"}",
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("publish sends the serialized event keyed by aggregate id to the configured topic")
    void publishSendsToKafka() {
        DomainEvent event = event("OrderCreated", "order-7");

        eventBus.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq("order-7"), eq(serde.serialize(event)));
    }

    @Test
    @DisplayName("onMessage dispatches a consumed record to subscribers of its event type")
    void onMessageDispatchesToSubscribers() {
        List<DomainEvent> received = new ArrayList<>();
        eventBus.subscribe("OrderCreated", received::add);
        DomainEvent event = event("OrderCreated", "order-7");

        eventBus.onMessage(serde.serialize(event));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()).isEqualTo(event);
    }

    @Test
    @DisplayName("onMessage only notifies subscribers registered for the event's type")
    void onMessageFiltersByType() {
        List<DomainEvent> orders = new ArrayList<>();
        List<DomainEvent> payments = new ArrayList<>();
        eventBus.subscribe("OrderCreated", orders::add);
        eventBus.subscribe("PaymentReceived", payments::add);

        eventBus.onMessage(serde.serialize(event("OrderCreated", "order-1")));

        assertThat(orders).hasSize(1);
        assertThat(payments).isEmpty();
    }

    @Test
    @DisplayName("a failing subscriber does not prevent other subscribers from receiving the event")
    void failingSubscriberIsolated() {
        List<DomainEvent> received = new ArrayList<>();
        eventBus.subscribe("OrderCreated", e -> { throw new RuntimeException("boom"); });
        eventBus.subscribe("OrderCreated", received::add);

        eventBus.onMessage(serde.serialize(event("OrderCreated", "order-1")));

        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("onMessage swallows malformed payloads without throwing")
    void onMessageIgnoresMalformedPayload() {
        List<DomainEvent> received = new ArrayList<>();
        eventBus.subscribe("OrderCreated", received::add);

        eventBus.onMessage("not-json");

        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("onMessage for an event type with no subscribers is a no-op")
    void onMessageNoSubscribers() {
        eventBus.onMessage(serde.serialize(event("Unhandled", "agg-1")));
        // no exception expected
    }
}
