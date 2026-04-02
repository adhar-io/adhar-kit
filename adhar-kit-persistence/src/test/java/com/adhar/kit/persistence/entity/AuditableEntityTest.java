package com.adhar.kit.persistence.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditableEntity Tests")
class AuditableEntityTest {

    // Concrete implementation for testing
    static class TestAuditableEntity extends AuditableEntity {
    }

    private TestAuditableEntity entity;

    @BeforeEach
    void setUp() {
        entity = new TestAuditableEntity();
    }

    @Test
    @DisplayName("Should set and get createdAt")
    void testCreatedAt() {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    @DisplayName("Should set and get createdBy")
    void testCreatedBy() {
        entity.setCreatedBy("testUser");
        assertEquals("testUser", entity.getCreatedBy());
    }

    @Test
    @DisplayName("Should set and get updatedAt")
    void testUpdatedAt() {
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should set and get updatedBy")
    void testUpdatedBy() {
        entity.setUpdatedBy("modifyUser");
        assertEquals("modifyUser", entity.getUpdatedBy());
    }

    @Test
    @DisplayName("Should set and get version")
    void testVersion() {
        entity.setVersion(1L);
        assertEquals(1L, entity.getVersion());

        entity.setVersion(5L);
        assertEquals(5L, entity.getVersion());
    }

    @Test
    @DisplayName("Should set createdAt and updatedAt on onCreate when null")
    void testOnCreateWhenNull() {
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());

        entity.onCreate();

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should not override createdAt on onCreate when already set")
    void testOnCreatePreservesCreatedAt() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setCreatedAt(originalCreatedAt);

        entity.onCreate();

        assertEquals(originalCreatedAt, entity.getCreatedAt());
    }

    @Test
    @DisplayName("Should not override updatedAt on onCreate when already set")
    void testOnCreatePreservesUpdatedAt() {
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setUpdatedAt(originalUpdatedAt);

        entity.onCreate();

        assertEquals(originalUpdatedAt, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should update updatedAt on onUpdate")
    void testOnUpdate() {
        LocalDateTime originalTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setUpdatedAt(originalTime);

        entity.onUpdate();

        assertNotEquals(originalTime, entity.getUpdatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should inherit id from BaseEntity")
    void testInheritance() {
        entity.setId(100L);
        assertEquals(100L, entity.getId());
    }

    @Test
    @DisplayName("Should handle null values")
    void testNullValues() {
        entity.setCreatedAt(null);
        entity.setCreatedBy(null);
        entity.setUpdatedAt(null);
        entity.setUpdatedBy(null);
        entity.setVersion(null);

        assertNull(entity.getCreatedAt());
        assertNull(entity.getCreatedBy());
        assertNull(entity.getUpdatedAt());
        assertNull(entity.getUpdatedBy());
        assertNull(entity.getVersion());
    }
}
