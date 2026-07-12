package com.adhar.kit.eventsourcing.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventEntry")
class EventEntryTest {

    @Test
    @DisplayName("all-args constructor populates all fields")
    void allArgsConstructor() {
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant storedAt = Instant.parse("2024-01-02T00:00:00Z");

        EventEntry entry = new EventEntry(id, "order-1", "OrderAggregate", 3,
                "OrderShipped", "{\"x\":1}", occurredAt, storedAt);

        assertThat(entry.getId()).isEqualTo(id);
        assertThat(entry.getAggregateId()).isEqualTo("order-1");
        assertThat(entry.getAggregateType()).isEqualTo("OrderAggregate");
        assertThat(entry.getVersion()).isEqualTo(3);
        assertThat(entry.getEventType()).isEqualTo("OrderShipped");
        assertThat(entry.getPayload()).isEqualTo("{\"x\":1}");
        assertThat(entry.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(entry.getStoredAt()).isEqualTo(storedAt);
    }

    @Test
    @DisplayName("no-args constructor with setters populates all fields")
    void noArgsConstructorWithSetters() {
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        Instant storedAt = Instant.now();

        EventEntry entry = new EventEntry();
        entry.setId(id);
        entry.setAggregateId("order-2");
        entry.setAggregateType("CartAggregate");
        entry.setVersion(7);
        entry.setEventType("ItemAdded");
        entry.setPayload("{}");
        entry.setOccurredAt(occurredAt);
        entry.setStoredAt(storedAt);

        assertThat(entry.getId()).isEqualTo(id);
        assertThat(entry.getAggregateId()).isEqualTo("order-2");
        assertThat(entry.getAggregateType()).isEqualTo("CartAggregate");
        assertThat(entry.getVersion()).isEqualTo(7);
        assertThat(entry.getEventType()).isEqualTo("ItemAdded");
        assertThat(entry.getPayload()).isEqualTo("{}");
        assertThat(entry.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(entry.getStoredAt()).isEqualTo(storedAt);
    }
}
