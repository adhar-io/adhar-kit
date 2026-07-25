package com.adhar.kit.analytics.flag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeatureFlagCache Tests")
class FeatureFlagCacheTest {

    /**
     * A mutable {@link Clock} so tests can advance "now" deterministically
     * without sleeping.
     */
    private static Clock mutableClock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }

    @Test
    @DisplayName("returns empty for a never-cached key")
    void missOnUncachedKey() {
        FeatureFlagCache cache = new FeatureFlagCache(Duration.ofSeconds(60), Clock.systemUTC());
        assertTrue(cache.get("user1", "flag1").isEmpty());
    }

    @Test
    @DisplayName("caches a decision and serves it before expiry")
    void servesCachedValueBeforeExpiry() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
        FeatureFlagCache cache = new FeatureFlagCache(Duration.ofSeconds(60), mutableClock(now));

        cache.put("user1", "flag1", "variant-a", true);

        now.set(now.get().plusSeconds(30));
        Optional<FeatureFlagCache.CachedFlag> cached = cache.get("user1", "flag1");
        assertTrue(cached.isPresent());
        assertTrue(cached.get().enabled());
        assertEquals("variant-a", cached.get().value());
    }

    @Test
    @DisplayName("expires entries after the configured TTL (lazy expiry on read)")
    void expiresAfterTtl() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
        FeatureFlagCache cache = new FeatureFlagCache(Duration.ofSeconds(60), mutableClock(now));

        cache.put("user1", "flag1", true, true);
        assertTrue(cache.get("user1", "flag1").isPresent());

        now.set(now.get().plusSeconds(61));
        assertTrue(cache.get("user1", "flag1").isEmpty(), "entry should have expired after TTL elapsed");
    }

    @Test
    @DisplayName("re-caching after expiry resets the TTL")
    void refreshAfterExpiry() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
        FeatureFlagCache cache = new FeatureFlagCache(Duration.ofSeconds(10), mutableClock(now));

        cache.put("user1", "flag1", false, false);
        now.set(now.get().plusSeconds(11));
        assertTrue(cache.get("user1", "flag1").isEmpty());

        cache.put("user1", "flag1", true, true);
        assertTrue(cache.get("user1", "flag1").get().enabled());
    }

    @Test
    @DisplayName("clear() removes all cached decisions for all users")
    void clearRemovesEverything() {
        FeatureFlagCache cache = new FeatureFlagCache(Duration.ofSeconds(60), Clock.systemUTC());
        cache.put("user1", "flag1", true, true);
        cache.put("user2", "flag1", true, true);
        assertEquals(2, cache.userCount());

        cache.clear();
        assertEquals(0, cache.userCount());
        assertTrue(cache.get("user1", "flag1").isEmpty());
    }

    @Test
    @DisplayName("non-positive TTL falls back to a sane default")
    void nonPositiveTtlFallsBackToDefault() {
        FeatureFlagCache zero = new FeatureFlagCache(Duration.ZERO, Clock.systemUTC());
        FeatureFlagCache negative = new FeatureFlagCache(Duration.ofSeconds(-5), Clock.systemUTC());
        FeatureFlagCache nullTtl = new FeatureFlagCache(null, null);

        assertEquals(Duration.ofSeconds(60), zero.ttl());
        assertEquals(Duration.ofSeconds(60), negative.ttl());
        assertEquals(Duration.ofSeconds(60), nullTtl.ttl());
    }
}
