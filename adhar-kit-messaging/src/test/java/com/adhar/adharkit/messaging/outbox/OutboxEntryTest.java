package com.adhar.adharkit.messaging.outbox;

import com.adhar.kit.messaging.outbox.OutboxEntry;
import com.adhar.kit.messaging.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link OutboxEntry} value object.
 */
class OutboxEntryTest {

    @Test
    void pendingFactoryPopulatesDefaults() {
        OutboxEntry entry = OutboxEntry.pending("orders", "key-1", "{\"a\":1}", "com.example.Event");

        assertNotNull(entry.getId());
        assertNotNull(entry.getCreatedAt());
        assertEquals("orders", entry.getDestination());
        assertEquals("key-1", entry.getRoutingKey());
        assertEquals("{\"a\":1}", entry.getPayload());
        assertEquals("com.example.Event", entry.getPayloadType());
        assertEquals(OutboxStatus.PENDING, entry.getStatus());
        assertEquals(0, entry.getAttempts());
        assertNull(entry.getLastAttemptAt());
        assertNull(entry.getLastError());
    }

    @Test
    void settersAndToString() {
        OutboxEntry entry = new OutboxEntry();
        Instant now = Instant.now();
        entry.setId("id-1");
        entry.setDestination("orders");
        entry.setRoutingKey("key");
        entry.setPayload("body");
        entry.setPayloadType("type");
        entry.setStatus(OutboxStatus.DEAD);
        entry.setAttempts(5);
        entry.setCreatedAt(now);
        entry.setLastAttemptAt(now);
        entry.setLastError("boom");

        assertEquals("id-1", entry.getId());
        assertEquals("orders", entry.getDestination());
        assertEquals("key", entry.getRoutingKey());
        assertEquals("body", entry.getPayload());
        assertEquals("type", entry.getPayloadType());
        assertEquals(OutboxStatus.DEAD, entry.getStatus());
        assertEquals(5, entry.getAttempts());
        assertEquals(now, entry.getCreatedAt());
        assertEquals(now, entry.getLastAttemptAt());
        assertEquals("boom", entry.getLastError());
        assertTrue(entry.toString().contains("id-1"));
    }
}
