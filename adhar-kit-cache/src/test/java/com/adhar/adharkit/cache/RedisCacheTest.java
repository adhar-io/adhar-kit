package com.adhar.adharkit.cache;

import com.adhar.adharkit.cache.config.RedisConfig;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis-based caching.
 * <p>
 * This test class uses Testcontainers to start a Redis container for testing.
 */
@SpringBootTest
@Testcontainers
@EnableCaching
public class RedisCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private CacheManager cacheManager;

    /**
     * Dynamically sets the Redis connection properties for the test.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("adhar.cache.redis.host", redis::getHost);
        registry.add("adhar.cache.redis.port", redis::getFirstMappedPort);
    }

    /**
     * Test configuration for Redis cache.
     */
    @Configuration
    @ImportAutoConfiguration(RedisConfig.class)
    static class TestConfig {
        @Bean
        public AdharCacheProperties adharCacheProperties() {
            return new AdharCacheProperties();
        }
    }

    @Test
    void shouldPutAndGetFromCache() {
        // Given
        String cacheName = "testCache";
        String key = "testKey";
        String value = "testValue-" + UUID.randomUUID();

        // When
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key, value);

        // Then
        assertThat(cache.get(key, String.class)).isEqualTo(value);
    }

    @Test
    void shouldEvictFromCache() {
        // Given
        String cacheName = "testCache";
        String key = "testKey";
        String value = "testValue-" + UUID.randomUUID();

        // When
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key, value);
        cache.evict(key);

        // Then
        assertThat(cache.get(key)).isNull();
    }

    @Test
    void shouldClearCache() {
        // Given
        String cacheName = "testCache";
        String key1 = "testKey1";
        String key2 = "testKey2";
        String value = "testValue-" + UUID.randomUUID();

        // When
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key1, value);
        cache.put(key2, value);
        cache.clear();

        // Then
        assertThat(cache.get(key1)).isNull();
        assertThat(cache.get(key2)).isNull();
    }
}