package com.adhar.adharkit.cache.quarkus;

import com.adhar.adharkit.cache.api.CacheService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.quarkus.cache.CacheManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Quarkus implementation of Cache Service.
 *
 * <p>This adapter integrates with Quarkus Cache extension which supports
 * Caffeine for local caching and Redis for distributed caching.</p>
 *
 * <p><b>Quarkus Configuration Example (application.properties):</b></p>
 * <pre>
 * quarkus.cache.enabled=true
 * quarkus.cache.type=caffeine
 *
 * # Cache-specific settings
 * quarkus.cache.caffeine."users".initial-capacity=100
 * quarkus.cache.caffeine."users".maximum-size=1000
 * quarkus.cache.caffeine."users".expire-after-write=10M
 * </pre>
 *
 * <p><b>Usage with Quarkus Cache annotations:</b></p>
 * <pre>{@code
 * @CacheResult(cacheName = "users")
 * public User findUser(String userId) {
 *     return userRepository.find(userId);
 * }
 *
 * @CacheInvalidate(cacheName = "users")
 * public void updateUser(String userId, User user) {
 *     userRepository.update(userId, user);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@ApplicationScoped
public class QuarkusCacheAdapter implements FrameworkAdapter<CacheService>, CacheService {

    @Inject
    CacheManager cacheManager;

    @Override
    public Framework getSupportedFramework() {
        return Framework.QUARKUS;
    }

    @Override
    public CacheService getService() {
        return this;
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        try {
            io.quarkus.cache.Cache cache = cacheManager.getCache(cacheName).orElse(null);
            if (cache == null) {
                log.warn("Cache '{}' not found", cacheName);
                return Optional.empty();
            }

            // Quarkus cache returns CompletableFuture
            CompletableFuture<T> future = cache.get(key, k -> null);
            T value = future.join();
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.error("Error retrieving from cache '{}'", cacheName, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        try {
            io.quarkus.cache.Cache cache = cacheManager.getCache(cacheName).orElse(null);
            if (cache == null) {
                log.warn("Cache '{}' not found, cannot put value", cacheName);
                return;
            }

            // Quarkus doesn't have explicit put, we use get with value loader
            cache.get(key, k -> CompletableFuture.completedFuture(value)).join();
            log.debug("Cached value for key '{}' in cache '{}'", key, cacheName);
        } catch (Exception e) {
            log.error("Error putting value in cache '{}'", cacheName, e);
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        // Quarkus Cache TTL is configured globally, not per-entry
        log.debug("Per-entry TTL not supported in Quarkus Cache, using configured TTL");
        put(cacheName, key, value);
    }

    @Override
    public boolean putIfAbsent(String cacheName, Object key, Object value) {
        // Check if exists first
        Optional<?> existing = get(cacheName, key, Object.class);
        if (existing.isPresent()) {
            return false;
        }

        put(cacheName, key, value);
        return true;
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Class<T> type, Callable<T> valueLoader) {
        try {
            io.quarkus.cache.Cache cache = cacheManager.getCache(cacheName).orElse(null);
            if (cache == null) {
                log.warn("Cache '{}' not found, computing value directly", cacheName);
                return valueLoader.call();
            }

            // Quarkus cache handles the cache-aside pattern natively
            CompletableFuture<T> future = cache.get(key, k -> {
                try {
                    return CompletableFuture.completedFuture(valueLoader.call());
                } catch (Exception e) {
                    log.error("Error computing value for key '{}'", key, e);
                    return CompletableFuture.failedFuture(e);
                }
            });

            return future.join();
        } catch (Exception e) {
            log.error("Failed to get or compute value for key '{}'", key, e);
            throw new RuntimeException("Failed to get or compute value", e);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        try {
            io.quarkus.cache.Cache cache = cacheManager.getCache(cacheName).orElse(null);
            if (cache == null) {
                log.warn("Cache '{}' not found, cannot evict", cacheName);
                return;
            }

            cache.invalidate(key).await().indefinitely();
            log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
        } catch (Exception e) {
            log.error("Error evicting key from cache '{}'", cacheName, e);
        }
    }

    @Override
    public void clear(String cacheName) {
        try {
            io.quarkus.cache.Cache cache = cacheManager.getCache(cacheName).orElse(null);
            if (cache == null) {
                log.warn("Cache '{}' not found, cannot clear", cacheName);
                return;
            }

            cache.invalidateAll().await().indefinitely();
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
        // Quarkus Cache doesn't expose size in the standard API
        log.debug("Size operation not supported in Quarkus Cache");
        return -1;
    }
}

