package com.adhar.kit.health.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Health model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class HealthTest {

    @Test
    void testHealthUp() {
        Health health = Health.up()
            .component("test")
            .withDetail("key", "value")
            .build();

        assertEquals(Health.Status.UP, health.getStatus());
        assertEquals("test", health.getComponent());
        assertEquals("value", health.getDetails().get("key"));
        assertTrue(health.isUp());
        assertFalse(health.isDown());
    }

    @Test
    void testHealthDown() {
        Health health = Health.down()
            .component("test")
            .withDetail("error", "connection failed")
            .build();

        assertEquals(Health.Status.DOWN, health.getStatus());
        assertTrue(health.isDown());
        assertFalse(health.isUp());
    }

    @Test
    void testHealthDownWithException() {
        Exception exception = new RuntimeException("Database error");

        Health health = Health.down(exception)
            .component("database")
            .build();

        assertEquals(Health.Status.DOWN, health.getStatus());
        assertEquals("Database error", health.getError());
        assertTrue(health.isDown());
    }

    @Test
    void testHealthUnknown() {
        Health health = Health.unknown()
            .component("test")
            .build();

        assertEquals(Health.Status.UNKNOWN, health.getStatus());
        assertFalse(health.isUp());
        assertFalse(health.isDown());
    }

    @Test
    void testHealthOutOfService() {
        Health health = Health.outOfService()
            .component("test")
            .build();

        assertEquals(Health.Status.OUT_OF_SERVICE, health.getStatus());
        assertTrue(health.isDown());
    }

    @Test
    void testWithMultipleDetails() {
        Health health = Health.up()
            .component("test")
            .withDetail("key1", "value1")
            .withDetail("key2", "value2")
            .withDetail("key3", 123)
            .build();

        assertEquals(3, health.getDetails().size());
        assertEquals("value1", health.getDetails().get("key1"));
        assertEquals("value2", health.getDetails().get("key2"));
        assertEquals(123, health.getDetails().get("key3"));
    }

    @Test
    void testTimestamp() {
        Health health = Health.up().build();

        assertNotNull(health.getTimestamp());
    }
}

