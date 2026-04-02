package com.adhar.kit.persistence.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantIdentifierResolver Tests")
class TenantIdentifierResolverTest {

    private TenantIdentifierResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantIdentifierResolver();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should resolve default tenant when not set")
    void testResolveDefaultTenant() {
        String tenantId = resolver.resolveCurrentTenantIdentifier();
        assertEquals("default", tenantId);
    }

    @Test
    @DisplayName("Should resolve current tenant from TenantContext")
    void testResolveCurrentTenant() {
        TenantContext.setTenant("tenant123");

        String tenantId = resolver.resolveCurrentTenantIdentifier();

        assertEquals("tenant123", tenantId);
    }

    @Test
    @DisplayName("Should return false for isRoot")
    void testIsRoot() {
        assertFalse(resolver.isRoot("tenant1"));
        assertFalse(resolver.isRoot("default"));
        assertFalse(resolver.isRoot(null));
        assertFalse(resolver.isRoot(""));
    }

    @Test
    @DisplayName("Should return true for validateExistingCurrentSessions")
    void testValidateExistingCurrentSessions() {
        assertTrue(resolver.validateExistingCurrentSessions());
    }

    @Test
    @DisplayName("Should resolve different tenants correctly")
    void testResolveDifferentTenants() {
        TenantContext.setTenant("tenantA");
        assertEquals("tenantA", resolver.resolveCurrentTenantIdentifier());

        TenantContext.setTenant("tenantB");
        assertEquals("tenantB", resolver.resolveCurrentTenantIdentifier());

        TenantContext.clear();
        assertEquals("default", resolver.resolveCurrentTenantIdentifier());
    }
}
