package com.adhar.kit.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BaseEntity Tests")
class BaseEntityTest {

    // Concrete implementation for testing
    static class TestEntity extends BaseEntity {
    }

    @Test
    @DisplayName("Should set and get id")
    void testSetAndGetId() {
        TestEntity entity = new TestEntity();
        assertNull(entity.getId());

        entity.setId(1L);
        assertEquals(1L, entity.getId());

        entity.setId(100L);
        assertEquals(100L, entity.getId());
    }

    @Test
    @DisplayName("Should return true when comparing same instance")
    void testEqualsSameInstance() {
        TestEntity entity = new TestEntity();
        entity.setId(1L);

        assertTrue(entity.equals(entity));
    }

    @Test
    @DisplayName("Should return true when comparing entities with same id")
    void testEqualsSameId() {
        TestEntity entity1 = new TestEntity();
        entity1.setId(1L);

        TestEntity entity2 = new TestEntity();
        entity2.setId(1L);

        assertTrue(entity1.equals(entity2));
        assertTrue(entity2.equals(entity1));
    }

    @Test
    @DisplayName("Should return false when comparing entities with different ids")
    void testEqualsDifferentId() {
        TestEntity entity1 = new TestEntity();
        entity1.setId(1L);

        TestEntity entity2 = new TestEntity();
        entity2.setId(2L);

        assertFalse(entity1.equals(entity2));
        assertFalse(entity2.equals(entity1));
    }

    @Test
    @DisplayName("Should return false when comparing with null")
    void testEqualsNull() {
        TestEntity entity = new TestEntity();
        entity.setId(1L);

        assertFalse(entity.equals(null));
    }

    @Test
    @DisplayName("Should return false when comparing with different type")
    void testEqualsDifferentType() {
        TestEntity entity = new TestEntity();
        entity.setId(1L);

        assertFalse(entity.equals("not an entity"));
        assertFalse(entity.equals(1L));
    }

    @Test
    @DisplayName("Should return false when id is null")
    void testEqualsNullId() {
        TestEntity entity1 = new TestEntity();
        TestEntity entity2 = new TestEntity();

        assertFalse(entity1.equals(entity2));
    }

    @Test
    @DisplayName("Should return false when one id is null")
    void testEqualsOneNullId() {
        TestEntity entity1 = new TestEntity();
        entity1.setId(1L);

        TestEntity entity2 = new TestEntity();

        assertFalse(entity1.equals(entity2));
    }

    @Test
    @DisplayName("Should return consistent hashCode")
    void testHashCode() {
        TestEntity entity1 = new TestEntity();
        entity1.setId(1L);

        TestEntity entity2 = new TestEntity();
        entity2.setId(1L);

        // hashCode should be consistent
        assertEquals(entity1.hashCode(), entity1.hashCode());

        // Same class should have same hashCode (based on getClass())
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    @DisplayName("Should return class hashCode regardless of id")
    void testHashCodeWithDifferentIds() {
        TestEntity entity1 = new TestEntity();
        entity1.setId(1L);

        TestEntity entity2 = new TestEntity();
        entity2.setId(2L);

        // hashCode is based on class, not id
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    @DisplayName("Should return hashCode even with null id")
    void testHashCodeNullId() {
        TestEntity entity = new TestEntity();
        assertNotNull(entity.hashCode());
    }
}
