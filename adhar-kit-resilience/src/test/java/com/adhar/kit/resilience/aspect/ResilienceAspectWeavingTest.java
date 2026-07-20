package com.adhar.kit.resilience.aspect;

import com.adhar.kit.resilience.annotation.CircuitBreaker;
import com.adhar.kit.resilience.annotation.Retry;
import com.adhar.kit.resilience.annotation.TimeLimiter;
import com.adhar.kit.resilience.config.ResilienceAutoConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for the unified resilience advice through a real Spring AOP proxy:
 * the pointcut matches, stacked annotations compose, attributes are honored and the
 * async path works.
 */
@SpringBootTest(classes = {ResilienceAutoConfiguration.class, ResilienceAspectWeavingTest.TestConfig.class})
@TestPropertySource(properties = "adhar.resilience.enabled=true")
@DisplayName("ResilienceAspect Weaving Tests")
class ResilienceAspectWeavingTest {

    @Autowired
    private GuardedService service;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCounters() {
        GuardedService.attempts.set(0);
    }

    @Test
    @DisplayName("@Retry through the proxy retries until success and honors maxAttempts")
    void retryThroughProxy() {
        String result = service.flaky();

        assertThat(result).isEqualTo("recovered");
        assertThat(GuardedService.attempts.get()).isEqualTo(4);
        assertThat(retryRegistry.retry("it-retry").getRetryConfig().getMaxAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("stacked @Retry + @CircuitBreaker execute once per attempt and fall back")
    void stackedAnnotationsThroughProxy() {
        String result = service.alwaysFails();

        assertThat(result).isEqualTo("fallback");
        assertThat(GuardedService.attempts.get()).isEqualTo(3);
        assertThat(circuitBreakerRegistry.circuitBreaker("it-stacked-cb")
                .getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    @Test
    @DisplayName("@TimeLimiter on a CompletableFuture method times out asynchronously")
    void asyncTimeLimiterThroughProxy() {
        CompletableFuture<String> future = service.never();

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Configuration
    static class TestConfig {
        @Bean
        GuardedService guardedService() {
            return new GuardedService();
        }
    }

    static class GuardedService {

        static final AtomicInteger attempts = new AtomicInteger();

        @Retry(name = "it-retry", maxAttempts = 4, waitDuration = 1)
        public String flaky() {
            if (attempts.incrementAndGet() < 4) {
                throw new IllegalStateException("boom " + attempts.get());
            }
            return "recovered";
        }

        @Retry(name = "it-stacked-retry", maxAttempts = 3, waitDuration = 1)
        @CircuitBreaker(name = "it-stacked-cb", fallbackMethod = "fallback")
        public String alwaysFails() {
            attempts.incrementAndGet();
            throw new IllegalStateException("down");
        }

        @SuppressWarnings("unused")
        private String fallback(Throwable ex) {
            return "fallback";
        }

        @TimeLimiter(name = "it-async", timeoutDuration = 200)
        public CompletableFuture<String> never() {
            return new CompletableFuture<>();
        }
    }
}
