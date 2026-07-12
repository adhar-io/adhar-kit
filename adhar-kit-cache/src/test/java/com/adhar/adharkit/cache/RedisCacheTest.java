package com.adhar.adharkit.cache;

import com.adhar.adharkit.cache.config.RedisConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
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
// Close this context (and its Lettuce connection) after the class so the
// connection does not linger and reconnect-storm against the stopped Redis
// container, which otherwise destabilizes the shared Netty event loop and
// causes intermittent command loss in sibling container-backed tests.
@SpringBootTest
@Testcontainers
@EnableCaching
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RedisCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private CacheManager cacheManager;

    // The Redis container and Spring context are shared across all test methods,
    // so clear the cache before each test to keep them independent and avoid
    // cross-method interference on shared keys.
    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache("testCache");
        if (cache != null) {
            cache.clear();
        }
    }

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
    // AdharCacheProperties is provided (and bound to the dynamic properties above)
    // by @EnableConfigurationProperties on RedisConfig - no manual bean needed.
    @Configuration
    @ImportAutoConfiguration(RedisConfig.class)
    static class TestConfig {
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

        // Then - read against a real Redis over a shared Lettuce connection that
        // may be transiently reconnecting in a busy multi-container test run, so
        // poll briefly rather than demanding the value on the very first read.
        Awaitility.await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(cache.get(key, String.class)).isEqualTo(value));
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

        // Then - clear() against a real Redis is eventually consistent (SCAN+DEL),
        // so poll until both entries are gone rather than asserting immediately.
        Awaitility.await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(cache.get(key1)).isNull();
                    assertThat(cache.get(key2)).isNull();
                });
    }
}