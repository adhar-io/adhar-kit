package com.adhar.adharkit.cache.metrics;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.manager.CacheManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheMetricsBinder}.
 */
@DisplayName("CacheMetricsBinder Tests")
class CacheMetricsBinderTest {

    private CacheManager cacheManager;
    private CacheMetricsBinder binder;
    private SimpleMeterRegistry registry;
    private String cacheName;

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        binder = new CacheMetricsBinder(cacheManager);
        registry = new SimpleMeterRegistry();
        cacheName = "metrics-" + UUID.randomUUID();
        cacheManager.getOrCreateCache(cacheName);
    }

    @Test
    @DisplayName("bindTo registers hit/miss/eviction/size/ratio meters per cache")
    void bindToRegistersMeters() {
        CacheFacade cache = cacheManager.getCache(cacheName);
        cache.put("k1", "v1");
        cache.get("k1"); // hit
        cache.get("k1"); // hit
        cache.get("absent"); // miss

        binder.bindTo(registry);

        assertEquals(2.0, registry.get("adhar.cache.gets")
            .tags("cache", cacheName, "result", "hit").functionCounter().count());
        assertEquals(1.0, registry.get("adhar.cache.gets")
            .tags("cache", cacheName, "result", "miss").functionCounter().count());
        assertEquals(0.0, registry.get("adhar.cache.evictions")
            .tags("cache", cacheName).functionCounter().count());
        assertEquals(1.0, registry.get("adhar.cache.size")
            .tags("cache", cacheName).gauge().value());
        double hitRatio = registry.get("adhar.cache.hit.ratio")
            .tags("cache", cacheName).gauge().value();
        assertTrue(hitRatio > 0.6 && hitRatio < 0.7, "expected ~2/3 hit ratio, got " + hitRatio);
    }

    @Test
    @DisplayName("binding is idempotent across bindAll calls")
    void bindingIsIdempotent() {
        binder.bindTo(registry);
        int meterCount = registry.getMeters().size();
        int boundCount = binder.getBoundCacheCount();

        binder.bindAll();
        binder.bindCache(cacheName);

        assertEquals(meterCount, registry.getMeters().size(), "re-binding must not duplicate meters");
        assertEquals(boundCount, binder.getBoundCacheCount());
    }

    @Test
    @DisplayName("caches created after bindTo are picked up lazily by bindAll")
    void lateCachesAreBound() {
        binder.bindTo(registry);

        String lateCache = "metrics-late-" + UUID.randomUUID();
        cacheManager.getOrCreateCache(lateCache);
        assertTrue(registry.find("adhar.cache.size").tags("cache", lateCache).gauges().isEmpty());

        binder.bindAll();
        assertNotNull(registry.get("adhar.cache.size").tags("cache", lateCache).gauge());
    }

    @Test
    @DisplayName("bindAll/bindCache before a registry is attached are safe no-ops")
    void bindWithoutRegistryIsNoOp() {
        assertDoesNotThrow(binder::bindAll);
        assertDoesNotThrow(() -> binder.bindCache(cacheName));
        assertEquals(0, binder.getBoundCacheCount());
    }

    @Test
    @DisplayName("binding an unknown cache name is a no-op")
    void unknownCacheIsNoOp() {
        binder.bindTo(registry);
        int boundCount = binder.getBoundCacheCount();

        binder.bindCache("metrics-unknown-" + UUID.randomUUID());
        assertEquals(boundCount, binder.getBoundCacheCount());
    }
}
