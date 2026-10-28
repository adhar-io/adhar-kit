package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.adharkit.cache.service.RedisCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.time.Duration;

/**
 * Auto-configuration for the Redis (Valkey) backed cache.
 * <p>
 * Registers {@link RedisCacheManager} as the application's Spring
 * {@link org.springframework.cache.CacheManager} whenever a Redis connection is
 * available, so {@code @Cacheable} and the module's multi-level L2 store entries in
 * Redis shared by every instance.
 * <p>
 * The connection is Spring Boot's own ({@code spring.data.redis.*}, e.g.
 * {@code spring.data.redis.url}) — this runs after Boot's Redis auto-configuration
 * and reuses it. {@code adhar.cache.redis.host/port/...} build a connection only
 * when the application has none. It runs before Boot's cache auto-configuration so
 * Boot's default cache manager backs off in favour of this one.
 * <p>
 * Disable with {@code adhar.cache.redis.enabled=false}; an application-defined
 * {@code CacheManager} always wins.
 */
@Slf4j
@AutoConfiguration(
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@EnableConfigurationProperties(AdharCacheProperties.class)
@ConditionalOnClass({RedisConnectionFactory.class, org.springframework.data.redis.cache.RedisCacheManager.class})
@ConditionalOnProperty(prefix = "adhar.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "adhar.cache.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(RedisConfig.NotDaprProvider.class)
public class RedisConfig {

    /**
     * Steps aside for {@code adhar.cache.provider=dapr}: the caches then live behind the sidecar
     * and a Redis connection of the service's own (even a fallback one) would be unused.
     */
    static class NotDaprProvider extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = context.getEnvironment().getProperty("adhar.cache.provider", "auto");
            return "dapr".equalsIgnoreCase(provider.trim())
                    ? ConditionOutcome.noMatch("adhar.cache.provider=dapr")
                    : ConditionOutcome.match("adhar.cache.provider=" + provider);
        }
    }

    /**
     * Fallback connection from {@code adhar.cache.redis.*}, created only when the
     * application has no Redis connection of its own.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.lettuce.core.RedisClient")
    static class FallbackConnectionConfiguration {

        @Bean
        @ConditionalOnMissingBean(RedisConnectionFactory.class)
        LettuceConnectionFactory adharCacheRedisConnectionFactory(AdharCacheProperties properties) {
            AdharCacheProperties.RedisProperties redis = properties.getRedis();
            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redis.getHost(), redis.getPort());
            if (redis.getPassword() != null) {
                redisConfig.setPassword(redis.getPassword());
            }
            redisConfig.setDatabase(redis.getDatabase());

            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                    .commandTimeout(redis.getReadTimeout())
                    .shutdownTimeout(Duration.ofMillis(100));
            if (redis.isSsl()) {
                builder.useSsl();
            }
            log.info("No Redis connection configured by the application; connecting to {}:{} from adhar.cache.redis.*",
                    redis.getHost(), redis.getPort());
            return new LettuceConnectionFactory(redisConfig, builder.build());
        }
    }

    /**
     * The Redis-backed cache manager.
     *
     * @param connectionFactory the Redis connection factory
     * @param properties        the cache properties
     * @return the cache manager
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, AdharCacheProperties properties) {
        return new RedisCacheManager(connectionFactory, properties);
    }
}
