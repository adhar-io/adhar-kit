package com.adhar.adharkit.cache.multilevel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemorySecondLevelCache}.
 */
@DisplayName("InMemorySecondLevelCache Tests")
class InMemorySecondLevelCacheTest {

    private InMemorySecondLevelCache cache;

    @BeforeEach
    void setUp() {
        cache = new InMemorySecondLevelCache();
    }

    @Test
    @DisplayName("put/get round-trips values")
    void putAndGet() {
        cache.put("c1", "k1", "v1", null);
        assertEquals("v1", cache.get("c1", "k1"));
        assertEquals(1, cache.size("c1"));
    }

    @Test
    @DisplayName("get on unknown cache or key returns null")
    void unknownReturnsNull() {
        assertNull(cache.get("missing", "k1"));
        cache.put("c1", "k1", "v1", null);
        assertNull(cache.get("c1", "missing"));
    }

    @Test
    @DisplayName("entries expire after their TTL")
    void ttlExpiry() {
        cache.put("c1", "k1", "v1", Duration.ofMillis(100));
        assertEquals("v1", cache.get("c1", "k1"));

        await().atMost(2, TimeUnit.SECONDS).pollInterval(25, TimeUnit.MILLISECONDS)
            .until(() -> cache.get("c1", "k1") == null);
    }

    @Test
    @DisplayName("zero or negative TTL means no expiry")
    void nonPositiveTtlNeverExpires() {
        cache.put("c1", "k-zero", "v", Duration.ZERO);
        cache.put("c1", "k-neg", "v", Duration.ofMillis(-5));
        assertEquals("v", cache.get("c1", "k-zero"));
        assertEquals("v", cache.get("c1", "k-neg"));
    }

    @Test
    @DisplayName("evict removes a single key; clear removes all")
    void evictAndClear() {
        cache.put("c1", "k1", "v1", null);
        cache.put("c1", "k2", "v2", null);

        cache.evict("c1", "k1");
        assertNull(cache.get("c1", "k1"));
        assertEquals("v2", cache.get("c1", "k2"));

        cache.clear("c1");
        assertEquals(0, cache.size("c1"));
    }

    @Test
    @DisplayName("evict/clear/size on unknown caches are no-ops")
    void unknownCacheNoOps() {
        assertDoesNotThrow(() -> cache.evict("missing", "k1"));
        assertDoesNotThrow(() -> cache.clear("missing"));
        assertEquals(0, cache.size("missing"));
    }
}
