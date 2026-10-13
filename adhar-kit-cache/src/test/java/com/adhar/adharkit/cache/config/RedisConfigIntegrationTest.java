package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.adharkit.cache.multilevel.SpringCacheSecondLevelCache;
import com.adhar.adharkit.cache.service.AdharCacheKafkaClients;
import com.adhar.adharkit.cache.service.RedisCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The Redis cache as an application gets it: Spring Boot's own Redis
 * auto-configuration supplies the connection from {@code spring.data.redis.url}
 * (as on the platform, where the URL comes from a Secret), against Valkey.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisConfigIntegrationTest {

    @Container
    static GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8"))
            .withExposedPorts(6379)
            // "Started" is not "ready": without this the first connection can reach
            // Docker's port proxy before the server is listening, and fails.
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    private ApplicationContextRunner runner(String keyPrefix) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class, RedisConfig.class, AdharCacheAutoConfiguration.class,
                        AdharCacheAspectsAutoConfiguration.class, CacheAutoConfiguration.class))
                .withUserConfiguration(CachingApp.class)
                .withPropertyValues(
                        "spring.data.redis.url=redis://" + valkey.getHost() + ":" + valkey.getFirstMappedPort(),
                        "adhar.cache.redis.key-prefix=" + keyPrefix);
    }

    @Test
    void usesSpringBootsConnectionAndBecomesTheCacheManager() {
        runner(prefix()).run(ctx -> {
            assertThat(ctx).hasSingleBean(RedisConnectionFactory.class)
                    .doesNotHaveBean("adharCacheRedisConnectionFactory");
            assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
        });
    }

    @Test
    void aCacheHitReturnsTheCachedType() {
        // The old serializer carried no type information, so the second call came
        // back as a LinkedHashMap and failed with ClassCastException.
        runner(prefix()).run(ctx -> {
            LookupService lookups = ctx.getBean(LookupService.class);
            Lookup first = lookups.find("a");
            awaitStored(ctx.getBean(CacheManager.class), "a");
            Lookup second = lookups.find("a");
            assertThat(second).isEqualTo(first);
            assertThat(lookups.calls()).isEqualTo(1);
        });
    }

    @Test
    void entriesAreSharedBetweenInstances() {
        String prefix = prefix();
        AtomicInteger firstInstanceCalls = new AtomicInteger();
        runner(prefix).run(ctx -> {
            ctx.getBean(LookupService.class).find("shared");
            awaitStored(ctx.getBean(CacheManager.class), "shared");
            firstInstanceCalls.set(ctx.getBean(LookupService.class).calls());
        });
        runner(prefix).run(ctx -> {
            LookupService lookups = ctx.getBean(LookupService.class);
            assertThat(lookups.find("shared").key()).isEqualTo("shared");
            assertThat(lookups.calls()).isZero();
        });
        assertThat(firstInstanceCalls).hasValue(1);
    }

    @Test
    void multiLevelL2IsTheRedisCache() {
        runner(prefix()).run(ctx -> assertThat(ctx.getBean(SecondLevelCache.class))
                .isInstanceOf(SpringCacheSecondLevelCache.class));
    }

    @Test
    void kafkaSynchronizationDoesNotReplaceTheSharedRedisCache() {
        runner(prefix()).withPropertyValues("adhar.cache.kafka.enabled=true").run(ctx -> {
            assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
            assertThat(ctx).doesNotHaveBean(AdharCacheKafkaClients.class);
        });
    }

    /** Redis cache writes are asynchronous (Spring's Cache contract allows it). */
    private static void awaitStored(CacheManager cacheManager, String key) {
        await().atMost(Duration.ofSeconds(5))
                .until(() -> cacheManager.getCache("lookups").get(key) != null);
    }

    private static String prefix() {
        return "test-" + UUID.randomUUID() + ":";
    }

    public record Lookup(String key, Instant computedAt) {
    }

    static class LookupService {
        // Read through calls(): this bean is a caching proxy, and a field read on the
        // proxy sees the proxy's own (unset) field.
        private final AtomicInteger calls = new AtomicInteger();

        public int calls() {
            return calls.get();
        }

        @Cacheable("lookups")
        public Lookup find(String key) {
            calls.incrementAndGet();
            return new Lookup(key, Instant.now());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingApp {
        @Bean
        LookupService lookupService() {
            return new LookupService();
        }
    }
}
