package com.adhar.kit.persistence.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantContext Tests")
class TenantContextTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should return default tenant when not set")
    void testGetTenantDefault() {
        assertEquals("default", TenantContext.getTenant());
    }

    @Test
    @DisplayName("Should set and get tenant")
    void testSetAndGetTenant() {
        TenantContext.setTenant("tenant1");
        assertEquals("tenant1", TenantContext.getTenant());

        TenantContext.setTenant("tenant2");
        assertEquals("tenant2", TenantContext.getTenant());
    }

    @Test
    @DisplayName("Should clear tenant")
    void testClear() {
        TenantContext.setTenant("tenant1");
        assertEquals("tenant1", TenantContext.getTenant());

        TenantContext.clear();
        assertEquals("default", TenantContext.getTenant());
    }

    @Test
    @DisplayName("Should return false for hasTenant when not set")
    void testHasTenantWhenNotSet() {
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should return true for hasTenant when set")
    void testHasTenantWhenSet() {
        TenantContext.setTenant("tenant1");
        assertTrue(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should return false for hasTenant after clear")
    void testHasTenantAfterClear() {
        TenantContext.setTenant("tenant1");
        assertTrue(TenantContext.hasTenant());

        TenantContext.clear();
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should handle null tenant")
    void testSetNullTenant() {
        TenantContext.setTenant(null);
        assertEquals("default", TenantContext.getTenant());
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should handle empty string tenant")
    void testSetEmptyTenant() {
        TenantContext.setTenant("");
        assertEquals("", TenantContext.getTenant());
        assertTrue(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should be thread-safe")
    void testThreadSafety() throws InterruptedException {
        TenantContext.setTenant("mainThread");

        Thread thread1 = new Thread(() -> {
            TenantContext.setTenant("thread1Tenant");
            assertEquals("thread1Tenant", TenantContext.getTenant());
        });

        Thread thread2 = new Thread(() -> {
            TenantContext.setTenant("thread2Tenant");
            assertEquals("thread2Tenant", TenantContext.getTenant());
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // Main thread tenant should be unchanged
        assertEquals("mainThread", TenantContext.getTenant());
    }
}
