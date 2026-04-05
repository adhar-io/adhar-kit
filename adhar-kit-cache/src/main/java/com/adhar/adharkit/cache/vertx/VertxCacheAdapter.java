package com.adhar.adharkit.cache.vertx;

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
 * Vert.x adapter for caching using Caffeine.
 *
 * <p>This adapter provides a standalone caching implementation for Vert.x applications
 * using Caffeine as the local cache engine. Since Vert.x does not have a built-in
 * dependency injection container or cache manager, this adapter manages Caffeine
 * cache instances directly via an internal cache registry.</p>
 *
 * <p>Caffeine is a high-performance, near-optimal caching library that is well-suited
 * for Vert.x's non-blocking architecture as its operations are lock-free and
 * thread-safe.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>No DI Required:</b> Standalone initialization, no annotations needed</li>
 *   <li><b>Caffeine Backed:</b> High-performance local caching with automatic eviction</li>
 *   <li><b>Dynamic Caches:</b> Caches are created on demand with configurable defaults</li>
 *   <li><b>Per-Entry TTL:</b> Supports per-entry TTL via separate cache instances</li>
 *   <li><b>Thread-Safe:</b> Safe for use across Vert.x event loop and worker threads</li>
 *   <li><b>Cache-Aside:</b> Built-in support for the cache-aside pattern via getOrCompute</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * VertxCacheAdapter cache = new VertxCacheAdapter();
 *
 * // Or with custom defaults
 * VertxCacheAdapter cache = new VertxCacheAdapter(10_000, Duration.ofMinutes(30));
 *
 * // Use in a Verticle
 * public class UserVerticle extends AbstractVerticle {
 *     private final VertxCacheAdapter cache = new VertxCacheAdapter();
 *
 *     public void start() {
 *         vertx.eventBus().consumer("users.get", msg -> {
 *             String userId = (String) msg.body();
 *             User user = cache.getOrCompute("users", userId, User.class,
 *                 () -> userRepository.findById(userId));
 *             msg.reply(user);
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Caffeine caches are inherently thread-safe and
 * the internal cache registry uses {@link ConcurrentHashMap}. It is safe to use
 * this adapter from any Vert.x event loop or worker thread.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see CacheService
 * @see FrameworkAdapter
 */
public class VertxCacheAdapter implements FrameworkAdapter<CacheService>, CacheService {

    private static final Logger log = LoggerFactory.getLogger(VertxCacheAdapter.class);

    private static final long DEFAULT_MAX_SIZE = 10_000;
    private static final Duration DEFAULT_EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);

    private final ConcurrentMap<String, Cache<Object, Object>> cacheRegistry = new ConcurrentHashMap<>();
    private final long defaultMaxSize;
    private final Duration defaultExpireAfterWrite;

    /**
     * Constructs a new Vert.x cache adapter with default settings.
     *
     * <p>Caches are created on demand with a maximum size of 10,000 entries
     * and a default TTL of 10 minutes.</p>
     */
    public VertxCacheAdapter() {
        this(DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_AFTER_WRITE);
    }

    /**
     * Constructs a new Vert.x cache adapter with custom default settings.
     *
     * @param defaultMaxSize the default maximum number of entries per cache
     * @param defaultExpireAfterWrite the default TTL for cache entries
     */
    public VertxCacheAdapter(long defaultMaxSize, Duration defaultExpireAfterWrite) {
        this.defaultMaxSize = defaultMaxSize;
        this.defaultExpireAfterWrite = defaultExpireAfterWrite;
        log.info("Initialized Vert.x Cache adapter (maxSize={}, expireAfterWrite={})",
                defaultMaxSize, defaultExpireAfterWrite);
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.VERTX;
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
            // Create or get a TTL-specific cache variant
            String ttlCacheName = cacheName + "_ttl_" + ttl.toMillis();
            Cache<Object, Object> cache = cacheRegistry.computeIfAbsent(ttlCacheName,
                    name -> Caffeine.newBuilder()
                            .maximumSize(defaultMaxSize)
                            .expireAfterWrite(ttl)
                            .build());
            cache.put(key, value);
            log.debug("Cached value for key '{}' in cache '{}' with TTL {}", key, cacheName, ttl);
        } catch (Exception e) {
            log.error("Error putting value in cache '{}' with TTL", cacheName, e);
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
                    throw new RuntimeException("Failed to compute cache value", e);
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
            Cache<Object, Object> cache = cacheRegistry.get(cacheName);
            if (cache != null) {
                cache.invalidate(key);
                log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
            } else {
                log.warn("Cache '{}' not found, cannot evict", cacheName);
            }
        } catch (Exception e) {
            log.error("Error evicting key from cache '{}'", cacheName, e);
        }
    }

    @Override
    public void clear(String cacheName) {
        try {
            Cache<Object, Object> cache = cacheRegistry.get(cacheName);
            if (cache != null) {
                cache.invalidateAll();
                log.info("Cleared cache '{}'", cacheName);
            } else {
                log.warn("Cache '{}' not found, cannot clear", cacheName);
            }
        } catch (Exception e) {
            log.error("Error clearing cache '{}'", cacheName, e);
        }
    }

    @Override
    public boolean contains(String cacheName, Object key) {
        Cache<Object, Object> cache = cacheRegistry.get(cacheName);
        if (cache == null) {
            return false;
        }
        return cache.getIfPresent(key) != null;
    }

    @Override
    public long size(String cacheName) {
        Cache<Object, Object> cache = cacheRegistry.get(cacheName);
        if (cache == null) {
            return -1;
        }
        return cache.estimatedSize();
    }

    private Cache<Object, Object> getOrCreateCache(String cacheName) {
        return cacheRegistry.computeIfAbsent(cacheName,
                name -> Caffeine.newBuilder()
                        .maximumSize(defaultMaxSize)
                        .expireAfterWrite(defaultExpireAfterWrite)
                        .build());
    }
}
