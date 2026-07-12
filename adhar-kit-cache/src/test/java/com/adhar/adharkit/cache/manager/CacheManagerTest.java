package com.adhar.adharkit.cache.manager;

import com.adhar.adharkit.cache.CacheFacade;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheManager}.
 */
@DisplayName("CacheManager Tests")
class CacheManagerTest {

    private CacheManager manager;

    /** Generates a unique cache name to avoid singleton state collisions across tests. */
    private String uniqueName() {
        return "cm-" + UUID.randomUUID();
    }

    @BeforeEach
    void setUp() {
        manager = CacheManager.getInstance();
    }

    @Test
    @DisplayName("getInstance returns the same singleton")
    void testSingleton() {
        assertSame(manager, CacheManager.getInstance());
    }

    @Test
    @DisplayName("createCache returns a configurable builder and produces a working cache")
    void testCreateCache() {
        String name = uniqueName();
        CacheFacade.Builder builder = manager.createCache(name);

        assertNotNull(builder);
        CacheFacade cache = builder.build();
        assertEquals(name, cache.getCacheName());
    }

    @Test
    @DisplayName("createCache returns null if cache already registered")
    void testCreateCacheDuplicate() {
        String name = uniqueName();
        manager.registerCache(name, manager.createCache(name).build());

        assertNull(manager.createCache(name));
    }

    @Test
    @DisplayName("getOrCreateCache creates once and reuses thereafter")
    void testGetOrCreateCache() {
        String name = uniqueName();
        CacheFacade first = manager.getOrCreateCache(name);
        CacheFacade second = manager.getOrCreateCache(name);

        assertNotNull(first);
        assertSame(first, second);
        assertTrue(manager.hasCache(name));
        assertEquals(name, manager.getCache(name).getCacheName());
    }

    @Test
    @DisplayName("registerCache stores the cache and getCache returns it")
    void testRegisterAndGet() {
        String name = uniqueName();
        CacheFacade cache = CacheFacade.builder().cacheName(name).recordStats(true).build();

        manager.registerCache(name, cache);

        assertSame(cache, manager.getCache(name));
        assertTrue(manager.getCacheNames().contains(name));
        assertTrue(manager.getAllCaches().containsKey(name));
    }

    @Test
    @DisplayName("registerCache validates null arguments")
    void testRegisterNullArgs() {
        assertThrows(NullPointerException.class, () -> manager.registerCache(null, CacheFacade.builder().build()));
        assertThrows(NullPointerException.class, () -> manager.registerCache("x", null));
    }

    @Test
    @DisplayName("createCache validates null name")
    void testCreateNullName() {
        assertThrows(NullPointerException.class, () -> manager.createCache(null));
    }

    @Test
    @DisplayName("getCacheNames and getAllCaches are unmodifiable")
    void testUnmodifiableViews() {
        String name = uniqueName();
        manager.getOrCreateCache(name);

        Set<String> names = manager.getCacheNames();
        Map<String, CacheFacade> all = manager.getAllCaches();

        assertThrows(UnsupportedOperationException.class, () -> names.add("nope"));
        assertThrows(UnsupportedOperationException.class, () -> all.put("nope", null));
    }

    @Test
    @DisplayName("clearAll empties every cache without removing them")
    void testClearAll() {
        String name = uniqueName();
        CacheFacade cache = manager.getOrCreateCache(name);
        cache.put("k", "v");
        assertEquals(1, cache.size());

        manager.clearAll();

        assertEquals(0, cache.size());
        assertTrue(manager.hasCache(name));
    }

    @Test
    @DisplayName("removeCache removes existing cache and returns true; false otherwise")
    void testRemoveCache() {
        String name = uniqueName();
        CacheFacade cache = manager.getOrCreateCache(name);
        cache.put("k", "v");

        assertTrue(manager.removeCache(name));
        assertFalse(manager.hasCache(name));
        assertFalse(manager.removeCache(name));
        assertFalse(manager.removeCache("never-existed-" + UUID.randomUUID()));
    }

    @Test
    @DisplayName("cleanUpAll runs without error")
    void testCleanUpAll() {
        String name = uniqueName();
        manager.getOrCreateCache(name).put("k", "v");
        assertDoesNotThrow(() -> manager.cleanUpAll());
    }

    @Test
    @DisplayName("getAllStats returns stats for each managed cache")
    void testGetAllStats() {
        String name = uniqueName();
        CacheFacade cache = manager.getOrCreateCache(name);
        cache.put("k", "v");
        cache.get("k");

        Map<String, CacheStats> stats = manager.getAllStats();

        assertTrue(stats.containsKey(name));
        assertNotNull(stats.get(name));
    }

    @Test
    @DisplayName("getHealthMetrics aggregates cache metrics")
    void testHealthMetrics() {
        String name = uniqueName();
        CacheFacade cache = manager.getOrCreateCache(name);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        Map<String, Object> metrics = manager.getHealthMetrics();

        assertTrue((Integer) metrics.get("totalCaches") >= 1);
        assertTrue((Long) metrics.get("totalEntries") >= 2);
        assertNotNull(metrics.get("averageHitRate"));
        assertNotNull(metrics.get("totalEvictions"));
        assertNotNull(metrics.get("cacheNames"));
    }

    @Test
    @DisplayName("getCacheStats returns detailed stats for known cache")
    void testGetCacheStats() {
        String name = uniqueName();
        CacheFacade cache = manager.getOrCreateCache(name);
        cache.put("k", "v");
        cache.get("k");

        Map<String, Object> stats = manager.getCacheStats(name);

        assertEquals(name, stats.get("cacheName"));
        assertEquals(1L, stats.get("size"));
        assertNotNull(stats.get("hitCount"));
        assertNotNull(stats.get("missCount"));
        assertNotNull(stats.get("hitRate"));
        assertNotNull(stats.get("missRate"));
        assertNotNull(stats.get("loadCount"));
        assertNotNull(stats.get("loadSuccessCount"));
        assertNotNull(stats.get("loadFailureCount"));
        assertNotNull(stats.get("evictionCount"));
        assertNotNull(stats.get("averageLoadPenalty"));
    }

    @Test
    @DisplayName("getCacheStats returns empty map for unknown cache")
    void testGetCacheStatsUnknown() {
        assertTrue(manager.getCacheStats("unknown-" + UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("default configuration setters affect newly created caches")
    void testDefaultSetters() {
        manager.setDefaultMaxSize(50);
        manager.setDefaultExpireAfterWrite(Duration.ofMinutes(2));
        manager.setDefaultRecordStats(true);

        CacheFacade cache = manager.getOrCreateCache(uniqueName());
        cache.put("k", "v");
        cache.get("k");

        // recordStats default true means hit counting works
        assertEquals(1, cache.stats().hitCount());
    }

    @Test
    @DisplayName("getCacheCount and getTotalEntries reflect managed caches")
    void testCountsAndTotals() {
        String name = uniqueName();
        CacheFacade cache = manager.getOrCreateCache(name);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        assertTrue(manager.getCacheCount() >= 1);
        assertTrue(manager.getTotalEntries() >= 2);
    }

    @Test
    @DisplayName("getCache returns null for unknown name")
    void testGetCacheUnknown() {
        assertNull(manager.getCache("unknown-" + UUID.randomUUID()));
    }
}
