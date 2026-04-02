package com.adhar.kit.persistence.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Annotation Enums Tests")
class AnnotationEnumsTest {

    @Test
    @DisplayName("Should have all MultiTenancyStrategy values")
    void testMultiTenancyStrategyValues() {
        MultiTenant.MultiTenancyStrategy[] values = MultiTenant.MultiTenancyStrategy.values();

        assertEquals(3, values.length);
        assertNotNull(MultiTenant.MultiTenancyStrategy.SCHEMA);
        assertNotNull(MultiTenant.MultiTenancyStrategy.DATABASE);
        assertNotNull(MultiTenant.MultiTenancyStrategy.DISCRIMINATOR);
    }

    @Test
    @DisplayName("Should convert MultiTenancyStrategy from string")
    void testMultiTenancyStrategyValueOf() {
        assertEquals(MultiTenant.MultiTenancyStrategy.SCHEMA,
            MultiTenant.MultiTenancyStrategy.valueOf("SCHEMA"));
        assertEquals(MultiTenant.MultiTenancyStrategy.DATABASE,
            MultiTenant.MultiTenancyStrategy.valueOf("DATABASE"));
        assertEquals(MultiTenant.MultiTenancyStrategy.DISCRIMINATOR,
            MultiTenant.MultiTenancyStrategy.valueOf("DISCRIMINATOR"));
    }

    @Test
    @DisplayName("Should have all CacheStrategy values")
    void testCacheStrategyValues() {
        Cacheable.CacheStrategy[] values = Cacheable.CacheStrategy.values();

        assertEquals(4, values.length);
        assertNotNull(Cacheable.CacheStrategy.READ_ONLY);
        assertNotNull(Cacheable.CacheStrategy.READ_WRITE);
        assertNotNull(Cacheable.CacheStrategy.NONSTRICT_READ_WRITE);
        assertNotNull(Cacheable.CacheStrategy.TRANSACTIONAL);
    }

    @Test
    @DisplayName("Should convert CacheStrategy from string")
    void testCacheStrategyValueOf() {
        assertEquals(Cacheable.CacheStrategy.READ_ONLY,
            Cacheable.CacheStrategy.valueOf("READ_ONLY"));
        assertEquals(Cacheable.CacheStrategy.READ_WRITE,
            Cacheable.CacheStrategy.valueOf("READ_WRITE"));
        assertEquals(Cacheable.CacheStrategy.NONSTRICT_READ_WRITE,
            Cacheable.CacheStrategy.valueOf("NONSTRICT_READ_WRITE"));
        assertEquals(Cacheable.CacheStrategy.TRANSACTIONAL,
            Cacheable.CacheStrategy.valueOf("TRANSACTIONAL"));
    }

    @Test
    @DisplayName("Should return correct name for MultiTenancyStrategy")
    void testMultiTenancyStrategyName() {
        assertEquals("SCHEMA", MultiTenant.MultiTenancyStrategy.SCHEMA.name());
        assertEquals("DATABASE", MultiTenant.MultiTenancyStrategy.DATABASE.name());
        assertEquals("DISCRIMINATOR", MultiTenant.MultiTenancyStrategy.DISCRIMINATOR.name());
    }

    @Test
    @DisplayName("Should return correct name for CacheStrategy")
    void testCacheStrategyName() {
        assertEquals("READ_ONLY", Cacheable.CacheStrategy.READ_ONLY.name());
        assertEquals("READ_WRITE", Cacheable.CacheStrategy.READ_WRITE.name());
        assertEquals("NONSTRICT_READ_WRITE", Cacheable.CacheStrategy.NONSTRICT_READ_WRITE.name());
        assertEquals("TRANSACTIONAL", Cacheable.CacheStrategy.TRANSACTIONAL.name());
    }

    @Test
    @DisplayName("Should have correct ordinal for MultiTenancyStrategy")
    void testMultiTenancyStrategyOrdinal() {
        assertEquals(0, MultiTenant.MultiTenancyStrategy.SCHEMA.ordinal());
        assertEquals(1, MultiTenant.MultiTenancyStrategy.DATABASE.ordinal());
        assertEquals(2, MultiTenant.MultiTenancyStrategy.DISCRIMINATOR.ordinal());
    }

    @Test
    @DisplayName("Should have correct ordinal for CacheStrategy")
    void testCacheStrategyOrdinal() {
        assertEquals(0, Cacheable.CacheStrategy.READ_ONLY.ordinal());
        assertEquals(1, Cacheable.CacheStrategy.READ_WRITE.ordinal());
        assertEquals(2, Cacheable.CacheStrategy.NONSTRICT_READ_WRITE.ordinal());
        assertEquals(3, Cacheable.CacheStrategy.TRANSACTIONAL.ordinal());
    }
}
