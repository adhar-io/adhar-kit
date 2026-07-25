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
    @DisplayName("create() builds a PENDING event with provided fields, a createdAt timestamp, and is immediately due")
    void testCreate() {
        Instant before = Instant.now();
        OutboxEvent event = OutboxEvent.create("Order", "42", "OrderCreated", "{\"k\":1}");

        assertEquals("Order", event.getAggregateType());
        assertEquals("42", event.getAggregateId());
        assertEquals("OrderCreated", event.getEventType());
        assertEquals("{\"k\":1}", event.getPayload());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertNull(event.getLastError());
        assertNotNull(event.getCreatedAt());
        assertNotNull(event.getNextAttemptAt());
        assertNull(event.getProcessedAt());
        assertNull(event.getId());
        // createdAt/nextAttemptAt should be set at or after the test start -- immediately due
        org.junit.jupiter.api.Assertions.assertFalse(event.getCreatedAt().isBefore(before));
        org.junit.jupiter.api.Assertions.assertFalse(event.getNextAttemptAt().isBefore(before));
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
        assertEquals(0, event.getAttempts());
    }

    @Test
    @DisplayName("all-args constructor and setters work, including retry fields")
    void testAllArgsAndSetters() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.now();
        Instant processed = created.plusSeconds(5);
        Instant nextAttempt = created.plusSeconds(10);
        OutboxEvent event = new OutboxEvent(id, "Agg", "7", "Type", "payload",
                created, processed, OutboxEvent.OutboxStatus.PROCESSED, 2, nextAttempt, "boom");

        assertEquals(id, event.getId());
        assertEquals("Agg", event.getAggregateType());
        assertEquals(processed, event.getProcessedAt());
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, event.getStatus());
        assertEquals(2, event.getAttempts());
        assertEquals(nextAttempt, event.getNextAttemptAt());
        assertEquals("boom", event.getLastError());

        event.setStatus(OutboxEvent.OutboxStatus.FAILED);
        event.setPayload("changed");
        event.setAggregateId("8");
        event.setEventType("Type2");
        event.setAttempts(3);
        event.setLastError("still broken");
        assertEquals(OutboxEvent.OutboxStatus.FAILED, event.getStatus());
        assertEquals("changed", event.getPayload());
        assertEquals("8", event.getAggregateId());
        assertEquals("Type2", event.getEventType());
        assertEquals(3, event.getAttempts());
        assertEquals("still broken", event.getLastError());
    }

    @Test
    @DisplayName("no-args constructor produces an empty event")
    void testNoArgs() {
        OutboxEvent event = new OutboxEvent();
        assertNull(event.getId());
        assertNull(event.getAggregateType());
        assertEquals(0, event.getAttempts());
    }

    @Test
    @DisplayName("OutboxStatus enum exposes all values including DEAD")
    void testStatusEnum() {
        assertEquals(4, OutboxEvent.OutboxStatus.values().length);

        assertNotNull(OutboxEvent.OutboxStatus.PENDING);
        assertNotNull(OutboxEvent.OutboxStatus.PROCESSED);
        assertNotNull(OutboxEvent.OutboxStatus.FAILED);
        assertNotNull(OutboxEvent.OutboxStatus.DEAD);

        assertEquals("PENDING", OutboxEvent.OutboxStatus.PENDING.name());
        assertEquals("PROCESSED", OutboxEvent.OutboxStatus.PROCESSED.name());
        assertEquals("FAILED", OutboxEvent.OutboxStatus.FAILED.name());
        assertEquals("DEAD", OutboxEvent.OutboxStatus.DEAD.name());
        assertEquals(OutboxEvent.OutboxStatus.DEAD, OutboxEvent.OutboxStatus.valueOf("DEAD"));
    }
}
