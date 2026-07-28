package com.adhar.kit.health.spi;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link CircuitBreakerStateProvider} backed by a resilience4j {@code CircuitBreakerRegistry}.
 *
 * <p>Uses reflection to read breaker state so the health module never needs a
 * compile-time dependency on resilience4j — the class compiles and loads even when
 * resilience4j is absent, and is only wired in by auto-configuration when the
 * registry type is on the classpath.</p>
 *
 * <p>Expects a registry exposing {@code getAllCircuitBreakers()} returning a collection
 * of objects that each expose {@code getName()} and {@code getState()} (an enum whose
 * {@link Enum#name()} is one of {@code CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN,
 * METRICS_ONLY}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class Resilience4jCircuitBreakerStateProvider implements CircuitBreakerStateProvider {

    private final Object circuitBreakerRegistry;

    /**
     * Creates the provider.
     *
     * @param circuitBreakerRegistry a resilience4j {@code CircuitBreakerRegistry} instance
     */
    public Resilience4jCircuitBreakerStateProvider(Object circuitBreakerRegistry) {
        if (circuitBreakerRegistry == null) {
            throw new IllegalArgumentException("circuitBreakerRegistry must not be null");
        }
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public List<CircuitBreakerStatus> states() {
        List<CircuitBreakerStatus> result = new ArrayList<>();
        try {
            Method getAll = circuitBreakerRegistry.getClass().getMethod("getAllCircuitBreakers");
            Object breakers = getAll.invoke(circuitBreakerRegistry);
            if (breakers instanceof Iterable<?> iterable) {
                for (Object breaker : iterable) {
                    result.add(toStatus(breaker));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read resilience4j circuit breakers: {}", e.getMessage());
        }
        return result;
    }

    private CircuitBreakerStatus toStatus(Object breaker) throws ReflectiveOperationException {
        Method getName = breaker.getClass().getMethod("getName");
        Method getState = breaker.getClass().getMethod("getState");
        String name = String.valueOf(getName.invoke(breaker));
        Object state = getState.invoke(breaker);
        return new CircuitBreakerStatus(name, mapState(state == null ? null : state.toString()));
    }

    /**
     * Maps a resilience4j state name to the neutral {@link CircuitBreakerStatus.State}.
     *
     * @param stateName resilience4j state enum name
     * @return normalized state
     */
    static CircuitBreakerStatus.State mapState(String stateName) {
        if (stateName == null) {
            return CircuitBreakerStatus.State.UNKNOWN;
        }
        return switch (stateName) {
            case "CLOSED" -> CircuitBreakerStatus.State.CLOSED;
            case "OPEN" -> CircuitBreakerStatus.State.OPEN;
            case "HALF_OPEN" -> CircuitBreakerStatus.State.HALF_OPEN;
            case "DISABLED", "METRICS_ONLY" -> CircuitBreakerStatus.State.DISABLED;
            case "FORCED_OPEN" -> CircuitBreakerStatus.State.FORCED_OPEN;
            default -> CircuitBreakerStatus.State.UNKNOWN;
        };
    }
}
