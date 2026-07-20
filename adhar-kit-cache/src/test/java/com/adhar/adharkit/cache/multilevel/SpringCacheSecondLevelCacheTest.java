package com.adhar.adharkit.cache.multilevel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SpringCacheSecondLevelCache} adapting a Spring CacheManager.
 */
@DisplayName("SpringCacheSecondLevelCache Tests")
class SpringCacheSecondLevelCacheTest {

    private SpringCacheSecondLevelCache adapter;

    @BeforeEach
    void setUp() {
        // fixed cache names: unknown names yield null caches (exercises the null branches)
        adapter = new SpringCacheSecondLevelCache(new ConcurrentMapCacheManager("known"));
    }

    @Test
    @DisplayName("put/get round-trips through the Spring cache")
    void putAndGet() {
        adapter.put("known", "k1", "v1", Duration.ofMinutes(5));
        assertEquals("v1", adapter.get("known", "k1"));
    }

    @Test
    @DisplayName("get returns null for absent keys")
    void absentKey() {
        assertNull(adapter.get("known", "absent"));
    }

    @Test
    @DisplayName("evict and clear remove entries")
    void evictAndClear() {
        adapter.put("known", "k1", "v1", null);
        adapter.put("known", "k2", "v2", null);

        adapter.evict("known", "k1");
        assertNull(adapter.get("known", "k1"));
        assertEquals("v2", adapter.get("known", "k2"));

        adapter.clear("known");
        assertNull(adapter.get("known", "k2"));
    }

    @Test
    @DisplayName("operations on unknown caches are safe no-ops")
    void unknownCacheNoOps() {
        assertNull(adapter.get("unknown", "k1"));
        assertDoesNotThrow(() -> adapter.put("unknown", "k1", "v1", null));
        assertDoesNotThrow(() -> adapter.evict("unknown", "k1"));
        assertDoesNotThrow(() -> adapter.clear("unknown"));
    }
}
