package com.adhar.kit.resilience.quarkus;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Quarkus implementation of Circuit Breaker Service.
 * Automatically activated when Quarkus is detected.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@ApplicationScoped
public class QuarkusCircuitBreakerAdapter implements FrameworkAdapter<CircuitBreakerService>, CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Inject
    public QuarkusCircuitBreakerAdapter(QuarkusResilienceConfig config) {
        // Create Resilience4j registry for Quarkus
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
        log.info("Initialized Quarkus Circuit Breaker adapter");
    }

    // No-arg constructor for CDI
    public QuarkusCircuitBreakerAdapter() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.QUARKUS;
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

