package com.adhar.adharkit.cache.micronaut;

import com.adhar.adharkit.cache.api.CacheService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Micronaut implementation of Cache Service.
 *
 * <p>This adapter integrates with Micronaut's caching abstraction which supports
 * Caffeine for local caching and Redis for distributed caching.</p>
 *
 * <p><b>Micronaut Configuration Example (application.yml):</b></p>
 * <pre>
 * micronaut:
 *   caches:
 *     users:
 *       maximum-size: 1000
 *       expire-after-write: 10m
 *     orders:
 *       maximum-size: 500
 *       expire-after-access: 5m
 * </pre>
 *
 * <p><b>Usage with Micronaut Cache annotations:</b></p>
 * <pre>{@code
 * @Cacheable("users")
 * public User findUser(String userId) {
 *     return userRepository.find(userId);
 * }
 *
 * @CacheInvalidate("users")
 * public void updateUser(String userId, User user) {
 *     userRepository.update(userId, user);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Singleton
@Requires(classes = io.micronaut.context.ApplicationContext.class)
public class MicronautCacheAdapter implements FrameworkAdapter<CacheService>, CacheService {

    private final CacheManager<SyncCache> cacheManager;

    public MicronautCacheAdapter(CacheManager<SyncCache> cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.MICRONAUT;
    }

    @Override
    public CacheService getService() {
        return this;
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        try {
            SyncCache cache = getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found", cacheName);
                return Optional.empty();
            }

            return cache.get(key, type);
        } catch (Exception e) {
            log.error("Error retrieving from cache '{}'", cacheName, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        try {
            SyncCache cache = getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, cannot put value", cacheName);
                return;
            }

            cache.put(key, value);
            log.debug("Cached value for key '{}' in cache '{}'", key, cacheName);
        } catch (Exception e) {
            log.error("Error putting value in cache '{}'", cacheName, e);
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        // Micronaut Cache TTL is configured globally, not per-entry
        log.debug("Per-entry TTL not supported in Micronaut Cache, using configured TTL");
        put(cacheName, key, value);
    }

    @Override
    public boolean putIfAbsent(String cacheName, Object key, Object value) {
        SyncCache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, cannot put value", cacheName);
            return false;
        }

        // Micronaut's putIfAbsent returns the previous value
        Optional<?> previous = cache.putIfAbsent(key, value);
        return previous.isEmpty();
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Class<T> type, Callable<T> valueLoader) {
        try {
            SyncCache cache = getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, computing value directly", cacheName);
                return valueLoader.call();
            }

            // Micronaut cache handles the cache-aside pattern natively
            return cache.get(key, type, valueLoader::call);
        } catch (Exception e) {
            log.error("Failed to get or compute value for key '{}'", key, e);
            throw new RuntimeException("Failed to get or compute value", e);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        try {
            SyncCache cache = getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, cannot evict", cacheName);
                return;
            }

            cache.invalidate(key);
            log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
        } catch (Exception e) {
            log.error("Error evicting key from cache '{}'", cacheName, e);
        }
    }

    @Override
    public void clear(String cacheName) {
        try {
            SyncCache cache = getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, cannot clear", cacheName);
                return;
            }

            cache.invalidateAll();
            log.info("Cleared cache '{}'", cacheName);
        } catch (Exception e) {
            log.error("Error clearing cache '{}'", cacheName, e);
        }
    }

    @Override
    public boolean contains(String cacheName, Object key) {
        Optional<?> value = get(cacheName, key, Object.class);
        return value.isPresent();
    }

    @Override
    public long size(String cacheName) {
        // Micronaut Cache doesn't expose size in the standard API
        log.debug("Size operation not supported in Micronaut Cache");
        return -1;
    }

    /**
     * Helper method to get cache instance.
     *
     * @param cacheName the name of the cache
     * @return the SyncCache instance, or null if not found
     */
    private SyncCache getCache(String cacheName) {
        return cacheManager.getCache(cacheName).orElse(null);
    }
}

