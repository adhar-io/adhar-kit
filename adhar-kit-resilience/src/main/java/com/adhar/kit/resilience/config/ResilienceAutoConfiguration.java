package com.adhar.kit.resilience.config;

import com.adhar.kit.resilience.aspect.ResilienceAspect;
import com.adhar.kit.resilience.cache.FallbackCache;
import com.adhar.kit.resilience.chaos.ChaosPolicy;
import com.adhar.kit.resilience.endpoint.ResilienceEndpoint;
import com.adhar.kit.resilience.event.ResilienceEventListeners;
import com.adhar.kit.resilience.event.ResilienceEventRecorder;
import com.adhar.kit.resilience.health.CircuitBreakerHealthIndicator;
import com.adhar.kit.resilience.metrics.ResiliencePlatformMetricsBridge;
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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.time.Duration;
import java.util.HashSet;

/**
 * Auto-configuration for Adhar Resilience module.
 *
 * <p>Configures Resilience4j registries and metrics with declarative annotation support.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Circuit Breaker - Prevents cascading failures</li>
 *   <li>Retry - Automatic retry with exponential backoff</li>
 *   <li>Rate Limiter - Request rate limiting</li>
 *   <li>Bulkhead - Concurrent request isolation</li>
 *   <li>Time Limiter - Timeout management</li>
 *   <li>Micrometer metrics integration</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   resilience:
 *     enabled: true
 *     circuit-breaker:
 *       default:
 *         failure-rate-threshold: 50
 *         slow-call-rate-threshold: 100
 *         wait-duration-in-open-state: 60s
 *     retry:
 *       default:
 *         max-attempts: 3
 *         wait-duration: 500ms
 *     rate-limiter:
 *       default:
 *         limit-for-period: 10
 *         limit-refresh-period: 1s
 *     metrics:
 *       enabled: true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(ResilienceProperties.class)
@ConditionalOnProperty(prefix = "adhar.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreaker")
public class ResilienceAutoConfiguration {

    @PostConstruct
    public void logResilienceConfiguration() {
        log.info("Adhar Resilience module initialized with Resilience4j patterns (CircuitBreaker, Retry, RateLimiter, Bulkhead, TimeLimiter)");
    }

    /**
     * Imports the Resilience4j Spring Boot aspect auto-configurations so that the
     * {@code io.github.resilience4j.*.annotation.*} annotations (CircuitBreaker, Retry,
     * RateLimiter, Bulkhead, TimeLimiter) are intercepted and applied.
     *
     * <p>Activated only when {@code resilience4j-spring-boot3} is on the classpath; otherwise
     * this configuration silently backs off so the module remains usable in non-Spring
     * runtimes (Quarkus, Micronaut, Helidon, Vert.x).</p>
     */
    @Configuration
    @ConditionalOnClass(CircuitBreakerAutoConfiguration.class)
    @ImportAutoConfiguration({
            CircuitBreakerAutoConfiguration.class,
            RetryAutoConfiguration.class,
            RateLimiterAutoConfiguration.class,
            BulkheadAutoConfiguration.class,
            TimeLimiterAutoConfiguration.class
    })
    public static class Resilience4jAspectConfiguration {
    }

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

    /**
     * Bounded "last known good" fallback cache. Always registered so methods can opt in via
     * {@code fallbackCache=true} on their annotation even when the global flag is off.
     */
    @Bean
    @ConditionalOnMissingBean
    public FallbackCache resilienceFallbackCache(ResilienceProperties properties) {
        ResilienceProperties.FallbackCacheConfig config = properties.getFallbackCache();
        log.info("Initializing resilience FallbackCache (maxSize={}, ttl={}, globalEnabled={})",
                config.getMaxSize(), config.getTtl(), config.isEnabled());
        return new FallbackCache(config.getMaxSize(), config.getTtl());
    }

    /**
     * Chaos policy for resilience testing. Only registered when
     * {@code adhar.resilience.chaos.enabled=true}; disabled (absent) by default.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.resilience.chaos", name = "enabled", havingValue = "true")
    public ChaosPolicy resilienceChaosPolicy(ResilienceProperties properties) {
        ResilienceProperties.ChaosConfig config = properties.getChaos();
        log.warn("Initializing resilience ChaosPolicy (latency={}, error={}) - FOR TESTING ONLY",
                config.isLatencyEnabled(), config.isErrorEnabled());
        return new ChaosPolicy(config.isEnabled(), config.isLatencyEnabled(),
                config.getMinLatencyMs(), config.getMaxLatencyMs(),
                config.isErrorEnabled(), config.getErrorProbability(), config.getIncludedMethods());
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilienceAspect resilienceAspect(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            ResilienceProperties properties,
            ObjectProvider<FallbackCache> fallbackCache,
            ObjectProvider<ChaosPolicy> chaosPolicy) {
        log.info("Initializing ResilienceAspect");
        return new ResilienceAspect(circuitBreakerRegistry, retryRegistry,
                rateLimiterRegistry, bulkheadRegistry, timeLimiterRegistry,
                fallbackCache.getIfAvailable(),
                properties.getFallbackCache().isEnabled(),
                chaosPolicy.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.resilience.events", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ResilienceEventRecorder resilienceEventRecorder() {
        log.info("Initializing ResilienceEventRecorder");
        return new ResilienceEventRecorder();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.resilience.events", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ResilienceEventListeners resilienceEventListeners(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            ResilienceEventRecorder resilienceEventRecorder) {
        log.info("Initializing ResilienceEventListeners");
        ResilienceEventListeners listeners = new ResilienceEventListeners(
                circuitBreakerRegistry, retryRegistry, rateLimiterRegistry,
                bulkheadRegistry, timeLimiterRegistry, resilienceEventRecorder);
        listeners.register();
        return listeners;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ResilienceMetricsService resilienceMetricsService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            ObjectProvider<ResilienceEventRecorder> resilienceEventRecorder) {
        log.info("Initializing ResilienceMetricsService");
        return new ResilienceMetricsService(circuitBreakerRegistry, retryRegistry,
                                           rateLimiterRegistry, bulkheadRegistry,
                                           resilienceEventRecorder.getIfAvailable());
    }

    /**
     * Registers the {@code resilience} actuator endpoint when Spring Boot Actuator is
     * on the classpath. Falls back to a locally constructed metrics service when the
     * shared one is disabled, so the endpoint remains functional either way.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public static class ResilienceEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.resilience.endpoint", name = "enabled", havingValue = "true", matchIfMissing = true)
        public ResilienceEndpoint resilienceEndpoint(
                CircuitBreakerRegistry circuitBreakerRegistry,
                RetryRegistry retryRegistry,
                RateLimiterRegistry rateLimiterRegistry,
                BulkheadRegistry bulkheadRegistry,
                ObjectProvider<ResilienceMetricsService> metricsService,
                ObjectProvider<ResilienceEventRecorder> eventRecorder) {
            log.info("Initializing Resilience actuator endpoint");
            ResilienceMetricsService service = metricsService.getIfAvailable(
                    () -> new ResilienceMetricsService(circuitBreakerRegistry, retryRegistry,
                            rateLimiterRegistry, bulkheadRegistry, eventRecorder.getIfAvailable()));
            return new ResilienceEndpoint(service, circuitBreakerRegistry);
        }
    }

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
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

    /**
     * Bridges Resilience4j events to the metrics module's {@code PlatformMetrics}.
     *
     * <p>Only active when {@code adhar-kit-metrics} is on the classpath and a
     * {@code PlatformMetrics} bean is present, so the module compiles and runs unchanged
     * when the optional dependency is absent.</p>
     */
    @Configuration
    @ConditionalOnClass(com.adhar.kit.metrics.auto.PlatformMetrics.class)
    @ConditionalOnProperty(prefix = "adhar.resilience.metrics", name = "bridge-to-platform-metrics", havingValue = "true", matchIfMissing = true)
    public static class ResiliencePlatformMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(com.adhar.kit.metrics.auto.PlatformMetrics.class)
        public ResiliencePlatformMetricsBridge resiliencePlatformMetricsBridge(
                CircuitBreakerRegistry circuitBreakerRegistry,
                RetryRegistry retryRegistry,
                RateLimiterRegistry rateLimiterRegistry,
                BulkheadRegistry bulkheadRegistry,
                TimeLimiterRegistry timeLimiterRegistry,
                com.adhar.kit.metrics.auto.PlatformMetrics platformMetrics) {
            log.info("Initializing Resilience -> PlatformMetrics bridge");
            ResiliencePlatformMetricsBridge bridge = new ResiliencePlatformMetricsBridge(
                    circuitBreakerRegistry, retryRegistry, rateLimiterRegistry,
                    bulkheadRegistry, timeLimiterRegistry, platformMetrics);
            bridge.register();
            return bridge;
        }
    }

    /**
     * Registers the circuit breaker health contributor when Spring Boot Actuator's health
     * API is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(org.springframework.boot.health.contributor.HealthIndicator.class)
    public static class ResilienceHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.resilience.health", name = "enabled", havingValue = "true", matchIfMissing = true)
        public CircuitBreakerHealthIndicator resilienceCircuitBreakers(
                CircuitBreakerRegistry circuitBreakerRegistry,
                ResilienceProperties properties) {
            log.info("Initializing circuit breaker health contributor");
            return new CircuitBreakerHealthIndicator(circuitBreakerRegistry,
                    new HashSet<>(properties.getHealth().getCriticalCircuitBreakers()));
        }
    }
}

