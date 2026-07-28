package com.adhar.kit.security.config;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.ratelimit.RateLimiterStore;
import com.adhar.kit.security.ratelimit.RedisRateLimiterStore;
import com.adhar.kit.security.service.RedisRefreshTokenStore;
import com.adhar.kit.security.service.RefreshTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed security store beans, activated only when spring-data-redis is on the
 * classpath (via {@link ConditionalOnClass}) and a {@link StringRedisTemplate} bean is
 * available. This keeps the Redis dependency optional: the module compiles and runs
 * without it, and these beans simply do not register.
 *
 * <p>The distributed stores replace their in-memory counterparts only when the
 * relevant {@code store} property is set to {@code redis}, keeping the two mutually
 * exclusive without relying on bean-ordering.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisSecurityConfiguration {

    /**
     * Redis-backed refresh-token store, active when token refresh is enabled and
     * {@code adhar.security.token-refresh.store=redis}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.security.token-refresh", name = "enabled", havingValue = "true")
    static class RedisRefreshTokenStoreConfiguration {

        /**
         * Configures the Redis-backed refresh-token store.
         *
         * @param redisTemplate the string Redis template
         * @param properties the security properties (for the key prefix)
         * @return the Redis refresh-token store
         */
        @Bean
        @ConditionalOnMissingBean(RefreshTokenStore.class)
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnProperty(prefix = "adhar.security.token-refresh", name = "store", havingValue = "redis")
        public RefreshTokenStore refreshTokenStore(StringRedisTemplate redisTemplate,
                                                   AdharSecurityProperties properties) {
            log.info("Configuring Redis-backed refresh-token store");
            return new RedisRefreshTokenStore(redisTemplate,
                properties.getTokenRefresh().getRedisKeyPrefix());
        }
    }

    /**
     * Redis-backed rate-limiter store, active when rate limiting is enabled and
     * {@code adhar.security.rate-limit.store=redis}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.security.rate-limit", name = "enabled", havingValue = "true")
    static class RedisRateLimiterStoreConfiguration {

        /**
         * Configures the Redis-backed rate-limiter store.
         *
         * @param redisTemplate the string Redis template
         * @param properties the security properties (for the key prefix)
         * @return the Redis rate-limiter store
         */
        @Bean
        @ConditionalOnMissingBean(RateLimiterStore.class)
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnProperty(prefix = "adhar.security.rate-limit", name = "store", havingValue = "redis")
        public RateLimiterStore rateLimiterStore(StringRedisTemplate redisTemplate,
                                                 AdharSecurityProperties properties) {
            log.info("Configuring Redis-backed rate-limiter store");
            return new RedisRateLimiterStore(redisTemplate,
                properties.getRateLimit().getRedisKeyPrefix());
        }
    }
}
