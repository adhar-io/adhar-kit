package com.adhar.kit.resilience.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Spring Boot implementation of Circuit Breaker Service.
 * Automatically activated when Spring Boot is detected.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Service
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@RequiredArgsConstructor
public class SpringCircuitBreakerAdapter implements FrameworkAdapter<CircuitBreakerService>, CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    @Override
    public CircuitBreakerService getService() {
        return this;
    }

    @Override
    public <T> T execute(String name, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return circuitBreaker.executeSupplier(supplier);
    }

    @Override
    public <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return circuitBreaker.executeSupplier(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                log.debug("Circuit breaker {} failed, executing fallback", name);
                return fallback.get();
            }
        });
    }

    @Override
    public State getState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return mapState(circuitBreaker.getState());
    }

    @Override
    public Metrics getMetrics(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        CircuitBreaker.Metrics resilience4jMetrics = circuitBreaker.getMetrics();

        return new Metrics() {
            @Override
            public float getFailureRate() {
                return resilience4jMetrics.getFailureRate();
            }

            @Override
            public int getNumberOfSuccessfulCalls() {
                return (int) resilience4jMetrics.getNumberOfSuccessfulCalls();
            }

            @Override
            public int getNumberOfFailedCalls() {
                return (int) resilience4jMetrics.getNumberOfFailedCalls();
            }

            @Override
            public int getNumberOfNotPermittedCalls() {
                return (int) resilience4jMetrics.getNumberOfNotPermittedCalls();
            }
        };
    }

    @Override
    public void reset(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        circuitBreaker.reset();
    }

    private State mapState(CircuitBreaker.State resilience4jState) {
        return switch (resilience4jState) {
            case CLOSED -> State.CLOSED;
            case OPEN -> State.OPEN;
            case HALF_OPEN -> State.HALF_OPEN;
            case DISABLED -> State.DISABLED;
            case FORCED_OPEN -> State.FORCED_OPEN;
            default -> State.CLOSED; // Treat unknown states as CLOSED
        };
    }
}

