package com.adhar.kit.resilience.micronaut;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Micronaut implementation of Circuit Breaker Service.
 * Automatically activated when Micronaut is detected.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Singleton
@Requires(classes = io.micronaut.context.ApplicationContext.class)
public class MicronautCircuitBreakerAdapter implements FrameworkAdapter<CircuitBreakerService>, CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public MicronautCircuitBreakerAdapter(MicronautResilienceConfig config) {
        // Create Resilience4j registry for Micronaut
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.getCircuitBreaker().getFailureRateThreshold())
                .slidingWindowSize(config.getCircuitBreaker().getSlidingWindowSize())
                .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
        log.info("Initialized Micronaut Circuit Breaker adapter");
    }

    // No-arg constructor for Micronaut
    public MicronautCircuitBreakerAdapter() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.MICRONAUT;
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

