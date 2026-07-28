package com.adhar.kit.resilience.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FallbackCache} covering put/get, TTL expiry, {@code null}
 * handling, LRU eviction, size and clear.
 */
@DisplayName("FallbackCache Tests")
class FallbackCacheTest {

    @Test
    @DisplayName("stores and returns the last known good value")
    void putAndGet() {
        FallbackCache cache = new FallbackCache(10, null);

        cache.put("k1", "v1");

        assertThat(cache.get("k1")).contains("v1");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns empty for an absent key")
    void missReturnsEmpty() {
        FallbackCache cache = new FallbackCache(10, null);

        assertThat(cache.get("absent")).isEmpty();
    }

    @Test
    @DisplayName("null key on put is ignored")
    void nullKeyPutIgnored() {
        FallbackCache cache = new FallbackCache(10, null);

        cache.put(null, "v");

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("null value on put is ignored (a cached null is indistinguishable from a miss)")
    void nullValuePutIgnored() {
        FallbackCache cache = new FallbackCache(10, null);

        cache.put("k", null);

        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("null key on get returns empty")
    void nullKeyGetReturnsEmpty() {
        FallbackCache cache = new FallbackCache(10, null);

        assertThat(cache.get(null)).isEmpty();
    }

    @Test
    @DisplayName("a later put for the same key overwrites the previous value")
    void putOverwrites() {
        FallbackCache cache = new FallbackCache(10, null);

        cache.put("k", "first");
        cache.put("k", "second");

        assertThat(cache.get("k")).contains("second");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("null TTL disables expiry")
    void nullTtlNeverExpires() throws InterruptedException {
        FallbackCache cache = new FallbackCache(10, null);
        cache.put("k", "v");

        Thread.sleep(20);

        assertThat(cache.get("k")).contains("v");
    }

    @Test
    @DisplayName("zero TTL disables expiry")
    void zeroTtlNeverExpires() throws InterruptedException {
        FallbackCache cache = new FallbackCache(10, Duration.ZERO);
        cache.put("k", "v");

        Thread.sleep(20);

        assertThat(cache.get("k")).contains("v");
    }

    @Test
    @DisplayName("negative TTL disables expiry")
    void negativeTtlNeverExpires() throws InterruptedException {
        FallbackCache cache = new FallbackCache(10, Duration.ofMillis(-5));
        cache.put("k", "v");

        Thread.sleep(20);

        assertThat(cache.get("k")).contains("v");
    }

    @Test
    @DisplayName("entries expire after the TTL and are evicted on read")
    void ttlExpiry() throws InterruptedException {
        FallbackCache cache = new FallbackCache(10, Duration.ofMillis(20));
        cache.put("k", "v");

        assertThat(cache.get("k")).contains("v");

        Thread.sleep(60);

        assertThat(cache.get("k")).isEmpty();
        // the expired entry is removed on the failing read
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("evicts the least-recently-used entry when the size bound is exceeded")
    void lruEviction() {
        FallbackCache cache = new FallbackCache(2, null);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        // access k1 so k2 becomes the least-recently-used
        assertThat(cache.get("k1")).contains("v1");

        cache.put("k3", "v3"); // exceeds bound -> evicts k2

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("k1")).contains("v1");
        assertThat(cache.get("k2")).isEmpty();
        assertThat(cache.get("k3")).contains("v3");
    }

    @Test
    @DisplayName("maxSize below one is raised to one")
    void maxSizeClampedToOne() {
        FallbackCache cache = new FallbackCache(0, null);

        cache.put("k1", "v1");
        cache.put("k2", "v2"); // evicts k1

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get("k1")).isEmpty();
        assertThat(cache.get("k2")).contains("v2");
    }

    @Test
    @DisplayName("clear removes all entries")
    void clear() {
        FallbackCache cache = new FallbackCache(10, null);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        cache.clear();

        assertThat(cache.size()).isZero();
        assertThat(cache.get("k1")).isEmpty();
    }
}
