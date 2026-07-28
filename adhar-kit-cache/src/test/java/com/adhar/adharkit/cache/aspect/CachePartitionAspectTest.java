package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CachePartition;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.partition.KeyPartitionResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CachePartitionAspect} covering tenant resolution from a
 * method parameter and the resolver fallback, per-tenant cache creation and the
 * get-or-compute semantics.
 */
@DisplayName("CachePartitionAspect Tests")
class CachePartitionAspectTest {

    private CacheManager cacheManager;
    private CacheKeyGenerator keyGenerator;
    private Sample sample;

    static class Sample {
        final AtomicInteger calls = new AtomicInteger();

        @CachePartition(cacheName = "orders", tenantParam = "tenantId", key = "#orderId", ttl = 10)
        public String findOrder(String tenantId, String orderId) {
            calls.incrementAndGet();
            return "order-" + tenantId + "-" + orderId;
        }

        @CachePartition(cacheName = "noparam", tenantParam = "missing", key = "#id", ttl = 0)
        public String findByResolver(String id) {
            calls.incrementAndGet();
            return "np-" + id;
        }

        @CachePartition(cacheName = "ttl0", tenantParam = "tenantId", key = "#id", ttl = 0)
        public String findTtlZero(String tenantId, String id) {
            calls.incrementAndGet();
            return "z-" + tenantId + "-" + id;
        }

        @CachePartition(cacheName = "nullres", tenantParam = "tenantId", key = "#id", ttl = 5)
        public String findNull(String tenantId, String id) {
            calls.incrementAndGet();
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        for (String name : new String[]{"orders", "orders::acme", "orders#acme", "orders::fb",
            "noparam", "noparam::fb", "ttl0", "ttl0::acme", "nullres", "nullres::acme"}) {
            cacheManager.removeCache(name);
        }
        keyGenerator = new CacheKeyGenerator();
        sample = new Sample();
    }

    private CachePartitionAspect aspect(KeyPartitionResolver resolver) {
        return new CachePartitionAspect(cacheManager, keyGenerator, resolver);
    }

    private Object invoke(CachePartitionAspect aspect, String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(sample, methodName, args);
        return aspect.aroundCachePartition(joinPoint,
            joinPoint.method().getAnnotation(CachePartition.class));
    }

    @Test
    @DisplayName("tenant resolved from a parameter partitions the cache and caches the result")
    void tenantFromParameter() throws Throwable {
        CachePartitionAspect aspect = aspect(null);
        assertEquals("order-acme-o1", invoke(aspect, "findOrder", "acme", "o1"));
        assertEquals(1, sample.calls.get());

        // second call hits the tenant cache, no re-compute
        assertEquals("order-acme-o1", invoke(aspect, "findOrder", "acme", "o1"));
        assertEquals(1, sample.calls.get());

        assertNotNull(cacheManager.getCache("orders::acme"));
        assertEquals("order-acme-o1", cacheManager.getCache("orders::acme").get("o1"));
    }

    @Test
    @DisplayName("different tenants use different physical caches")
    void differentTenantsIsolated() throws Throwable {
        CachePartitionAspect aspect = aspect(null);
        invoke(aspect, "findOrder", "acme", "o1");
        invoke(aspect, "findOrder", "beta", "o1");
        assertEquals(2, sample.calls.get());
        assertNotNull(cacheManager.getCache("orders::acme"));
        assertNotNull(cacheManager.getCache("orders::beta"));
        cacheManager.removeCache("orders::beta");
    }

    @Test
    @DisplayName("tenant falls back to the KeyPartitionResolver when no parameter matches")
    void tenantFromResolverFallback() throws Throwable {
        CachePartitionAspect aspect = aspect(() -> "fb");
        assertEquals("np-x", invoke(aspect, "findByResolver", "x"));
        assertNotNull(cacheManager.getCache("noparam::fb"));
        assertEquals("np-x", cacheManager.getCache("noparam::fb").get("x"));
    }

    @Test
    @DisplayName("a null parameter value falls back to the resolver")
    void nullParameterFallsBackToResolver() throws Throwable {
        CachePartitionAspect aspect = aspect(() -> "fb");
        assertEquals("order-null-o1", invoke(aspect, "findOrder", null, "o1"));
        assertNotNull(cacheManager.getCache("orders::fb"));
    }

    @Test
    @DisplayName("no resolvable tenant uses the unpartitioned base cache")
    void noTenantUsesBaseCache() throws Throwable {
        CachePartitionAspect aspect = aspect(null); // no resolver, no matching param
        assertEquals("np-y", invoke(aspect, "findByResolver", "y"));
        assertNotNull(cacheManager.getCache("noparam"));
        assertEquals("np-y", cacheManager.getCache("noparam").get("y"));
    }

    @Test
    @DisplayName("a blank resolver result is treated as no tenant")
    void blankResolverResultIsNoTenant() throws Throwable {
        CachePartitionAspect aspect = aspect(() -> "   ");
        invoke(aspect, "findByResolver", "z");
        assertNotNull(cacheManager.getCache("noparam"));
    }

    @Test
    @DisplayName("ttl=0 uses the default get-or-create cache")
    void ttlZeroUsesGetOrCreate() throws Throwable {
        CachePartitionAspect aspect = aspect(null);
        assertEquals("z-acme-i1", invoke(aspect, "findTtlZero", "acme", "i1"));
        assertNotNull(cacheManager.getCache("ttl0::acme"));
    }

    @Test
    @DisplayName("a null result is not cached and re-computes on the next call")
    void nullResultNotCached() throws Throwable {
        CachePartitionAspect aspect = aspect(null);
        assertNull(invoke(aspect, "findNull", "acme", "i1"));
        assertNull(invoke(aspect, "findNull", "acme", "i1"));
        assertEquals(2, sample.calls.get());
    }

    @Test
    @DisplayName("a custom separator is used between cache name and tenant")
    void customSeparator() throws Throwable {
        CachePartitionAspect aspect = new CachePartitionAspect(
            cacheManager, keyGenerator, null, "#");
        invoke(aspect, "findOrder", "acme", "o1");
        assertNotNull(cacheManager.getCache("orders#acme"));
    }

    @Test
    @DisplayName("a blank separator falls back to the default '::'")
    void blankSeparatorFallsBackToDefault() throws Throwable {
        CachePartitionAspect aspect = new CachePartitionAspect(
            cacheManager, keyGenerator, null, "  ");
        invoke(aspect, "findOrder", "acme", "o1");
        assertNotNull(cacheManager.getCache("orders::acme"));
    }
}
