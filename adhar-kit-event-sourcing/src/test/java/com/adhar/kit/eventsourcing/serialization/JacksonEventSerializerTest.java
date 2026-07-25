package com.adhar.kit.eventsourcing.serialization;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JacksonEventSerializer")
class JacksonEventSerializerTest {

    record OrderCreated(String customerId, int amount) {
    }

    private ObjectMapper objectMapper;
    private EventTypeRegistry typeRegistry;
    private JacksonEventSerializer serializer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        typeRegistry = new EventTypeRegistry();
        serializer = new JacksonEventSerializer(objectMapper, typeRegistry);
    }

    private DomainEvent event(String eventType, String payload) {
        return new DomainEvent("evt-1", "order-1", "OrderAggregate", 1, eventType, payload, Instant.now());
    }

    @Test
    @DisplayName("serialize then deserialize round-trips a typed payload")
    void serializeThenDeserializeRoundTrips() {
        OrderCreated payload = new OrderCreated("cust-1", 42);

        String json = serializer.serialize(payload);
        DomainEvent event = event("OrderCreated", json);
        OrderCreated result = serializer.deserialize(event, OrderCreated.class);

        assertThat(result).isEqualTo(payload);
    }

    @Test
    @DisplayName("deserialize throws IllegalArgumentException on malformed JSON")
    void deserializeThrowsOnMalformedJson() {
        DomainEvent event = event("OrderCreated", "{not-json");

        assertThatThrownBy(() -> serializer.deserialize(event, OrderCreated.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrderCreated");
    }

    @Test
    @DisplayName("serialize throws IllegalArgumentException when the payload cannot be serialized")
    void serializeThrowsOnUnserializablePayload() {
        Object cyclic = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        };

        assertThatThrownBy(() -> serializer.serialize(cyclic))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deserializeTyped resolves the payload class via the registry")
    void deserializeTypedUsesRegistry() {
        typeRegistry.register("OrderCreated", OrderCreated.class);
        DomainEvent event = event("OrderCreated", serializer.serialize(new OrderCreated("cust-2", 7)));

        Optional<Object> result = serializer.deserializeTyped(event);

        assertThat(result).contains(new OrderCreated("cust-2", 7));
    }

    @Test
    @DisplayName("deserializeTyped returns empty when the event type is not registered")
    void deserializeTypedReturnsEmptyWhenUnregistered() {
        DomainEvent event = event("UnknownEvent", "{}");

        assertThat(serializer.deserializeTyped(event)).isEmpty();
    }
}
