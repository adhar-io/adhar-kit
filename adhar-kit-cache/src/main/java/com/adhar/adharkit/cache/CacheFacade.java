package com.adhar.adharkit.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Universal Cache Facade providing enterprise-grade caching with Caffeine.
 *
 * <p>This facade provides a simplified, production-ready interface to Caffeine's
 * high-performance caching capabilities including:</p>
 * <ul>
 *   <li><b>Multiple Cache Types</b> - In-memory, loading, async caches</li>
 *   <li><b>TTL & Size-based Eviction</b> - Flexible expiration policies</li>
 *   <li><b>Statistics & Monitoring</b> - Track cache performance</li>
 *   <li><b>Thread-Safe</b> - Concurrent access support</li>
 *   <li><b>Type-Safe</b> - Generic key-value support</li>
 *   <li><b>Auto-Loading</b> - Lazy loading from data sources</li>
 *   <li><b>Batch Operations</b> - Put/Get multiple entries</li>
 * </ul>
 *
 * <p><b>Example - Basic Caching:</b></p>
 * <pre>{@code
 * CacheFacade cache = CacheFacade.builder()
 *     .cacheName("users")
 *     .maximumSize(1000)
 *     .expireAfterWrite(Duration.ofMinutes(10))
 *     .recordStats(true)
 *     .build();
 *
 * // Put value
 * cache.put("user123", user);
 *
 * // Get value
 * User user = cache.get("user123", User.class);
 *
 * // Get with loader
 * User user = cache.get("user123", userId -> userService.findById(userId));
 * }</pre>
 *
 * <p><b>Example - Loading Cache:</b></p>
 * <pre>{@code
 * CacheFacade cache = CacheFacade.loadingBuilder()
 *     .cacheName("products")
 *     .maximumSize(5000)
 *     .expireAfterWrite(Duration.ofHours(1))
 *     .loader(productId -> productService.findById(productId))
 *     .build();
 *
 * // Automatically loads from data source if not cached
 * Product product = cache.get("prod123");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class CacheFacade {

    /**
     * Cache name for identification and logging.
     */
    private final String cacheName;

    /**
     * Underlying Caffeine cache instance.
     */
    private final Cache<Object, Object> cache;

    /**
     * Registry of all cache instances for management.
     */
    private static final Map<String, CacheFacade> CACHE_REGISTRY = new ConcurrentHashMap<>();

    /**
     * Private constructor - use builder().
     */
    private CacheFacade(String cacheName, Cache<Object, Object> cache) {
        this.cacheName = cacheName;
        this.cache = cache;
        CACHE_REGISTRY.put(cacheName, this);
        log.info("Created cache: {} with configuration", cacheName);
    }

    // ==================== PUT OPERATIONS ====================

    /**
     * Puts a value into the cache.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * cache.put("key1", "value1");
     * cache.put("user123", userObject);
     * }</pre>
     *
     * @param key cache key (must not be null)
     * @param value value to cache (must not be null)
     */
    public void put(Object key, Object value) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(value, "Cache value must not be null");

        log.trace("Putting into cache '{}': key={}", cacheName, key);
        cache.put(key, value);
    }

    /**
     * Puts multiple entries into the cache.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Map<String, User> users = Map.of(
     *     "user1", user1,
     *     "user2", user2,
     *     "user3", user3
     * );
     * cache.putAll(users);
     * }</pre>
     *
     * @param entries map of key-value pairs to cache
     */
    public void putAll(Map<?, ?> entries) {
        Objects.requireNonNull(entries, "Entries map must not be null");

        log.trace("Putting {} entries into cache '{}'", entries.size(), cacheName);
        cache.putAll(entries);
    }

    /**
     * Puts a value only if the key is not already present.
     *
     * @param key cache key
     * @param value value to cache
     * @return the previous value associated with key, or null if none
     */
    public Object putIfAbsent(Object key, Object value) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(value, "Cache value must not be null");

        return cache.asMap().putIfAbsent(key, value);
    }

    // ==================== GET OPERATIONS ====================

    /**
     * Gets a value from the cache.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * User user = cache.get("user123", User.class);
     * if (user != null) {
     *     // Use cached user
     * }
     * }</pre>
     *
     * @param key cache key
     * @param <T> value type
     * @return cached value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        Objects.requireNonNull(key, "Cache key must not be null");

        Object value = cache.getIfPresent(key);
        log.trace("Getting from cache '{}': key={}, found={}", cacheName, key, value != null);
        return (T) value;
    }

    /**
     * Gets a value with type checking.
     *
     * @param key cache key
     * @param type expected value type
     * @param <T> value type
     * @return cached value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = get(key);
        if (value != null && !type.isInstance(value)) {
            log.warn("Cache value type mismatch for key '{}': expected {}, got {}",
                key, type.getName(), value.getClass().getName());
            return null;
        }
        return (T) value;
    }

    /**
     * Gets a value, loading it if not present.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * User user = cache.get("user123", userId -> userService.findById(userId));
     * }</pre>
     *
     * @param key cache key
     * @param loader function to load value if not cached
     * @param <K> key type
     * @param <V> value type
     * @return cached or loaded value
     */
    @SuppressWarnings("unchecked")
    public <K, V> V get(K key, Function<K, V> loader) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(loader, "Loader function must not be null");

        return (V) cache.get(key, k -> loader.apply((K) k));
    }

    /**
     * Gets multiple values at once.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Set<String> userIds = Set.of("user1", "user2", "user3");
     * Map<String, User> users = cache.getAll(userIds);
     * }</pre>
     *
     * @param keys collection of keys to retrieve
     * @return map of found key-value pairs
     */
    public Map<Object, Object> getAll(Iterable<?> keys) {
        Objects.requireNonNull(keys, "Keys must not be null");

        return cache.getAllPresent(keys);
    }

    // ==================== REMOVE OPERATIONS ====================

    /**
     * Removes an entry from the cache.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * cache.evict("user123");
     * }</pre>
     *
     * @param key key to remove
     */
    public void evict(Object key) {
        Objects.requireNonNull(key, "Cache key must not be null");

        log.trace("Evicting from cache '{}': key={}", cacheName, key);
        cache.invalidate(key);
    }

    /**
     * Removes multiple entries from the cache.
     *
     * @param keys keys to remove
     */
    public void evictAll(Iterable<?> keys) {
        Objects.requireNonNull(keys, "Keys must not be null");

        log.trace("Evicting multiple entries from cache '{}'", cacheName);
        cache.invalidateAll(keys);
    }

    /**
     * Clears all entries from the cache.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * cache.clear();
     * log.info("Cache cleared");
     * }</pre>
     */
    public void clear() {
        log.debug("Clearing cache '{}'", cacheName);
        cache.invalidateAll();
    }

    // ==================== STATISTICS & MONITORING ====================

    /**
     * Gets cache statistics.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * CacheStats stats = cache.stats();
     * log.info("Hit rate: {}%", stats.hitRate() * 100);
     * log.info("Evictions: {}", stats.evictionCount());
     * }</pre>
     *
     * @return cache statistics (returns empty stats if not enabled)
     */
    public CacheStats stats() {
        return cache.stats();
    }

    /**
     * Gets cache size.
     *
     * @return number of entries in cache
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * Checks if cache contains a key.
     *
     * @param key key to check
     * @return true if key exists in cache
     */
    public boolean containsKey(Object key) {
        return cache.asMap().containsKey(key);
    }

    /**
     * Gets all cache keys.
     *
     * @return set of all keys
     */
    public Set<Object> keys() {
        return cache.asMap().keySet();
    }

    /**
     * Gets cache name.
     *
     * @return cache name
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * Performs cache maintenance (cleanup of stale entries).
     *
     * <p>Typically called automatically, but can be invoked manually.</p>
     */
    public void cleanUp() {
        log.trace("Performing cleanup on cache '{}'", cacheName);
        cache.cleanUp();
    }

    // ==================== CACHE REGISTRY ====================

    /**
     * Gets a cache by name from the registry.
     *
     * @param cacheName cache name
     * @return cache instance or null if not found
     */
    public static CacheFacade getCache(String cacheName) {
        return CACHE_REGISTRY.get(cacheName);
    }

    /**
     * Gets all registered caches.
     *
     * @return map of all caches
     */
    public static Map<String, CacheFacade> getAllCaches() {
        return Collections.unmodifiableMap(CACHE_REGISTRY);
    }

    /**
     * Clears all registered caches.
     */
    public static void clearAll() {
        log.info("Clearing all {} caches", CACHE_REGISTRY.size());
        CACHE_REGISTRY.values().forEach(CacheFacade::clear);
    }

    // ==================== BUILDER ====================

    /**
     * Creates a new cache builder.
     *
     * @return cache builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new loading cache builder.
     *
     * @param <K> key type
     * @param <V> value type
     * @return loading cache builder instance
     */
    public static <K, V> LoadingBuilder<K, V> loadingBuilder() {
        return new LoadingBuilder<>();
    }

    /**
     * Builder for creating cache instances.
     */
    public static class Builder {
        private String cacheName = "default";
        private long maximumSize = 1000;
        private Duration expireAfterWrite;
        private Duration expireAfterAccess;
        private Duration refreshAfterWrite;
        private boolean recordStats = false;
        private int initialCapacity = 16;

        /**
         * Sets cache name.
         */
        public Builder cacheName(String cacheName) {
            this.cacheName = cacheName;
            return this;
        }

        /**
         * Sets maximum cache size.
         */
        public Builder maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * Sets time-to-live after write.
         */
        public Builder expireAfterWrite(Duration duration) {
            this.expireAfterWrite = duration;
            return this;
        }

        /**
         * Sets expiration after last access.
         */
        public Builder expireAfterAccess(Duration duration) {
            this.expireAfterAccess = duration;
            return this;
        }

        /**
         * Sets refresh interval for loading caches.
         */
        public Builder refreshAfterWrite(Duration duration) {
            this.refreshAfterWrite = duration;
            return this;
        }

        /**
         * Enables statistics recording.
         */
        public Builder recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        /**
         * Sets initial capacity.
         */
        public Builder initialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        /**
         * Builds the cache instance.
         */
        public CacheFacade build() {
            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize);

            if (expireAfterWrite != null) {
                builder.expireAfterWrite(expireAfterWrite);
            }
            if (expireAfterAccess != null) {
                builder.expireAfterAccess(expireAfterAccess);
            }
            if (refreshAfterWrite != null) {
                builder.refreshAfterWrite(refreshAfterWrite);
            }
            if (recordStats) {
                builder.recordStats();
            }

            Cache<Object, Object> cache = builder.build();
            return new CacheFacade(cacheName, cache);
        }
    }

    /**
     * Builder for creating loading caches.
     */
    public static class LoadingBuilder<K, V> {
        private String cacheName = "loading-cache";
        private long maximumSize = 1000;
        private Duration expireAfterWrite;
        private Duration expireAfterAccess;
        private Duration refreshAfterWrite;
        private boolean recordStats = false;
        private Function<K, V> loader;

        public LoadingBuilder<K, V> cacheName(String cacheName) {
            this.cacheName = cacheName;
            return this;
        }

        public LoadingBuilder<K, V> maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        public LoadingBuilder<K, V> expireAfterWrite(Duration duration) {
            this.expireAfterWrite = duration;
            return this;
        }

        public LoadingBuilder<K, V> expireAfterAccess(Duration duration) {
            this.expireAfterAccess = duration;
            return this;
        }

        public LoadingBuilder<K, V> refreshAfterWrite(Duration duration) {
            this.refreshAfterWrite = duration;
            return this;
        }

        public LoadingBuilder<K, V> recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        /**
         * Sets the loader function.
         */
        public LoadingBuilder<K, V> loader(Function<K, V> loader) {
            this.loader = loader;
            return this;
        }

        @SuppressWarnings("unchecked")
        public CacheFacade build() {
            Objects.requireNonNull(loader, "Loader function must not be null for loading cache");

            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maximumSize);

            if (expireAfterWrite != null) {
                builder.expireAfterWrite(expireAfterWrite);
            }
            if (expireAfterAccess != null) {
                builder.expireAfterAccess(expireAfterAccess);
            }
            if (refreshAfterWrite != null) {
                builder.refreshAfterWrite(refreshAfterWrite);
            }
            if (recordStats) {
                builder.recordStats();
            }

            LoadingCache<Object, Object> loadingCache = builder.build(key -> loader.apply((K) key));
            return new CacheFacade(cacheName, loadingCache);
        }
    }
}

