package com.adhar.adharkit.cache.partition;

import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the key-partitioning building blocks: {@link TenantContextHolder},
 * {@link ThreadLocalKeyPartitionResolver} and the partitioning behavior of
 * {@link CacheKeyGenerator}.
 */
@DisplayName("Key Partition Tests")
class KeyPartitionTest {

    @SuppressWarnings("unused")
    static String sample(String id) {
        return id;
    }

    private static Method sampleMethod() {
        try {
            return KeyPartitionTest.class.getDeclaredMethod("sample", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    // ==================== TenantContextHolder ====================

    @Test
    @DisplayName("TenantContextHolder stores, returns and clears the tenant")
    void tenantHolderLifecycle() {
        assertNull(TenantContextHolder.getTenant());
        TenantContextHolder.setTenant("acme");
        assertEquals("acme", TenantContextHolder.getTenant());
        TenantContextHolder.clear();
        assertNull(TenantContextHolder.getTenant());
    }

    @Test
    @DisplayName("TenantContextHolder.setTenant(null) removes the tenant")
    void tenantHolderSetNull() {
        TenantContextHolder.setTenant("acme");
        TenantContextHolder.setTenant(null);
        assertNull(TenantContextHolder.getTenant());
    }

    // ==================== ThreadLocalKeyPartitionResolver ====================

    @Test
    @DisplayName("ThreadLocalKeyPartitionResolver reflects the current tenant")
    void resolverReflectsTenant() {
        KeyPartitionResolver resolver = new ThreadLocalKeyPartitionResolver();
        assertNull(resolver.resolvePartition());
        TenantContextHolder.setTenant("beta");
        assertEquals("beta", resolver.resolvePartition());
    }

    // ==================== CacheKeyGenerator partitioning ====================

    @Test
    @DisplayName("partitioning disabled leaves the key unchanged")
    void partitioningDisabled() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(new ThreadLocalKeyPartitionResolver());
        TenantContextHolder.setTenant("acme");
        assertEquals("k1", generator.generate("'k1'", sampleMethod(), this, new Object[]{"x"}));
    }

    @Test
    @DisplayName("partitioning enabled prefixes the key with the resolved partition")
    void partitioningEnabledPrefixes() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(new ThreadLocalKeyPartitionResolver());
        generator.setPartitioningEnabled(true);
        TenantContextHolder.setTenant("acme");
        assertEquals("acme::k1", generator.generate("'k1'", sampleMethod(), this, new Object[]{"x"}));
    }

    @Test
    @DisplayName("a custom partition separator is honored")
    void customSeparator() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(new ThreadLocalKeyPartitionResolver());
        generator.setPartitioningEnabled(true);
        generator.setPartitionSeparator("#");
        TenantContextHolder.setTenant("acme");
        assertEquals("acme#k1", generator.generate("'k1'", sampleMethod(), this, new Object[]{"x"}));
    }

    @Test
    @DisplayName("a blank partition separator falls back to the default '::'")
    void blankSeparatorFallsBack() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(new ThreadLocalKeyPartitionResolver());
        generator.setPartitioningEnabled(true);
        generator.setPartitionSeparator("  ");
        TenantContextHolder.setTenant("acme");
        assertEquals("acme::k1", generator.generate("'k1'", sampleMethod(), this, new Object[]{"x"}));
    }

    @Test
    @DisplayName("partitioning enabled with no tenant leaves the key unchanged")
    void enabledButNoTenant() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(new ThreadLocalKeyPartitionResolver());
        generator.setPartitioningEnabled(true);
        // no tenant bound
        assertEquals("k1", generator.generate("'k1'", sampleMethod(), this, new Object[]{"x"}));
    }

    @Test
    @DisplayName("partitioning enabled with a null resolver leaves the key unchanged")
    void enabledButNullResolver() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitioningEnabled(true);
        generator.setPartitionResolver(null);
        TenantContextHolder.setTenant("acme");
        assertEquals("k1", generator.generate("'k1'", sampleMethod(), this, new Object[]{"x"}));
    }

    @Test
    @DisplayName("applyPartition prefixes an explicit key when enabled")
    void applyPartitionDirect() {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(new ThreadLocalKeyPartitionResolver());
        generator.setPartitioningEnabled(true);
        TenantContextHolder.setTenant("acme");
        assertEquals("acme::raw", generator.applyPartition("raw"));
        TenantContextHolder.setTenant("   ");
        assertEquals("raw", generator.applyPartition("raw"), "blank tenant leaves key unchanged");
    }
}
