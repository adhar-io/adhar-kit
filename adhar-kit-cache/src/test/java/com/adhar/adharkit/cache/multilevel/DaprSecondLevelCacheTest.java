package com.adhar.adharkit.cache.multilevel;

import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DaprSecondLevelCache} on an in-memory Dapr facade: typed hits, TTL, clear and
 * graceful degradation, plus its composition into {@link MultiLevelCacheService}.
 */
@DisplayName("DaprSecondLevelCache Tests")
class DaprSecondLevelCacheTest {

    private static final String STORE = "statestore";

    record Product(String sku, int stock, Instant updatedAt) {
    }

    private DaprFacade dapr;
    private DaprSecondLevelCache l2;

    @BeforeEach
    void setUp() {
        dapr = DaprFacade.builder()
            .connection(DaprConnectionDetails.resolve(Map.of("adhar.dapr.mode", "memory")::get))
            .build();
        l2 = new DaprSecondLevelCache(dapr, STORE);
    }

    @Test
    @DisplayName("a hit returns the class that was cached, not a Map")
    void hitsAreTyped() {
        Product product = new Product("A-1", 4, Instant.parse("2026-10-28T00:00:00Z"));
        l2.put("products", "A-1", product, Duration.ofMinutes(5));
        l2.put("products", "all", List.of(product), Duration.ofMinutes(5));

        assertEquals(product, l2.get("products", "A-1"));
        Object all = l2.get("products", "all");
        assertInstanceOf(List.class, all);
        assertInstanceOf(Product.class, ((List<?>) all).getFirst());
    }

    @Test
    void missesReturnNull() {
        assertNull(l2.get("products", "missing"));
    }

    @Test
    void evictRemovesAnEntry() {
        l2.put("products", "k", "v", null);
        l2.evict("products", "k");
        assertNull(l2.get("products", "k"));
    }

    @Test
    @DisplayName("clear empties one cache without listing keys")
    void clearStartsANewGeneration() {
        l2.put("products", "k", "v", Duration.ofMinutes(5));
        l2.put("prices", "k", "p", Duration.ofMinutes(5));

        l2.clear("products");

        assertNull(l2.get("products", "k"));
        assertEquals("p", l2.get("prices", "k"));
        l2.put("products", "k", "v2", Duration.ofMinutes(5));
        assertEquals("v2", l2.get("products", "k"));
    }

    @Test
    void entriesCarryTheirTtlIntoTheStore() {
        DaprFacade spy = spy(dapr);
        DaprSecondLevelCache cache = new DaprSecondLevelCache(spy, STORE);
        cache.put("products", "k", "v", Duration.ofSeconds(90));
        verify(spy).saveState(eq(STORE), startsWith("cache:products:"),
            any(), argThat(options -> options.requestMetadata().get("ttlInSeconds").equals("90")));
    }

    @Test
    @DisplayName("a store failure on read is a miss")
    void readFailuresAreMisses() {
        DaprFacade broken = mock(DaprFacade.class);
        when(broken.getState(anyString(), anyString(), eq(Long.class))).thenThrow(new RuntimeException("sidecar down"));
        assertNull(new DaprSecondLevelCache(broken, STORE).get("products", "k"));
    }

    @Test
    void constructorNullChecks() {
        assertThrows(NullPointerException.class, () -> new DaprSecondLevelCache(null, STORE));
        assertThrows(NullPointerException.class, () -> new DaprSecondLevelCache(dapr, null));
    }

    @Test
    void exposesStateStoreName() {
        assertEquals(STORE, l2.getStateStoreName());
    }

    @Nested
    @DisplayName("Composed into MultiLevelCacheService")
    class MultiLevelComposition {

        private final CacheManager cacheManager = CacheManager.getInstance();
        private MultiLevelCacheService service;
        private final AtomicInteger loads = new AtomicInteger();
        private String l1Name;
        private String l2Name;

        @BeforeEach
        void setUpService() {
            service = new MultiLevelCacheService(cacheManager, l2);
            l1Name = "dapr-l1-" + UUID.randomUUID();
            l2Name = "dapr-l2-" + UUID.randomUUID();
            loads.set(0);
        }

        private Object get(Object key) {
            return service.get(l1Name, l2Name, key, Duration.ofMinutes(5), Duration.ofMinutes(60),
                true, () -> "loaded-" + loads.incrementAndGet());
        }

        @Test
        @DisplayName("a full miss loads once and writes through; later reads hit")
        void fullMissWritesThrough() {
            assertEquals("loaded-1", get("k1"));
            assertEquals("loaded-1", l2.get(l2Name, "k1"));
            assertEquals("loaded-1", get("k1"));
            assertEquals(1, loads.get());
        }

        @Test
        @DisplayName("an L2 hit is promoted into L1 without loading")
        void l2HitPromotesToL1() {
            l2.put(l2Name, "k1", "dapr-value", Duration.ofMinutes(5));

            assertEquals("dapr-value", get("k1"));
            assertEquals(0, loads.get());
            assertEquals("dapr-value", cacheManager.getCache(l1Name).get("k1"));
        }

        @Test
        @DisplayName("evict removes from both levels")
        void evictBothLevels() {
            get("k1");
            service.evict(l1Name, l2Name, "k1");
            assertNull(cacheManager.getCache(l1Name).get("k1"));
            assertNull(l2.get(l2Name, "k1"));
        }
    }
}
