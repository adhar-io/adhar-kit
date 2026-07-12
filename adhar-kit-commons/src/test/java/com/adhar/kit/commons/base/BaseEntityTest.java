package com.adhar.kit.commons.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    static class User extends BaseEntity<String> {
        private String userId;
        User(String id) { this.userId = id; }
        @Override
        public String getId() { return userId; }
    }

    @Test
    void isNewTrueWhenIdNull() {
        assertTrue(new User(null).isNew());
    }

    @Test
    void isNewTrueWhenCreatedAtNull() {
        User u = new User("u1");
        assertNull(u.getCreatedAt());
        assertTrue(u.isNew());
    }

    @Test
    void isNewFalseWhenIdAndCreatedAtPresent() {
        User u = new User("u1");
        u.prePersist("admin");
        assertFalse(u.isNew());
    }

    @Test
    void prePersistSetsAllAuditFields() {
        User u = new User("u1");
        u.prePersist("creator");
        assertEquals("creator", u.getCreatedBy());
        assertEquals("creator", u.getUpdatedBy());
        assertNotNull(u.getCreatedAt());
        assertNotNull(u.getUpdatedAt());
    }

    @Test
    void preUpdateSetsOnlyUpdateFields() {
        User u = new User("u1");
        u.preUpdate("editor");
        assertEquals("editor", u.getUpdatedBy());
        assertNotNull(u.getUpdatedAt());
        assertNull(u.getCreatedBy());
        assertNull(u.getCreatedAt());
    }

    @Test
    void versionAccessor() {
        User u = new User("u1");
        u.setVersion(5L);
        assertEquals(5L, u.getVersion());
    }
}
