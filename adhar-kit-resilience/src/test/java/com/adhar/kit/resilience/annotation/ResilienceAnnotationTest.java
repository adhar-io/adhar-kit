package com.adhar.kit.resilience.annotation;

import com.adhar.kit.resilience.config.ResilienceAutoConfiguration;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Resilience4j annotations.
 * Tests circuit breaker, retry, rate limiter, and bulkhead patterns.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@SpringBootTest(classes = {ResilienceAutoConfiguration.class, ResilienceAnnotationTest.TestConfig.class})
@TestPropertySource(properties = {
    "adhar.resilience.enabled=true",
    "resilience4j.circuitbreaker.instances.test.slidingWindowSize=10",
    "resilience4j.circuitbreaker.instances.test-fail.slidingWindowSize=10",
    "resilience4j.retry.instances.test.maxAttempts=3",
    "resilience4j.ratelimiter.instances.test.limitForPeriod=10",
    "resilience4j.bulkhead.instances.test.maxConcurrentCalls=5"
})
class ResilienceAnnotationTest {

    @Autowired
    private TestService testService;

    @Test
    void circuitBreakerShouldWork() {
        String result = testService.methodWithCircuitBreaker("test");
        assertThat(result).isEqualTo("success: test");
    }

    @Test
    void circuitBreakerShouldUseFallback() {
        String result = testService.methodWithCircuitBreakerThatFails("test");
        assertThat(result).isEqualTo("fallback: test");
    }

    @Test
    void retryShouldWork() {
        testService.resetCounter();
        String result = testService.methodWithRetry();
        assertThat(result).isEqualTo("success");
        assertThat(testService.getCounter()).isGreaterThan(1); // Should have retried
    }

    @Test
    void rateLimiterShouldWork() {
        String result = testService.methodWithRateLimit("test");
        assertThat(result).isEqualTo("limited: test");
    }

    @Test
    void bulkheadShouldWork() {
        String result = testService.methodWithBulkhead("test");
        assertThat(result).isEqualTo("bulkhead: test");
    }

    @Configuration
    static class TestConfig {

        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    @Component
    static class TestService {

        private final AtomicInteger counter = new AtomicInteger(0);

        @CircuitBreaker(name = "test")
        public String methodWithCircuitBreaker(String input) {
            return "success: " + input;
        }

        @CircuitBreaker(name = "test-fail", fallbackMethod = "fallbackMethod")
        public String methodWithCircuitBreakerThatFails(String input) {
            throw new RuntimeException("Simulated failure");
        }

        private String fallbackMethod(String input, Throwable ex) {
            return "fallback: " + input;
        }

        @Retry(name = "test")
        public String methodWithRetry() {
            if (counter.incrementAndGet() < 2) {
                throw new RuntimeException("Retry needed");
            }
            return "success";
        }

        @RateLimiter(name = "test")
        public String methodWithRateLimit(String input) {
            return "limited: " + input;
        }

        @Bulkhead(name = "test")
        public String methodWithBulkhead(String input) {
            return "bulkhead: " + input;
        }

        public void resetCounter() {
            counter.set(0);
        }

        public int getCounter() {
            return counter.get();
        }
    }
}
