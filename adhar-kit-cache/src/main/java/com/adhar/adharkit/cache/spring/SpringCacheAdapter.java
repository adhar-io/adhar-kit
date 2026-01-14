package com.adhar.adharkit.cache.spring;

import com.adhar.adharkit.cache.api.CacheService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Spring Boot implementation of Cache Service.
 *
 * <p>This adapter integrates with Spring's caching abstraction and supports
 * various cache providers like Caffeine, Redis, Hazelcast, etc.</p>
 *
 * <p><b>Spring Boot Configuration Example:</b></p>
 * <pre>{@code
 * @Configuration
 * @EnableCaching
 * public class CacheConfig {
 *
 *     @Bean
 *     public CacheManager cacheManager() {
 *         return new CaffeineCacheManager("users", "orders");
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Service
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@RequiredArgsConstructor
public class SpringCacheAdapter implements FrameworkAdapter<CacheService>, CacheService {

    private final CacheManager cacheManager;

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    @Override
    public CacheService getService() {
        return this;
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found", cacheName);
            return Optional.empty();
        }

        T value = cache.get(key, type);
        return Optional.ofNullable(value);
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, cannot put value", cacheName);
            return;
        }

        cache.put(key, value);
        log.debug("Cached value for key '{}' in cache '{}'", key, cacheName);
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        // Spring Cache doesn't support per-entry TTL in the standard API
        // This would require Redis-specific implementation
        log.debug("TTL-based caching not supported in standard Spring Cache, using default TTL");
        put(cacheName, key, value);
    }

    @Override
    public boolean putIfAbsent(String cacheName, Object key, Object value) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, cannot put value", cacheName);
            return false;
        }

        // Check if already exists
        if (cache.get(key) != null) {
            return false;
        }

        cache.putIfAbsent(key, value);
        return true;
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Class<T> type, Callable<T> valueLoader) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, computing value directly", cacheName);
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute value", e);
            }
        }

        try {
            // Spring Cache's get method with valueLoader handles the cache-aside pattern
            return cache.get(key, valueLoader);
        } catch (Cache.ValueRetrievalException e) {
            log.error("Failed to retrieve or compute value for key '{}'", key, e);
            throw new RuntimeException("Failed to get or compute value", e);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, cannot evict", cacheName);
            return;
        }

        cache.evict(key);
        log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
    }

    @Override
    public void clear(String cacheName) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, cannot clear", cacheName);
            return;
        }

        cache.clear();
        log.info("Cleared cache '{}'", cacheName);
    }

    @Override
    public boolean contains(String cacheName, Object key) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            return false;
        }

        return cache.get(key) != null;
    }

    @Override
    public long size(String cacheName) {
        // Spring Cache doesn't have a standard size() method
        // Would require cache-specific implementation
        log.debug("Size operation not supported in standard Spring Cache");
        return -1;
    }

    /**
     * Helper method to get cache instance.
     *
     * @param cacheName the name of the cache
     * @return the Cache instance, or null if not found
     */
    private Cache getCache(String cacheName) {
        return cacheManager.getCache(cacheName);
    }
}

