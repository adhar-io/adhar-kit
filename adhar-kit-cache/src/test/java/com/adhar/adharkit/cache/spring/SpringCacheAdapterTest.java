package com.adhar.adharkit.cache.spring;

import com.adhar.adharkit.cache.api.CacheService;
import com.adhar.kit.commons.framework.Framework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SpringCacheAdapter} using mocked Spring {@link CacheManager}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SpringCacheAdapter Tests")
class SpringCacheAdapterTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private SpringCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringCacheAdapter(cacheManager);
        when(cacheManager.getCache("users")).thenReturn(cache);
        when(cacheManager.getCache("missing")).thenReturn(null);
    }

    @Test
    @DisplayName("reports SPRING_BOOT framework and returns itself as service")
    void testFrameworkAndService() {
        assertEquals(Framework.SPRING_BOOT, adapter.getSupportedFramework());
        assertSame(adapter, adapter.getService());
        assertTrue(adapter instanceof CacheService);
    }

    @Test
    @DisplayName("get returns value when present")
    void testGetPresent() {
        when(cache.get("k", String.class)).thenReturn("v");

        Optional<String> result = adapter.get("users", "k", String.class);

        assertTrue(result.isPresent());
        assertEquals("v", result.get());
    }

    @Test
    @DisplayName("get returns empty when value missing")
    void testGetMissingValue() {
        when(cache.get("k", String.class)).thenReturn(null);

        assertTrue(adapter.get("users", "k", String.class).isEmpty());
    }

    @Test
    @DisplayName("get returns empty when cache not found")
    void testGetNoCache() {
        assertTrue(adapter.get("missing", "k", String.class).isEmpty());
    }

    @Test
    @DisplayName("put stores value when cache exists")
    void testPut() {
        adapter.put("users", "k", "v");
        verify(cache).put("k", "v");
    }

    @Test
    @DisplayName("put does nothing when cache missing")
    void testPutNoCache() {
        adapter.put("missing", "k", "v");
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("put with TTL delegates to standard put")
    void testPutWithTtl() {
        adapter.put("users", "k", "v", Duration.ofMinutes(5));
        verify(cache).put("k", "v");
    }

    @Test
    @DisplayName("putIfAbsent stores when key absent and returns true")
    void testPutIfAbsentNew() {
        when(cache.get("k")).thenReturn(null);

        assertTrue(adapter.putIfAbsent("users", "k", "v"));
        verify(cache).putIfAbsent("k", "v");
    }

    @Test
    @DisplayName("putIfAbsent returns false when key already present")
    void testPutIfAbsentExisting() {
        when(cache.get("k")).thenReturn(mock(Cache.ValueWrapper.class));

        assertFalse(adapter.putIfAbsent("users", "k", "v"));
        verify(cache, never()).putIfAbsent(any(), any());
    }

    @Test
    @DisplayName("putIfAbsent returns false when cache missing")
    void testPutIfAbsentNoCache() {
        assertFalse(adapter.putIfAbsent("missing", "k", "v"));
    }

    @Test
    @DisplayName("getOrCompute uses cache loader when cache exists")
    void testGetOrComputeWithCache() throws Exception {
        when(cache.get(eq("k"), any(Callable.class))).thenReturn("computed");

        String result = adapter.getOrCompute("users", "k", String.class, () -> "computed");

        assertEquals("computed", result);
    }

    @Test
    @DisplayName("getOrCompute computes directly when cache missing")
    void testGetOrComputeNoCache() {
        String result = adapter.getOrCompute("missing", "k", String.class, () -> "direct");
        assertEquals("direct", result);
    }

    @Test
    @DisplayName("getOrCompute wraps loader exception when cache missing")
    void testGetOrComputeNoCacheError() {
        Callable<String> failing = () -> { throw new IllegalStateException("boom"); };
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> adapter.getOrCompute("missing", "k", String.class, failing));
        assertEquals("Failed to compute value", ex.getMessage());
    }

    @Test
    @DisplayName("getOrCompute wraps ValueRetrievalException from cache")
    void testGetOrComputeValueRetrievalException() {
        when(cache.get(eq("k"), any(Callable.class)))
            .thenThrow(new Cache.ValueRetrievalException("k", () -> null, new RuntimeException("inner")));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> adapter.getOrCompute("users", "k", String.class, () -> "x"));
        assertEquals("Failed to get or compute value", ex.getMessage());
    }

    @Test
    @DisplayName("evict removes key when cache exists")
    void testEvict() {
        adapter.evict("users", "k");
        verify(cache).evict("k");
    }

    @Test
    @DisplayName("evict does nothing when cache missing")
    void testEvictNoCache() {
        adapter.evict("missing", "k");
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("clear clears cache when present")
    void testClear() {
        adapter.clear("users");
        verify(cache).clear();
    }

    @Test
    @DisplayName("clear does nothing when cache missing")
    void testClearNoCache() {
        adapter.clear("missing");
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("contains returns true when value present")
    void testContainsTrue() {
        when(cache.get("k")).thenReturn(mock(Cache.ValueWrapper.class));
        assertTrue(adapter.contains("users", "k"));
    }

    @Test
    @DisplayName("contains returns false when value absent")
    void testContainsFalse() {
        when(cache.get("k")).thenReturn(null);
        assertFalse(adapter.contains("users", "k"));
    }

    @Test
    @DisplayName("contains returns false when cache missing")
    void testContainsNoCache() {
        assertFalse(adapter.contains("missing", "k"));
    }

    @Test
    @DisplayName("size returns -1 (unsupported in standard Spring Cache)")
    void testSize() {
        assertEquals(-1, adapter.size("users"));
    }
}
