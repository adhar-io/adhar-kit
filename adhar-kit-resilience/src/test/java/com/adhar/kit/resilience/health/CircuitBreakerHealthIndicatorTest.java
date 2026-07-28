package com.adhar.kit.resilience.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CircuitBreakerHealthIndicator} using a real in-memory
 * {@link CircuitBreakerRegistry}.
 */
@DisplayName("CircuitBreakerHealthIndicator Tests")
class CircuitBreakerHealthIndicatorTest {

    private CircuitBreakerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
    }

    @Test
    @DisplayName("reports UP when no circuit breakers are registered")
    void upWithNoBreakers() {
        Health health = new CircuitBreakerHealthIndicator(registry, Set.of()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("openCriticalBreakers", false);
    }

    @Test
    @DisplayName("reports UP when all breakers are closed")
    void upWhenAllClosed() {
        registry.circuitBreaker("a");
        registry.circuitBreaker("b");

        Health health = new CircuitBreakerHealthIndicator(registry, Set.of()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("with empty critical set, every breaker is critical so any open breaker turns it DOWN")
    void emptyCriticalSetTreatsAllAsCritical() {
        CircuitBreaker cb = registry.circuitBreaker("a");
        registry.circuitBreaker("b");
        cb.transitionToOpenState();

        Health health = new CircuitBreakerHealthIndicator(registry, Set.of()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("openCriticalBreakers", true);
    }

    @Test
    @DisplayName("null critical set is treated as empty (all breakers critical)")
    void nullCriticalSetTreatsAllAsCritical() {
        CircuitBreaker cb = registry.circuitBreaker("a");
        cb.transitionToOpenState();

        Health health = new CircuitBreakerHealthIndicator(registry, null).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("only a listed critical breaker being open turns the indicator DOWN")
    void onlyListedCriticalBreakerCounts() {
        CircuitBreaker critical = registry.circuitBreaker("payments");
        CircuitBreaker nonCritical = registry.circuitBreaker("recommendations");
        critical.transitionToOpenState();
        nonCritical.transitionToOpenState();

        Health health = new CircuitBreakerHealthIndicator(registry, Set.of("payments")).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("a non-critical open breaker alone leaves the indicator UP")
    void nonCriticalOpenBreakerStaysUp() {
        CircuitBreaker nonCritical = registry.circuitBreaker("recommendations");
        registry.circuitBreaker("payments"); // critical, but closed
        nonCritical.transitionToOpenState();

        Health health = new CircuitBreakerHealthIndicator(registry, Set.of("payments")).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("openCriticalBreakers", false);
    }

    @Test
    @DisplayName("a FORCED_OPEN critical breaker is treated as open")
    void forcedOpenCountsAsOpen() {
        CircuitBreaker cb = registry.circuitBreaker("payments");
        cb.transitionToForcedOpenState();

        Health health = new CircuitBreakerHealthIndicator(registry, Set.of("payments")).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("each breaker contributes a per-breaker detail with state, critical flag and failure rate")
    @SuppressWarnings("unchecked")
    void perBreakerDetails() {
        CircuitBreaker critical = registry.circuitBreaker("payments");
        registry.circuitBreaker("recommendations");
        critical.transitionToOpenState();

        Health health = new CircuitBreakerHealthIndicator(registry, Set.of("payments")).health();

        Map<String, Object> details = (Map<String, Object>) health.getDetails();
        assertThat(details).containsKeys("payments", "recommendations", "openCriticalBreakers");

        Map<String, Object> paymentsDetail = (Map<String, Object>) details.get("payments");
        assertThat(paymentsDetail)
                .containsEntry("state", "OPEN")
                .containsEntry("critical", true)
                .containsKey("failureRate");

        Map<String, Object> recDetail = (Map<String, Object>) details.get("recommendations");
        assertThat(recDetail)
                .containsEntry("state", "CLOSED")
                .containsEntry("critical", false);
    }
}
