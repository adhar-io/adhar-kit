package com.adhar.kit.resilience.config;

import com.adhar.kit.resilience.aspect.ResilienceAspect;
import com.adhar.kit.resilience.service.ResilienceMetricsService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.time.Duration;

/**
 * Auto-configuration for Adhar Resilience module.
 * Configures Resilience4j registries and metrics.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(ResilienceProperties.class)
@ConditionalOnProperty(prefix = "adhar.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties properties) {
        log.info("Initializing CircuitBreakerRegistry with default configuration");

        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(100.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(60))
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(10)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Configure named circuit breakers
        properties.getCircuitBreaker().forEach((name, config) -> {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(config.getFailureRateThreshold())
                    .slowCallRateThreshold(config.getSlowCallRateThreshold())
                    .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
                    .slidingWindowSize(config.getSlidingWindowSize())
                    .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                    .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
                    .waitDurationInOpenState(config.getWaitDurationInOpenState())
                    .automaticTransitionFromOpenToHalfOpenEnabled(config.isAutomaticTransitionFromOpenToHalfOpenEnabled())
                    .build();

            registry.circuitBreaker(name, cbConfig);
            log.info("Configured circuit breaker: {}", name);
        });

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryRegistry retryRegistry(ResilienceProperties properties) {
        log.info("Initializing RetryRegistry with default configuration");

        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // Configure named retries
        properties.getRetry().forEach((name, config) -> {
            RetryConfig.Builder builder = RetryConfig.custom()
                    .maxAttempts(config.getMaxAttempts())
                    .waitDuration(config.getWaitDuration());

            if (config.isEnableExponentialBackoff()) {
                builder.intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(config.getWaitDuration().toMillis(),
                                            config.getExponentialBackoffMultiplier()));
            }

            registry.retry(name, builder.build());
            log.info("Configured retry: {}", name);
        });

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry(ResilienceProperties properties) {
        log.info("Initializing RateLimiterRegistry with default configuration");

        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);

        // Configure named rate limiters
        properties.getRateLimiter().forEach((name, config) -> {
            RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getLimitForPeriod())
                    .limitRefreshPeriod(config.getLimitRefreshPeriod())
                    .timeoutDuration(config.getTimeoutDuration())
                    .build();

            registry.rateLimiter(name, rlConfig);
            log.info("Configured rate limiter: {}", name);
        });

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public BulkheadRegistry bulkheadRegistry(ResilienceProperties properties) {
        log.info("Initializing BulkheadRegistry with default configuration");

        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(25)
                .maxWaitDuration(Duration.ofMillis(0))
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);

        // Configure named bulkheads
        properties.getBulkhead().forEach((name, config) -> {
            BulkheadConfig bhConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(config.getMaxConcurrentCalls())
                    .maxWaitDuration(config.getMaxWaitDuration())
                    .build();

            registry.bulkhead(name, bhConfig);
            log.info("Configured bulkhead: {}", name);
        });

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeLimiterRegistry timeLimiterRegistry(ResilienceProperties properties) {
        log.info("Initializing TimeLimiterRegistry with default configuration");

        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(1))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultConfig);

        // Configure named time limiters
        properties.getTimeLimiter().forEach((name, config) -> {
            TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                    .timeoutDuration(config.getTimeoutDuration())
                    .cancelRunningFuture(config.isCancelRunningFuture())
                    .build();

            registry.timeLimiter(name, tlConfig);
            log.info("Configured time limiter: {}", name);
        });

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilienceAspect resilienceAspect(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        log.info("Initializing ResilienceAspect");
        return new ResilienceAspect(circuitBreakerRegistry, retryRegistry,
                                   rateLimiterRegistry, bulkheadRegistry, timeLimiterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ResilienceMetricsService resilienceMetricsService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry) {
        log.info("Initializing ResilienceMetricsService");
        return new ResilienceMetricsService(circuitBreakerRegistry, retryRegistry,
                                           rateLimiterRegistry, bulkheadRegistry);
    }

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class ResilienceMetricsConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "export-circuit-breaker-metrics", havingValue = "true", matchIfMissing = true)
        public TaggedCircuitBreakerMetrics taggedCircuitBreakerMetrics(
                CircuitBreakerRegistry circuitBreakerRegistry,
                MeterRegistry meterRegistry) {
            log.info("Registering CircuitBreaker metrics");
            return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
        }

        @Bean
        @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "export-retry-metrics", havingValue = "true", matchIfMissing = true)
        public TaggedRetryMetrics taggedRetryMetrics(
                RetryRegistry retryRegistry,
                MeterRegistry meterRegistry) {
            log.info("Registering Retry metrics");
            return TaggedRetryMetrics.ofRetryRegistry(retryRegistry);
        }

        @Bean
        @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "export-rate-limiter-metrics", havingValue = "true", matchIfMissing = true)
        public TaggedRateLimiterMetrics taggedRateLimiterMetrics(
                RateLimiterRegistry rateLimiterRegistry,
                MeterRegistry meterRegistry) {
            log.info("Registering RateLimiter metrics");
            return TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry);
        }

        @Bean
        @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "export-bulkhead-metrics", havingValue = "true", matchIfMissing = true)
        public TaggedBulkheadMetrics taggedBulkheadMetrics(
                BulkheadRegistry bulkheadRegistry,
                MeterRegistry meterRegistry) {
            log.info("Registering Bulkhead metrics");
            return TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry);
        }
    }
}

