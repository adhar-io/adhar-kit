package com.adhar.kit.health.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Resilience4jCircuitBreakerStateProvider}.
 *
 * <p>Uses duck-typed fakes that mirror the resilience4j {@code CircuitBreakerRegistry} /
 * {@code CircuitBreaker} shape so the reflection path is exercised without a resilience4j
 * dependency.</p>
 */
class Resilience4jCircuitBreakerStateProviderTest {

    /** Mirrors resilience4j's CircuitBreaker.State enum names. */
    enum FakeState { CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN, METRICS_ONLY }

    /** Duck-typed CircuitBreaker. */
    record FakeBreaker(String name, FakeState state) {
        public String getName() {
            return name;
        }

        public FakeState getState() {
            return state;
        }
    }

    /** Duck-typed CircuitBreakerRegistry. */
    static final class FakeRegistry {
        private final List<FakeBreaker> breakers;

        FakeRegistry(List<FakeBreaker> breakers) {
            this.breakers = breakers;
        }

        public List<FakeBreaker> getAllCircuitBreakers() {
            return breakers;
        }
    }

    @Test
    void constructor_nullRegistry_throws() {
        assertThatThrownBy(() -> new Resilience4jCircuitBreakerStateProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void states_mapsAllBreakerStates() {
        FakeRegistry registry = new FakeRegistry(List.of(
                new FakeBreaker("a", FakeState.CLOSED),
                new FakeBreaker("b", FakeState.OPEN),
                new FakeBreaker("c", FakeState.HALF_OPEN),
                new FakeBreaker("d", FakeState.FORCED_OPEN),
                new FakeBreaker("e", FakeState.DISABLED),
                new FakeBreaker("f", FakeState.METRICS_ONLY)));

        List<CircuitBreakerStatus> states =
                new Resilience4jCircuitBreakerStateProvider(registry).states();

        assertThat(states).containsExactly(
                new CircuitBreakerStatus("a", CircuitBreakerStatus.State.CLOSED),
                new CircuitBreakerStatus("b", CircuitBreakerStatus.State.OPEN),
                new CircuitBreakerStatus("c", CircuitBreakerStatus.State.HALF_OPEN),
                new CircuitBreakerStatus("d", CircuitBreakerStatus.State.FORCED_OPEN),
                new CircuitBreakerStatus("e", CircuitBreakerStatus.State.DISABLED),
                new CircuitBreakerStatus("f", CircuitBreakerStatus.State.DISABLED));
    }

    @Test
    void states_registryWithoutExpectedMethod_returnsEmpty() {
        Object notARegistry = new Object();

        List<CircuitBreakerStatus> states =
                new Resilience4jCircuitBreakerStateProvider(notARegistry).states();

        assertThat(states).isEmpty();
    }

    @Test
    void states_nonIterableResult_returnsEmpty() {
        Object weird = new Object() {
            public String getAllCircuitBreakers() {
                return "not-iterable";
            }
        };

        assertThat(new Resilience4jCircuitBreakerStateProvider(weird).states()).isEmpty();
    }

    @Test
    void mapState_handlesNullAndUnknown() {
        assertThat(Resilience4jCircuitBreakerStateProvider.mapState(null))
                .isEqualTo(CircuitBreakerStatus.State.UNKNOWN);
        assertThat(Resilience4jCircuitBreakerStateProvider.mapState("SOMETHING_ELSE"))
                .isEqualTo(CircuitBreakerStatus.State.UNKNOWN);
    }
}
