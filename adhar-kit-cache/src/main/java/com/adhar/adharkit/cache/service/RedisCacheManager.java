package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.ClassUtils;

import java.util.Collection;

/**
 * A cache manager that stores entries in Redis (or Valkey), shared by every
 * instance of the application.
 * <p>
 * Wraps Spring Data Redis' {@code RedisCacheManager} with the module's defaults:
 * {@code adhar.cache.time-to-live} as the entry TTL, {@code adhar.cache.redis.key-prefix}
 * in front of every key, and {@code adhar.cache.allow-null-values}.
 * <p>
 * Values are stored as JSON carrying their type, so a cache hit returns the same
 * class that was cached — records and {@code java.time} values included — and the
 * entries stay readable with {@code redis-cli}. The type information is trusted on
 * read: anything able to write to this Redis can choose the class a cache read
 * deserializes into, so the store must be one the application alone writes to.
 * Without Jackson 3 on the classpath, values fall back to JDK serialization and
 * must be {@link java.io.Serializable}.
 */
@Slf4j
public class RedisCacheManager implements CacheManager {

    private static final boolean JACKSON_3_PRESENT =
            ClassUtils.isPresent("tools.jackson.databind.json.JsonMapper", RedisCacheManager.class.getClassLoader());

    private final org.springframework.data.redis.cache.RedisCacheManager internalCacheManager;

    /**
     * Creates a new RedisCacheManager.
     *
     * @param connectionFactory the Redis connection factory
     * @param properties        the cache properties
     */
    public RedisCacheManager(RedisConnectionFactory connectionFactory, AdharCacheProperties properties) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getTimeToLive())
                .prefixCacheNameWith(properties.getRedis().getKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer()));

        if (!properties.isAllowNullValues()) {
            defaultCacheConfig = defaultCacheConfig.disableCachingNullValues();
        }

        // Spring's RedisCacheManager is an InitializingBean; built here rather than
        // registered, so initialize it explicitly.
        this.internalCacheManager = RedisCacheManagerBuilder.fromConnectionFactory(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .build();
        this.internalCacheManager.afterPropertiesSet();

        log.info("Initialized RedisCacheManager (ttl={}, keyPrefix='{}', serializer={})",
                properties.getTimeToLive(), properties.getRedis().getKeyPrefix(),
                JACKSON_3_PRESENT ? "json" : "jdk");
    }

    /**
     * Creates a new RedisCacheManager.
     *
     * @param connectionFactory the Redis connection factory
     * @param properties        the cache properties
     * @param objectMapper      ignored
     * @deprecated the object mapper was used without type information, so a cache
     *     hit came back as a {@code LinkedHashMap} instead of the cached class. Use
     *     {@link #RedisCacheManager(RedisConnectionFactory, AdharCacheProperties)}.
     */
    @Deprecated(since = "0.1.1", forRemoval = true)
    public RedisCacheManager(RedisConnectionFactory connectionFactory, AdharCacheProperties properties,
                             ObjectMapper objectMapper) {
        this(connectionFactory, properties);
    }

    @Override
    public Cache getCache(String name) {
        return internalCacheManager.getCache(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return internalCacheManager.getCacheNames();
    }

    private static RedisSerializer<Object> valueSerializer() {
        return JACKSON_3_PRESENT ? JsonValues.serializer() : RedisSerializer.java();
    }

    /** Isolated so Jackson 3 types are loaded only when Jackson 3 is present. */
    private static final class JsonValues {
        static RedisSerializer<Object> serializer() {
            return GenericJacksonJsonRedisSerializer.builder()
                    .enableSpringCacheNullValueSupport()
                    .enableUnsafeDefaultTyping()
                    .build();
        }
    }
}
