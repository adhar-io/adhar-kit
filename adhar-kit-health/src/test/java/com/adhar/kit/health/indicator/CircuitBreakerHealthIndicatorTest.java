package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.spi.CircuitBreakerStateProvider;
import com.adhar.kit.health.spi.CircuitBreakerStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CircuitBreakerHealthIndicator}.
 */
class CircuitBreakerHealthIndicatorTest {

    private static CircuitBreakerStateProvider provider(CircuitBreakerStatus... statuses) {
        return () -> List.of(statuses);
    }

    private static CircuitBreakerStatus cb(String name, CircuitBreakerStatus.State state) {
        return new CircuitBreakerStatus(name, state);
    }

    @Test
    void name_isCircuitBreakers() {
        assertThat(new CircuitBreakerHealthIndicator(List.of()).getName())
                .isEqualTo(CircuitBreakerHealthIndicator.NAME);
    }

    @Test
    void noBreakers_isUp() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("total", 0);
    }

    @Test
    void allClosed_isUp() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                provider(cb("a", CircuitBreakerStatus.State.CLOSED),
                        cb("b", CircuitBreakerStatus.State.DISABLED))));

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("total", 2);
        assertThat((List<?>) health.getDetails().get("open")).isEmpty();
    }

    @Test
    void openBreaker_isDown_withNamesAndError() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                provider(cb("payments", CircuitBreakerStatus.State.OPEN),
                        cb("ok", CircuitBreakerStatus.State.CLOSED))));

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails().get("open")).isEqualTo(List.of("payments"));
        assertThat(health.getError()).contains("payments");
    }

    @Test
    void forcedOpen_isDown() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                provider(cb("x", CircuitBreakerStatus.State.FORCED_OPEN))));

        assertThat(indicator.check().getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void halfOpen_isDegraded_byDefault() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                provider(cb("x", CircuitBreakerStatus.State.HALF_OPEN))));

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
        assertThat(health.getDetails().get("halfOpen")).isEqualTo(List.of("x"));
    }

    @Test
    void halfOpen_treatedAsUp_whenConfigured() {
        AdharHealthProperties.CircuitBreakerConfig config = new AdharHealthProperties.CircuitBreakerConfig();
        config.setTreatHalfOpenAsDegraded(false);
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(
                List.of(provider(cb("x", CircuitBreakerStatus.State.HALF_OPEN))), config);

        assertThat(indicator.check().getStatus()).isEqualTo(Health.Status.UP);
    }

    @Test
    void openWinsOverHalfOpen() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                provider(cb("open", CircuitBreakerStatus.State.OPEN),
                        cb("half", CircuitBreakerStatus.State.HALF_OPEN))));

        assertThat(indicator.check().getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void aggregatesAcrossMultipleProviders() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                provider(cb("a", CircuitBreakerStatus.State.CLOSED)),
                provider(cb("b", CircuitBreakerStatus.State.OPEN))));

        Health health = indicator.check();

        assertThat(health.getDetails()).containsEntry("total", 2);
        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void failingProvider_isIsolated() {
        CircuitBreakerStateProvider bad = () -> {
            throw new IllegalStateException("boom");
        };
        CircuitBreakerStateProvider nullStates = () -> null;
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(List.of(
                bad, nullStates, provider(cb("ok", CircuitBreakerStatus.State.CLOSED))));

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("total", 1);
    }

    @Test
    void nullProviders_treatedAsEmpty() {
        CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(null);

        assertThat(indicator.check().getStatus()).isEqualTo(Health.Status.UP);
    }

    @Test
    void statusRecord_helpers() {
        assertThat(cb("a", CircuitBreakerStatus.State.OPEN).isOpen()).isTrue();
        assertThat(cb("a", CircuitBreakerStatus.State.FORCED_OPEN).isOpen()).isTrue();
        assertThat(cb("a", CircuitBreakerStatus.State.HALF_OPEN).isOpen()).isFalse();
        assertThat(cb("a", CircuitBreakerStatus.State.HALF_OPEN).isHalfOpen()).isTrue();
        assertThat(cb("a", CircuitBreakerStatus.State.CLOSED).isHalfOpen()).isFalse();
    }
}
