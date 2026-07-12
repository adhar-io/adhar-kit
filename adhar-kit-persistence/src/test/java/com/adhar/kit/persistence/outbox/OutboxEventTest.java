package com.adhar.kit.persistence.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("OutboxEvent Tests")
class OutboxEventTest {

    @Test
    @DisplayName("create() builds a PENDING event with provided fields and a createdAt timestamp")
    void testCreate() {
        Instant before = Instant.now();
        OutboxEvent event = OutboxEvent.create("Order", "42", "OrderCreated", "{\"k\":1}");

        assertEquals("Order", event.getAggregateType());
        assertEquals("42", event.getAggregateId());
        assertEquals("OrderCreated", event.getEventType());
        assertEquals("{\"k\":1}", event.getPayload());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, event.getStatus());
        assertNotNull(event.getCreatedAt());
        assertNull(event.getProcessedAt());
        assertNull(event.getId());
        // createdAt should be set at or after the test start
        org.junit.jupiter.api.Assertions.assertFalse(event.getCreatedAt().isBefore(before));
    }

    @Test
    @DisplayName("builder default status is PENDING")
    void testBuilderDefaultStatus() {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("A")
                .aggregateId("1")
                .eventType("E")
                .createdAt(Instant.now())
                .build();
        assertEquals(OutboxEvent.OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    @DisplayName("all-args constructor and setters work")
    void testAllArgsAndSetters() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.now();
        Instant processed = created.plusSeconds(5);
        OutboxEvent event = new OutboxEvent(id, "Agg", "7", "Type", "payload",
                created, processed, OutboxEvent.OutboxStatus.PROCESSED);

        assertEquals(id, event.getId());
        assertEquals("Agg", event.getAggregateType());
        assertEquals(processed, event.getProcessedAt());
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, event.getStatus());

        event.setStatus(OutboxEvent.OutboxStatus.FAILED);
        event.setPayload("changed");
        event.setAggregateId("8");
        event.setEventType("Type2");
        assertEquals(OutboxEvent.OutboxStatus.FAILED, event.getStatus());
        assertEquals("changed", event.getPayload());
        assertEquals("8", event.getAggregateId());
        assertEquals("Type2", event.getEventType());
    }

    @Test
    @DisplayName("no-args constructor produces an empty event")
    void testNoArgs() {
        OutboxEvent event = new OutboxEvent();
        assertNull(event.getId());
        assertNull(event.getAggregateType());
    }

    @Test
    @DisplayName("OutboxStatus enum exposes all values")
    void testStatusEnum() {
        assertEquals(3, OutboxEvent.OutboxStatus.values().length);
        assertEquals(OutboxEvent.OutboxStatus.PENDING, OutboxEvent.OutboxStatus.valueOf("PENDING"));
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, OutboxEvent.OutboxStatus.valueOf("PROCESSED"));
        assertEquals(OutboxEvent.OutboxStatus.FAILED, OutboxEvent.OutboxStatus.valueOf("FAILED"));
    }
}
