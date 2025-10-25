package com.adhar.kit.resilience.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Resilience Auto-Configuration.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@SpringBootTest(classes = ResilienceAutoConfiguration.class)
@TestPropertySource(properties = {
    "adhar.resilience.enabled=true"
})
class ResilienceAutoConfigurationTest {

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired(required = false)
    private RetryRegistry retryRegistry;

    @Autowired(required = false)
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired(required = false)
    private BulkheadRegistry bulkheadRegistry;

    @Autowired(required = false)
    private TimeLimiterRegistry timeLimiterRegistry;

    @Test
    void shouldAutoConfigureCircuitBreakerRegistry() {
        assertThat(circuitBreakerRegistry).isNotNull();
    }

    @Test
    void shouldAutoConfigureRetryRegistry() {
        assertThat(retryRegistry).isNotNull();
    }

    @Test
    void shouldAutoConfigureRateLimiterRegistry() {
        assertThat(rateLimiterRegistry).isNotNull();
    }

    @Test
    void shouldAutoConfigureBulkheadRegistry() {
        assertThat(bulkheadRegistry).isNotNull();
    }

    @Test
    void shouldAutoConfigureTimeLimiterRegistry() {
        assertThat(timeLimiterRegistry).isNotNull();
    }

    @Test
    void shouldHaveDefaultConfigurations() {
        assertThat(circuitBreakerRegistry.getAllCircuitBreakers()).isEmpty();
        assertThat(retryRegistry.getAllRetries()).isEmpty();
        assertThat(rateLimiterRegistry.getAllRateLimiters()).isEmpty();
        assertThat(bulkheadRegistry.getAllBulkheads()).isEmpty();
        assertThat(timeLimiterRegistry.getAllTimeLimiters()).isEmpty();
    }
}

