package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DomainEventKafkaSerde")
class DomainEventKafkaSerdeTest {

    private EventTypeRegistry registry;
    private DomainEventKafkaSerde serde;

    record OrderCreated(String orderId, int amount) { }

    @BeforeEach
    void setUp() {
        registry = new EventTypeRegistry();
        serde = new DomainEventKafkaSerde(new ObjectMapper(), registry);
    }

    private DomainEvent event(String type, String payload) {
        return new DomainEvent("evt-1", "order-1", "OrderAggregate", 3, type, payload,
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("round-trips a domain event envelope including its Instant timestamp")
    void roundTripsEnvelope() {
        DomainEvent original = event("OrderCreated", "{\"orderId\":\"order-1\",\"amount\":42}");

        String wire = serde.serialize(original);
        DomainEvent restored = serde.deserialize(wire);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.occurredAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("deserialize validates payload against the registered type without failing on mismatch")
    void deserializeToleratesIncompatiblePayload() {
        registry.register("OrderCreated", OrderCreated.class);
        DomainEvent good = event("OrderCreated", "{\"orderId\":\"order-1\",\"amount\":42}");
        DomainEvent bad = event("OrderCreated", "{\"orderId\":\"order-1\",\"amount\":\"not-a-number\"}");

        assertThat(serde.deserialize(serde.serialize(good)).eventType()).isEqualTo("OrderCreated");
        // Incompatible payload is logged, not thrown: the envelope still comes back intact.
        assertThat(serde.deserialize(serde.serialize(bad)).payload())
                .isEqualTo("{\"orderId\":\"order-1\",\"amount\":\"not-a-number\"}");
    }

    @Test
    @DisplayName("deserialize of a malformed envelope throws IllegalArgumentException")
    void deserializeMalformedThrows() {
        assertThatThrownBy(() -> serde.deserialize("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("unregistered event types skip payload validation and round-trip cleanly")
    void unregisteredTypeRoundTrips() {
        DomainEvent original = event("UnknownEvent", "{\"anything\":true}");
        assertThat(serde.deserialize(serde.serialize(original))).isEqualTo(original);
    }
}
