package com.adhar.adharkit.cache.multilevel;

import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DaprSecondLevelCache} against a mocked {@link DaprFacade},
 * including the Caffeine-L1 + Dapr-L2 read-through composition via
 * {@link MultiLevelCacheService}.
 */
@DisplayName("DaprSecondLevelCache Tests")
@ExtendWith(MockitoExtension.class)
class DaprSecondLevelCacheTest {

    private static final String STORE = "statestore";

    @Mock
    private DaprFacade daprFacade;

    private DaprSecondLevelCache l2;

    @BeforeEach
    void setUp() {
        l2 = new DaprSecondLevelCache(daprFacade, STORE);
    }

    @Test
    @DisplayName("put without TTL saves state under the namespaced 'cacheName:key'")
    void putWithoutTtl() {
        l2.put("users", "u1", "value-1", null);

        verify(daprFacade).saveState(STORE, "users:u1", "value-1");
        verify(daprFacade, never()).saveStateWithTTL(any(), any(), any(), any());
    }

    @Test
    @DisplayName("put with zero or negative TTL saves without expiry")
    void putWithNonPositiveTtl() {
        l2.put("users", "u1", "v", Duration.ZERO);
        l2.put("users", "u2", "v", Duration.ofSeconds(-5));

        verify(daprFacade).saveState(STORE, "users:u1", "v");
        verify(daprFacade).saveState(STORE, "users:u2", "v");
        verify(daprFacade, never()).saveStateWithTTL(any(), any(), any(), any());
    }

    @Test
    @DisplayName("put with positive TTL delegates to saveStateWithTTL")
    void putWithTtl() {
        Duration ttl = Duration.ofMinutes(10);
        l2.put("users", "u1", "value-1", ttl);

        verify(daprFacade).saveStateWithTTL(STORE, "users:u1", "value-1", ttl);
        verify(daprFacade, never()).saveState(any(), any(), any());
    }

    @Test
    @DisplayName("get returns the stored value for the namespaced key")
    void getHit() {
        when(daprFacade.getState(STORE, "users:u1", Object.class)).thenReturn("value-1");

        assertEquals("value-1", l2.get("users", "u1"));
    }

    @Test
    @DisplayName("get returns null on a miss")
    void getMiss() {
        when(daprFacade.getState(STORE, "users:missing", Object.class)).thenReturn(null);

        assertNull(l2.get("users", "missing"));
    }

    @Test
    @DisplayName("get treats a Dapr failure as a miss instead of propagating")
    void getFailureIsMiss() {
        when(daprFacade.getState(STORE, "users:u1", Object.class))
            .thenThrow(new RuntimeException("sidecar down"));

        assertNull(l2.get("users", "u1"));
    }

    @Test
    @DisplayName("evict deletes the namespaced state entry")
    void evict() {
        l2.evict("users", "u1");

        verify(daprFacade).deleteState(STORE, "users:u1");
    }

    @Test
    @DisplayName("clear is unsupported (Dapr state has no key enumeration)")
    void clearUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> l2.clear("users"));
    }

    @Test
    @DisplayName("distinct cache names namespace the same key differently")
    void keyNamespacing() {
        l2.put("users", "k", "u-value", null);
        l2.put("orders", "k", "o-value", null);

        verify(daprFacade).saveState(STORE, "users:k", "u-value");
        verify(daprFacade).saveState(STORE, "orders:k", "o-value");
    }

    @Test
    @DisplayName("constructor rejects null facade and store name")
    void constructorNullChecks() {
        assertThrows(NullPointerException.class, () -> new DaprSecondLevelCache(null, STORE));
        assertThrows(NullPointerException.class, () -> new DaprSecondLevelCache(daprFacade, null));
    }

    @Test
    @DisplayName("exposes the configured state store name")
    void exposesStateStoreName() {
        assertEquals(STORE, l2.getStateStoreName());
    }

    @Nested
    @DisplayName("MultiLevelCacheService with Dapr L2")
    class MultiLevelComposition {

        private final CacheManager cacheManager = CacheManager.getInstance();
        private final AtomicInteger loads = new AtomicInteger();
        private MultiLevelCacheService service;
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
        @DisplayName("L1 miss falls back to the Dapr state store and promotes the hit into L1")
        void l2HitPromotesToL1() {
            when(daprFacade.getState(STORE, l2Name + ":k1", Object.class)).thenReturn("dapr-value");

            assertEquals("dapr-value", get("k1"));
            assertEquals(0, loads.get(), "L2 hit must not invoke the loader");
            assertEquals("dapr-value", cacheManager.getCache(l1Name).get("k1"),
                "L2 hit must be promoted into L1");

            // Second read is an L1 hit and must not touch Dapr again.
            assertEquals("dapr-value", get("k1"));
            verify(daprFacade, times(1)).getState(STORE, l2Name + ":k1", Object.class);
        }

        @Test
        @DisplayName("full miss loads and writes through to the Dapr state store with the L2 TTL")
        void fullMissWritesThrough() {
            when(daprFacade.getState(STORE, l2Name + ":k1", Object.class)).thenReturn(null);

            assertEquals("loaded-1", get("k1"));
            assertEquals(1, loads.get());
            assertEquals("loaded-1", cacheManager.getCache(l1Name).get("k1"));
            verify(daprFacade).saveStateWithTTL(STORE, l2Name + ":k1", "loaded-1",
                Duration.ofMinutes(60));
        }

        @Test
        @DisplayName("evict removes from both L1 and the Dapr state store")
        void evictBothLevels() {
            when(daprFacade.getState(STORE, l2Name + ":k1", Object.class)).thenReturn(null);
            get("k1");

            service.evict(l1Name, l2Name, "k1");
            assertNull(cacheManager.getCache(l1Name).get("k1"));
            verify(daprFacade).deleteState(STORE, l2Name + ":k1");
        }

        @Test
        @DisplayName("a Dapr read failure degrades to the loader instead of failing the read")
        void daprFailureFallsBackToLoader() {
            when(daprFacade.getState(STORE, l2Name + ":k1", Object.class))
                .thenThrow(new RuntimeException("sidecar down"));

            assertEquals("loaded-1", get("k1"));
            assertEquals(1, loads.get());
        }
    }
}
