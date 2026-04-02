package com.adhar.kit.persistence.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SoftDeletableEntity Tests")
class SoftDeletableEntityTest {

    // Concrete implementation for testing
    static class TestSoftDeletableEntity extends SoftDeletableEntity {
    }

    private TestSoftDeletableEntity entity;

    @BeforeEach
    void setUp() {
        entity = new TestSoftDeletableEntity();
    }

    @Test
    @DisplayName("Should have deleted=false by default")
    void testDefaultDeleted() {
        assertFalse(entity.getDeleted());
        assertFalse(entity.isDeleted());
    }

    @Test
    @DisplayName("Should set and get deleted flag")
    void testSetDeleted() {
        entity.setDeleted(true);
        assertTrue(entity.getDeleted());
        assertTrue(entity.isDeleted());

        entity.setDeleted(false);
        assertFalse(entity.getDeleted());
        assertFalse(entity.isDeleted());
    }

    @Test
    @DisplayName("Should set and get deletedAt")
    void testDeletedAt() {
        LocalDateTime now = LocalDateTime.now();
        entity.setDeletedAt(now);
        assertEquals(now, entity.getDeletedAt());
    }

    @Test
    @DisplayName("Should set and get deletedBy")
    void testDeletedBy() {
        entity.setDeletedBy("adminUser");
        assertEquals("adminUser", entity.getDeletedBy());
    }

    @Test
    @DisplayName("Should mark entity as deleted without user")
    void testMarkDeleted() {
        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());

        entity.markDeleted();

        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }

    @Test
    @DisplayName("Should mark entity as deleted with user")
    void testMarkDeletedWithUser() {
        assertFalse(entity.isDeleted());

        entity.markDeleted("deleteUser");

        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());
        assertEquals("deleteUser", entity.getDeletedBy());
    }

    @Test
    @DisplayName("Should restore soft-deleted entity")
    void testRestore() {
        // First mark as deleted
        entity.markDeleted("someUser");
        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());
        assertEquals("someUser", entity.getDeletedBy());

        // Then restore
        entity.restore();

        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }

    @Test
    @DisplayName("Should handle isDeleted with null deleted flag")
    void testIsDeletedWithNullFlag() {
        entity.setDeleted(null);
        assertFalse(entity.isDeleted());
    }

    @Test
    @DisplayName("Should inherit from AuditableEntity")
    void testInheritance() {
        // Test AuditableEntity fields
        entity.setCreatedBy("creator");
        entity.setUpdatedBy("updater");
        entity.setVersion(1L);

        assertEquals("creator", entity.getCreatedBy());
        assertEquals("updater", entity.getUpdatedBy());
        assertEquals(1L, entity.getVersion());

        // Test BaseEntity fields
        entity.setId(50L);
        assertEquals(50L, entity.getId());
    }

    @Test
    @DisplayName("Should call onCreate from AuditableEntity")
    void testOnCreateInheritance() {
        entity.onCreate();
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should call onUpdate from AuditableEntity")
    void testOnUpdateInheritance() {
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setUpdatedAt(original);

        entity.onUpdate();

        assertNotEquals(original, entity.getUpdatedAt());
    }
}
