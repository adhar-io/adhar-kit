package com.adhar.adharkit.cache;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CacheFacade.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("CacheFacade Tests")
class CacheFacadeTest {

    private CacheFacade cache;

    @BeforeEach
    void setUp() {
        cache = CacheFacade.builder()
            .cacheName("test-cache")
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats(true)
            .build();

        cache.clear();
    }

    @Test
    @DisplayName("Should put and get value")
    void testPutAndGet() {
        // Given
        String key = "key1";
        String value = "value1";

        // When
        cache.put(key, value);
        String retrieved = cache.get(key);

        // Then
        assertNotNull(retrieved);
        assertEquals(value, retrieved);
    }

    @Test
    @DisplayName("Should return null for non-existent key")
    void testGetNonExistent() {
        // When
        String result = cache.get("non-existent");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should put multiple entries")
    void testPutAll() {
        // Given
        Map<String, String> entries = Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3"
        );

        // When
        cache.putAll(entries);

        // Then
        assertEquals("value1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertEquals("value3", cache.get("key3"));
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("Should get with type checking")
    void testGetWithType() {
        // Given
        cache.put("key1", "string value");
        cache.put("key2", 123);

        // When
        String stringValue = cache.get("key1", String.class);
        Integer intValue = cache.get("key2", Integer.class);

        // Then
        assertEquals("string value", stringValue);
        assertEquals(123, intValue);
    }

    @Test
    @DisplayName("Should return null for type mismatch")
    void testGetWithWrongType() {
        // Given
        cache.put("key1", "string value");

        // When
        Integer result = cache.get("key1", Integer.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should load value if not present")
    void testGetWithLoader() {
        // Given
        String key = "user123";

        // When
        String value = cache.get(key, k -> "loaded-" + k);

        // Then
        assertEquals("loaded-user123", value);
        assertEquals("loaded-user123", cache.get(key));
    }

    @Test
    @DisplayName("Should not reload if value exists")
    void testGetWithLoaderCached() {
        // Given
        cache.put("key1", "existing");

        // When
        String value = cache.get("key1", k -> "loaded");

        // Then
        assertEquals("existing", value);
    }

    @Test
    @DisplayName("Should get multiple values")
    void testGetAll() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // When
        Map<Object, Object> results = cache.getAll(Set.of("key1", "key2", "key4"));

        // Then
        assertEquals(2, results.size());
        assertEquals("value1", results.get("key1"));
        assertEquals("value2", results.get("key2"));
        assertFalse(results.containsKey("key4"));
    }

    @Test
    @DisplayName("Should evict entry")
    void testEvict() {
        // Given
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));

        // When
        cache.evict("key1");

        // Then
        assertNull(cache.get("key1"));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Should evict multiple entries")
    void testEvictAll() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // When
        cache.evictAll(Set.of("key1", "key2"));

        // Then
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertEquals("value3", cache.get("key3"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Should clear all entries")
    void testClear() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        assertEquals(2, cache.size());

        // When
        cache.clear();

        // Then
        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }

    @Test
    @DisplayName("Should record statistics")
    void testStats() {
        // Given
        cache.put("key1", "value1");
        cache.get("key1");  // Hit
        cache.get("key2");  // Miss

        // When
        CacheStats stats = cache.stats();

        // Then
        assertNotNull(stats);
        assertEquals(1, stats.hitCount());
        assertEquals(1, stats.missCount());
        assertEquals(0.5, stats.hitRate(), 0.01);
    }

    @Test
    @DisplayName("Should check if key exists")
    void testContainsKey() {
        // Given
        cache.put("key1", "value1");

        // Then
        assertTrue(cache.containsKey("key1"));
        assertFalse(cache.containsKey("key2"));
    }

    @Test
    @DisplayName("Should get all keys")
    void testKeys() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // When
        Set<Object> keys = cache.keys();

        // Then
        assertEquals(2, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
    }

    @Test
    @DisplayName("Should get cache size")
    void testSize() {
        // When
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // Then
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("Should put if absent")
    void testPutIfAbsent() {
        // When
        Object prev1 = cache.putIfAbsent("key1", "value1");
        Object prev2 = cache.putIfAbsent("key1", "value2");

        // Then
        assertNull(prev1);
        assertEquals("value1", prev2);
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void testNullKey() {
        assertThrows(NullPointerException.class, () ->
            cache.put(null, "value")
        );
    }

    @Test
    @DisplayName("Should throw exception for null value")
    void testNullValue() {
        assertThrows(NullPointerException.class, () ->
            cache.put("key", null)
        );
    }

    @Test
    @DisplayName("Should create loading cache")
    void testLoadingCache() {
        // Given
        CacheFacade loadingCache = CacheFacade.<String, String>loadingBuilder()
            .cacheName("loading-test")
            .maximumSize(100)
            .loader(key -> "loaded-" + key)
            .build();

        // When
        String value = loadingCache.get("key1");

        // Then
        assertEquals("loaded-key1", value);
    }

    @Test
    @DisplayName("Should expire entries after write")
    void testExpireAfterWrite() throws InterruptedException {
        // Given
        CacheFacade expiring = CacheFacade.builder()
            .cacheName("expiring")
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMillis(100))
            .build();

        // When
        expiring.put("key1", "value1");
        assertEquals("value1", expiring.get("key1"));

        Thread.sleep(150);
        expiring.cleanUp();

        // Then
        assertNull(expiring.get("key1"));
    }

    @Test
    @DisplayName("Should retrieve cache from registry")
    void testCacheRegistry() {
        // When
        CacheFacade retrieved = CacheFacade.getCache("test-cache");

        // Then
        assertNotNull(retrieved);
        assertEquals("test-cache", retrieved.getCacheName());
    }

    @Test
    @DisplayName("Should get all caches from registry")
    void testGetAllCaches() {
        // When
        Map<String, CacheFacade> allCaches = CacheFacade.getAllCaches();

        // Then
        assertNotNull(allCaches);
        assertTrue(allCaches.containsKey("test-cache"));
    }

    @Test
    @DisplayName("Should handle complex objects")
    void testComplexObjects() {
        // Given
        User user = new User("123", "John", "john@example.com");

        // When
        cache.put("user123", user);
        User retrieved = cache.get("user123", User.class);

        // Then
        assertNotNull(retrieved);
        assertEquals("123", retrieved.getId());
        assertEquals("John", retrieved.getName());
        assertEquals("john@example.com", retrieved.getEmail());
    }

    // Test helper class
    static class User {
        private final String id;
        private final String name;
        private final String email;

        public User(String id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
    }
}

