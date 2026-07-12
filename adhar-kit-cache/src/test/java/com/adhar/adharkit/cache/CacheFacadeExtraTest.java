package com.adhar.adharkit.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for {@link CacheFacade} covering builder options and static registry helpers.
 */
@DisplayName("CacheFacade Extra Tests")
class CacheFacadeExtraTest {

    @Test
    @DisplayName("Builder applies expireAfterWrite, expireAfterAccess and initialCapacity")
    void testBuilderAllOptions() {
        CacheFacade cache = CacheFacade.builder()
            .cacheName("extra-" + System.nanoTime())
            .maximumSize(50)
            .initialCapacity(8)
            .expireAfterWrite(Duration.ofMinutes(5))
            .expireAfterAccess(Duration.ofMinutes(5))
            .recordStats(true)
            .build();

        cache.put("k", "v");
        assertEquals("v", cache.get("k"));
    }

    @Test
    @DisplayName("LoadingBuilder applies all options and loads on miss")
    void testLoadingBuilderAllOptions() {
        CacheFacade cache = CacheFacade.<String, String>loadingBuilder()
            .cacheName("extra-loading-" + System.nanoTime())
            .maximumSize(50)
            .expireAfterWrite(Duration.ofMinutes(5))
            .expireAfterAccess(Duration.ofMinutes(5))
            .refreshAfterWrite(Duration.ofMinutes(10))
            .recordStats(true)
            .loader(k -> "loaded-" + k)
            .build();

        assertEquals("loaded-a", cache.get("a"));
    }

    @Test
    @DisplayName("LoadingBuilder without loader throws NPE")
    void testLoadingBuilderNoLoader() {
        assertThrows(NullPointerException.class,
            () -> CacheFacade.loadingBuilder().cacheName("no-loader").build());
    }

    @Test
    @DisplayName("static getCache returns null for unknown name")
    void testStaticGetCacheUnknown() {
        assertNull(CacheFacade.getCache("definitely-not-registered-" + System.nanoTime()));
    }

    @Test
    @DisplayName("static clearAll empties all registered caches")
    void testStaticClearAll() {
        CacheFacade cache = CacheFacade.builder().cacheName("clear-all-" + System.nanoTime()).build();
        cache.put("k", "v");
        assertEquals(1, cache.size());

        CacheFacade.clearAll();

        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("getAll, evictAll and putAll validate null arguments")
    void testNullValidation() {
        CacheFacade cache = CacheFacade.builder().cacheName("nullcheck-" + System.nanoTime()).build();
        assertThrows(NullPointerException.class, () -> cache.putAll(null));
        assertThrows(NullPointerException.class, () -> cache.getAll(null));
        assertThrows(NullPointerException.class, () -> cache.evictAll(null));
        assertThrows(NullPointerException.class, () -> cache.evict(null));
        assertThrows(NullPointerException.class, () -> cache.get(null, String.class));
        assertThrows(NullPointerException.class, () -> cache.get("k", (java.util.function.Function<Object, Object>) null));
    }

    @Test
    @DisplayName("getAllCaches returns an unmodifiable registry view")
    void testGetAllCachesUnmodifiable() {
        Map<String, CacheFacade> all = CacheFacade.getAllCaches();
        assertThrows(UnsupportedOperationException.class, () -> all.put("x", null));
    }
}
