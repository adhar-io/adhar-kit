package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.adharkit.cache.service.RedisCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuration for Redis-based caching.
 * <p>
 * This class provides configuration for Redis connection and cache management.
 * It is conditionally enabled based on the "adhar.cache.redis.enabled" property.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdharCacheProperties.class)
@ConditionalOnProperty(prefix = "adhar.cache.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    /**
     * Creates an ObjectMapper for serialization/deserialization.
     *
     * @return the ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Creates a Redis connection factory.
     *
     * @param properties the cache properties
     * @return the Redis connection factory
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisConnectionFactory redisConnectionFactory(AdharCacheProperties properties) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(properties.getRedis().getHost());
        redisConfig.setPort(properties.getRedis().getPort());

        if (properties.getRedis().getPassword() != null) {
            redisConfig.setPassword(properties.getRedis().getPassword());
        }

        redisConfig.setDatabase(properties.getRedis().getDatabase());

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(properties.getRedis().getConnectTimeout())
                .shutdownTimeout(Duration.ofMillis(100));

        if (properties.getRedis().isSsl()) {
            builder.useSsl();
        }

        LettuceClientConfiguration clientConfig = builder.build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    /**
     * Creates a Redis template for general-purpose Redis operations.
     *
     * @param connectionFactory the Redis connection factory
     * @param objectMapper      the object mapper for serialization/deserialization
     * @return the Redis template
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for keys
        template.setKeySerializer(new StringRedisSerializer());

        // Use Jackson2JsonRedisSerializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);

        // Also use the same serializers for hash keys and values
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * Creates a Redis cache manager.
     *
     * @param connectionFactory the Redis connection factory
     * @param properties        the cache properties
     * @param objectMapper      the object mapper for serialization/deserialization
     * @return the Redis cache manager
     */
    @Bean
    @ConditionalOnMissingBean(name = "cacheManager")
    @ConditionalOnProperty(prefix = "adhar.cache.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public org.springframework.cache.CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            AdharCacheProperties properties,
            ObjectMapper objectMapper) {
        return new RedisCacheManager(connectionFactory, properties, objectMapper);
    }
}
