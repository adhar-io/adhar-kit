package com.adhar.adharkit.cache.multilevel;

import com.adhar.adharkit.cache.manager.CacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MultiLevelCacheService} read-through/write-through composition.
 */
@DisplayName("MultiLevelCacheService Tests")
class MultiLevelCacheServiceTest {

    private CacheManager cacheManager;
    private InMemorySecondLevelCache l2;
    private MultiLevelCacheService service;
    private String l1Name;
    private String l2Name;
    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        l2 = new InMemorySecondLevelCache();
        service = new MultiLevelCacheService(cacheManager, l2);
        l1Name = "ml1-" + UUID.randomUUID();
        l2Name = "ml2-" + UUID.randomUUID();
        loads.set(0);
    }

    private Object get(Object key, boolean writeThrough) {
        return service.get(l1Name, l2Name, key, Duration.ofMinutes(5), Duration.ofMinutes(60),
            writeThrough, () -> "loaded-" + loads.incrementAndGet());
    }

    @Test
    @DisplayName("full miss loads and populates both levels (write-through)")
    void fullMissLoadsAndWritesThrough() {
        assertEquals("loaded-1", get("k1", true));
        assertEquals(1, loads.get());
        assertEquals("loaded-1", cacheManager.getCache(l1Name).get("k1"));
        assertEquals("loaded-1", l2.get(l2Name, "k1"));
    }

    @Test
    @DisplayName("writeThrough=false populates only L1 on load")
    void loadWithoutWriteThrough() {
        assertEquals("loaded-1", get("k1", false));
        assertEquals("loaded-1", cacheManager.getCache(l1Name).get("k1"));
        assertNull(l2.get(l2Name, "k1"));
    }

    @Test
    @DisplayName("L1 hit returns without touching L2 or the loader")
    void l1Hit() {
        get("k1", true);
        assertEquals("loaded-1", get("k1", true));
        assertEquals(1, loads.get(), "L1 hit must not reload");
    }

    @Test
    @DisplayName("L1 miss falls back to L2 and promotes the value into L1")
    void l2HitPromotesToL1() {
        get("k1", true);
        cacheManager.getCache(l1Name).evict("k1");

        assertEquals("loaded-1", get("k1", true));
        assertEquals(1, loads.get(), "L2 hit must not reload");
        assertEquals("loaded-1", cacheManager.getCache(l1Name).get("k1"),
            "L2 hit must be promoted into L1");
    }

    @Test
    @DisplayName("null loader results are not cached")
    void nullLoadNotCached() {
        Object value = service.get(l1Name, l2Name, "k-null", Duration.ofMinutes(5),
            Duration.ofMinutes(60), true, () -> null);
        assertNull(value);
        assertNull(cacheManager.getCache(l1Name).get("k-null"));
        assertNull(l2.get(l2Name, "k-null"));
    }

    @Test
    @DisplayName("put writes both levels when write-through, evict removes from both")
    void putAndEvict() {
        service.put(l1Name, l2Name, "k1", "v1", Duration.ofMinutes(5), Duration.ofMinutes(60), true);
        assertEquals("v1", cacheManager.getCache(l1Name).get("k1"));
        assertEquals("v1", l2.get(l2Name, "k1"));

        service.evict(l1Name, l2Name, "k1");
        assertNull(cacheManager.getCache(l1Name).get("k1"));
        assertNull(l2.get(l2Name, "k1"));
    }

    @Test
    @DisplayName("put without write-through skips L2; null values ignored")
    void putVariants() {
        service.put(l1Name, l2Name, "k1", "v1", Duration.ofMinutes(5), Duration.ofMinutes(60), false);
        assertEquals("v1", cacheManager.getCache(l1Name).get("k1"));
        assertNull(l2.get(l2Name, "k1"));

        service.put(l1Name, l2Name, "k2", null, Duration.ofMinutes(5), Duration.ofMinutes(60), true);
        assertNull(cacheManager.getCache(l1Name).get("k2"));
    }

    @Test
    @DisplayName("evict on unknown L1 cache is a no-op")
    void evictUnknownL1() {
        assertDoesNotThrow(() -> service.evict("ml1-unknown-" + UUID.randomUUID(), l2Name, "k1"));
    }

    @Test
    @DisplayName("L1 TTL applies when the cache is first created")
    void l1TtlHonoredOnCreation() {
        String shortL1 = "ml1-short-" + UUID.randomUUID();
        service.put(shortL1, l2Name, "k1", "v1", Duration.ofMillis(150), Duration.ofMinutes(60), false);
        assertEquals("v1", cacheManager.getCache(shortL1).get("k1"));

        await().atMost(2, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
            .until(() -> cacheManager.getCache(shortL1).get("k1") == null);
    }

    @Test
    @DisplayName("null or non-positive L1 TTL uses manager defaults")
    void l1DefaultTtl() {
        String name = "ml1-default-" + UUID.randomUUID();
        service.put(name, l2Name, "k1", "v1", null, Duration.ofMinutes(60), false);
        assertEquals("v1", cacheManager.getCache(name).get("k1"));

        String name2 = "ml1-zero-" + UUID.randomUUID();
        service.put(name2, l2Name, "k1", "v1", Duration.ZERO, Duration.ofMinutes(60), false);
        assertEquals("v1", cacheManager.getCache(name2).get("k1"));
    }
}
