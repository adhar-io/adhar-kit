package com.adhar.kit.persistence.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CurrentTenantIdentifierResolverImpl Tests")
class CurrentTenantIdentifierResolverImplTest {

    private CurrentTenantIdentifierResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentTenantIdentifierResolverImpl();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("is a TenantIdentifierResolver (Hibernate CurrentTenantIdentifierResolver)")
    void isATenantIdentifierResolver() {
        assertInstanceOf(TenantIdentifierResolver.class, resolver);
    }

    @Test
    @DisplayName("resolves the default tenant when none is set")
    void resolvesDefaultTenant() {
        assertEquals("default", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    @DisplayName("resolves the tenant currently set on TenantContext")
    void resolvesCurrentTenant() {
        TenantContext.setTenant("acme");
        assertEquals("acme", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    @DisplayName("never reports any tenant identifier as root")
    void neverReportsRoot() {
        assertFalse(resolver.isRoot("acme"));
        assertFalse(resolver.isRoot("default"));
    }

    @Test
    @DisplayName("validates existing current sessions")
    void validatesExistingSessions() {
        assertTrue(resolver.validateExistingCurrentSessions());
    }
}
