package com.adhar.adharkit.cache.helidon;

import com.adhar.adharkit.cache.api.CacheService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Helidon-specific adapter for caching using Caffeine.
 *
 * <p>This adapter provides a standalone caching implementation for Helidon SE and MP
 * applications. It uses Caffeine as the underlying cache library, which is a
 * high-performance, near-optimal caching library for Java.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Standalone:</b> No DI annotations; compatible with Helidon SE (no container)</li>
 *   <li><b>Caffeine:</b> High-performance local caching with configurable eviction</li>
 *   <li><b>Named Caches:</b> Supports multiple named caches with automatic creation</li>
 *   <li><b>TTL Support:</b> Per-entry time-to-live via separate cache instances</li>
 *   <li><b>Thread Safe:</b> Caffeine caches are inherently thread-safe</li>
 * </ul>
 *
 * <p><b>Usage with Helidon SE:</b></p>
 * <pre>{@code
 * HelidonCacheAdapter cache = new HelidonCacheAdapter();
 *
 * // Simple put/get
 * cache.put("users", "user-123", userObject);
 * Optional<User> user = cache.get("users", "user-123", User.class);
 *
 * // Cache-aside pattern
 * User result = cache.getOrCompute("users", "user-456", User.class,
 *     () -> userRepository.findById("user-456"));
 * }</pre>
 *
 * <p><b>Custom Configuration:</b></p>
 * <pre>{@code
 * HelidonCacheAdapter cache = new HelidonCacheAdapter(1000, Duration.ofMinutes(30));
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Caffeine caches handle concurrent access internally,
 * and the cache registry uses {@link ConcurrentHashMap}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see CacheService
 * @see FrameworkAdapter
 */
public class HelidonCacheAdapter implements FrameworkAdapter<CacheService>, CacheService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(HelidonCacheAdapter.class);

    /**
     * Registry of named caches, lazily created on first access.
     */
    private final ConcurrentMap<String, Cache<Object, Object>> caches = new ConcurrentHashMap<>();

    /**
     * Default maximum cache size.
     */
    private final long defaultMaxSize;

    /**
     * Default expiration duration after write.
     */
    private final Duration defaultExpireAfterWrite;

    /**
     * Constructs a HelidonCacheAdapter with custom defaults.
     *
     * @param defaultMaxSize the default maximum number of entries per cache
     * @param defaultExpireAfterWrite the default expiration duration after write
     */
    public HelidonCacheAdapter(long defaultMaxSize, Duration defaultExpireAfterWrite) {
        this.defaultMaxSize = defaultMaxSize;
        this.defaultExpireAfterWrite = defaultExpireAfterWrite;
        log.debug("Initialized HelidonCacheAdapter with maxSize={}, expireAfterWrite={}",
                defaultMaxSize, defaultExpireAfterWrite);
    }

    /**
     * Constructs a HelidonCacheAdapter with sensible defaults.
     * <p>
     * Default maximum size is 10,000 entries with a 10-minute expiration.
     * </p>
     */
    public HelidonCacheAdapter() {
        this(10_000, Duration.ofMinutes(10));
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.HELIDON;
    }

    @Override
    public CacheService getService() {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        try {
            Cache<Object, Object> cache = getOrCreateCache(cacheName);
            Object value = cache.getIfPresent(key);
            return Optional.ofNullable((T) value);
        } catch (Exception e) {
            log.error("Error retrieving from cache '{}'", cacheName, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        try {
            Cache<Object, Object> cache = getOrCreateCache(cacheName);
            cache.put(key, value);
            log.debug("Cached value for key '{}' in cache '{}'", key, cacheName);
        } catch (Exception e) {
            log.error("Error putting value in cache '{}'", cacheName, e);
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        try {
            // Create or get a TTL-specific cache for this entry
            String ttlCacheName = cacheName + "__ttl_" + ttl.toMillis();
            Cache<Object, Object> cache = caches.computeIfAbsent(ttlCacheName, name ->
                    Caffeine.newBuilder()
                            .maximumSize(defaultMaxSize)
                            .expireAfterWrite(ttl)
                            .build()
            );
            cache.put(key, value);
            // Also put in main cache for lookup
            getOrCreateCache(cacheName).put(key, value);
            log.debug("Cached value for key '{}' in cache '{}' with TTL {}", key, cacheName, ttl);
        } catch (Exception e) {
            log.error("Error putting value in cache '{}'", cacheName, e);
        }
    }

    @Override
    public boolean putIfAbsent(String cacheName, Object key, Object value) {
        Cache<Object, Object> cache = getOrCreateCache(cacheName);
        Object existing = cache.getIfPresent(key);
        if (existing != null) {
            return false;
        }
        cache.put(key, value);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String cacheName, Object key, Class<T> type, Callable<T> valueLoader) {
        try {
            Cache<Object, Object> cache = getOrCreateCache(cacheName);
            Object value = cache.get(key, k -> {
                try {
                    return valueLoader.call();
                } catch (Exception e) {
                    log.error("Error computing value for key '{}'", key, e);
                    throw new RuntimeException("Failed to compute value", e);
                }
            });
            return (T) value;
        } catch (Exception e) {
            log.error("Failed to get or compute value for key '{}'", key, e);
            throw new RuntimeException("Failed to get or compute value", e);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        try {
            Cache<Object, Object> cache = getOrCreateCache(cacheName);
            cache.invalidate(key);
            log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
        } catch (Exception e) {
            log.error("Error evicting key from cache '{}'", cacheName, e);
        }
    }

    @Override
    public void clear(String cacheName) {
        try {
            Cache<Object, Object> cache = getOrCreateCache(cacheName);
            cache.invalidateAll();
            log.info("Cleared cache '{}'", cacheName);
        } catch (Exception e) {
            log.error("Error clearing cache '{}'", cacheName, e);
        }
    }

    @Override
    public boolean contains(String cacheName, Object key) {
        Cache<Object, Object> cache = getOrCreateCache(cacheName);
        return cache.getIfPresent(key) != null;
    }

    @Override
    public long size(String cacheName) {
        Cache<Object, Object> cache = getOrCreateCache(cacheName);
        cache.cleanUp();
        return cache.estimatedSize();
    }

    /**
     * Gets or creates a named Caffeine cache with default configuration.
     *
     * @param cacheName the name of the cache
     * @return the Caffeine cache instance
     */
    private Cache<Object, Object> getOrCreateCache(String cacheName) {
        return caches.computeIfAbsent(cacheName, name ->
                Caffeine.newBuilder()
                        .maximumSize(defaultMaxSize)
                        .expireAfterWrite(defaultExpireAfterWrite)
                        .build()
        );
    }
}
