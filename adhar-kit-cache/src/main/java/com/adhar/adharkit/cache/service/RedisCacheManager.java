package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache manager implementation that uses Redis for distributed cache storage.
 * <p>
 * This cache manager wraps Spring's RedisCacheManager and provides additional
 * functionality for configuring Redis caches with sensible defaults and custom
 * serialization.
 * <p>
 * The cache manager uses Jackson for serialization/deserialization of cache values,
 * which allows for storing complex objects in the cache.
 */
@Slf4j
public class RedisCacheManager implements CacheManager {

    private final org.springframework.data.redis.cache.RedisCacheManager internalCacheManager;
    private final AdharCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    /**
     * Creates a new RedisCacheManager.
     *
     * @param connectionFactory the Redis connection factory
     * @param properties        the cache properties
     * @param objectMapper      the object mapper for serialization/deserialization
     */
    public RedisCacheManager(
            RedisConnectionFactory connectionFactory,
            AdharCacheProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        // Create a JSON serializer for cache values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Create the default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getTimeToLive())
                .prefixCacheNameWith(properties.getRedis().getKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // Configure null value handling
        if (!properties.isAllowNullValues()) {
            defaultCacheConfig = defaultCacheConfig.disableCachingNullValues();
        }

        // Create the internal cache manager
        this.internalCacheManager = RedisCacheManagerBuilder.fromConnectionFactory(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .build();

        log.info("Initialized RedisCacheManager with Redis at {}:{}", 
                properties.getRedis().getHost(), properties.getRedis().getPort());
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, cacheName -> {
            org.springframework.cache.Cache internalCache = internalCacheManager.getCache(cacheName);
            return new RedisCache(internalCache, cacheName);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return internalCacheManager.getCacheNames();
    }

    /**
     * A cache implementation that wraps a Redis cache.
     */
    private class RedisCache implements Cache {
        private final Cache internalCache;
        private final String name;

        /**
         * Creates a new RedisCache.
         *
         * @param internalCache the internal cache
         * @param name          the cache name
         */
        public RedisCache(Cache internalCache, String name) {
            this.internalCache = internalCache;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return internalCache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return internalCache.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return internalCache.get(key, type);
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            return internalCache.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            internalCache.put(key, value);
        }

        @Override
        public void evict(Object key) {
            internalCache.evict(key);
        }

        @Override
        public void clear() {
            internalCache.clear();
        }

        /**
         * Puts a value in the cache with a custom time-to-live.
         *
         * @param key        the key
         * @param value      the value
         * @param timeToLive the time-to-live
         */
        public void put(Object key, Object value, Duration timeToLive) {
            // For Redis, we need to create a new cache with the custom TTL
            if (internalCache instanceof org.springframework.data.redis.cache.RedisCache) {
                org.springframework.data.redis.cache.RedisCache redisCache = 
                        (org.springframework.data.redis.cache.RedisCache) internalCache;

                // Get the current configuration and update the TTL
                RedisCacheConfiguration config = redisCache.getCacheConfiguration().entryTtl(timeToLive);

                // Create a new cache with the custom configuration
                org.springframework.data.redis.cache.RedisCache customCache = 
                        (org.springframework.data.redis.cache.RedisCache) internalCacheManager.getCache(name + ":" + timeToLive.toMillis());

                if (customCache != null) {
                    customCache.put(key, value);
                } else {
                    // Fall back to the default cache
                    internalCache.put(key, value);
                }
            } else {
                // Fall back to the default cache
                internalCache.put(key, value);
            }
        }
    }
}
