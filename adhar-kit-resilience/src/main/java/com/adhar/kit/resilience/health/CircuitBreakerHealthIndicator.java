package com.adhar.kit.resilience.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Spring Boot health contributor that reports the resilience subsystem as {@code DOWN}
 * when one or more <em>critical</em> circuit breakers are open.
 *
 * <p>Registered under the {@code resilienceCircuitBreakers} health indicator id, this
 * surfaces at {@code /actuator/health} (and as a dedicated
 * {@code /actuator/health/resilienceCircuitBreakers} probe). Only active when Spring Boot
 * Actuator's health API is on the classpath.</p>
 *
 * <p>A circuit breaker is considered <em>critical</em> when its name is listed in
 * {@code adhar.resilience.health.critical-circuit-breakers}. When that list is empty every
 * circuit breaker is treated as critical, so any open breaker turns the indicator down.
 * Each breaker contributes a per-breaker detail entry (state, failure rate, whether it is
 * critical), regardless of the overall status.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Set<String> criticalCircuitBreakers;

    /**
     * Creates the health indicator.
     *
     * @param circuitBreakerRegistry registry of circuit breakers to inspect
     * @param criticalCircuitBreakers names of breakers that must not be open; empty means
     *                                every breaker is treated as critical
     */
    public CircuitBreakerHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry,
                                         Set<String> criticalCircuitBreakers) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.criticalCircuitBreakers = criticalCircuitBreakers == null ? Set.of() : Set.copyOf(criticalCircuitBreakers);
    }

    @Override
    public Health health() {
        boolean down = false;
        Map<String, Object> details = new LinkedHashMap<>();

        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            CircuitBreaker.State state = cb.getState();
            boolean critical = criticalCircuitBreakers.isEmpty() || criticalCircuitBreakers.contains(cb.getName());
            boolean open = state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN;

            Map<String, Object> breakerDetail = new LinkedHashMap<>();
            breakerDetail.put("state", state.name());
            breakerDetail.put("critical", critical);
            breakerDetail.put("failureRate", cb.getMetrics().getFailureRate());
            details.put(cb.getName(), breakerDetail);

            if (critical && open) {
                down = true;
            }
        }

        Health.Builder builder = down ? Health.down() : Health.up();
        builder.withDetail("openCriticalBreakers", down);
        details.forEach(builder::withDetail);
        if (down) {
            log.warn("Resilience health DOWN: one or more critical circuit breakers are open");
        }
        return builder.build();
    }
}
