package com.adhar.kit.resilience.endpoint;

import com.adhar.kit.resilience.service.ResilienceMetricsService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Actuator endpoint exposing Adhar resilience metrics and management operations.
 *
 * <ul>
 *   <li>{@code GET /actuator/resilience} - snapshot of circuit breaker, retry,
 *       rate limiter and bulkhead metrics plus recorded resilience events</li>
 *   <li>{@code POST /actuator/resilience/{name}} - resets the named circuit breaker</li>
 * </ul>
 *
 * <p>Only active when Spring Boot Actuator is on the classpath and the endpoint is
 * exposed. Can be disabled with {@code adhar.resilience.endpoint.enabled=false}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@Endpoint(id = "resilience")
public class ResilienceEndpoint {

    private final ResilienceMetricsService metricsService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Snapshot of all resilience metrics and recorded events.
     *
     * @return metrics grouped by pattern plus an events section
     */
    @ReadOperation
    public Map<String, Object> resilience() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("circuitBreakers", metricsService.getCircuitBreakerMetrics());
        snapshot.put("retries", metricsService.getRetryMetrics());
        snapshot.put("rateLimiters", metricsService.getRateLimiterMetrics());
        snapshot.put("bulkheads", metricsService.getBulkheadMetrics());
        snapshot.put("events", metricsService.getEventMetrics());
        return snapshot;
    }

    /**
     * Resets the named circuit breaker back to its original (CLOSED) state,
     * clearing all recorded metrics.
     *
     * @param name circuit breaker name
     * @return operation result including the resulting state, or an error when unknown
     */
    @WriteOperation
    public Map<String, String> resetCircuitBreaker(@Selector String name) {
        Optional<CircuitBreaker> circuitBreaker = circuitBreakerRegistry.find(name);
        if (circuitBreaker.isEmpty()) {
            log.warn("Cannot reset unknown circuit breaker '{}'", name);
            return Map.of("name", name, "result", "not-found");
        }
        circuitBreaker.get().reset();
        log.info("Circuit breaker '{}' reset via actuator endpoint", name);
        return Map.of("name", name, "result", "reset",
                "state", circuitBreaker.get().getState().name());
    }
}
