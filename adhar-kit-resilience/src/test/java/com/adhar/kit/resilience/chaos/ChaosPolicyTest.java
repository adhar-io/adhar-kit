package com.adhar.kit.resilience.chaos;

import com.adhar.kit.resilience.chaos.ChaosPolicy.ChaosInjectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChaosPolicy}: enablement, per-method matching, error injection
 * and latency injection.
 */
@DisplayName("ChaosPolicy Tests")
class ChaosPolicyTest {

    @AfterEach
    void clearInterruptFlag() {
        // Some tests deliberately set the thread interrupt flag; clear it so it does not
        // leak into subsequent tests.
        Thread.interrupted();
    }

    @Test
    @DisplayName("isEnabled reflects the master switch")
    void isEnabled() {
        assertThat(new ChaosPolicy(true, false, 0, 0, false, 0, List.of()).isEnabled()).isTrue();
        assertThat(new ChaosPolicy(false, false, 0, 0, false, 0, List.of()).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("apply is a no-op when the policy is disabled, even with error probability 1")
    void disabledIsNoOp() {
        ChaosPolicy policy = new ChaosPolicy(false, false, 0, 0, true, 1.0, List.of());

        assertThatCode(() -> policy.apply("anything")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("error injection always fires at probability 1.0 for a matching method")
    void errorInjectionCertain() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of());

        assertThatThrownBy(() -> policy.apply("guarded"))
                .isInstanceOf(ChaosInjectedException.class)
                .hasMessageContaining("guarded");
    }

    @Test
    @DisplayName("error injection never fires at probability 0.0")
    void errorInjectionNever() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 0.0, List.of());

        assertThatCode(() -> policy.apply("guarded")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("error injection does not fire when error injection is disabled")
    void errorInjectionDisabled() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, false, 1.0, List.of());

        assertThatCode(() -> policy.apply("guarded")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empty includedMethods matches every method")
    void emptyMatcherMatchesAll() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of());

        assertThatThrownBy(() -> policy.apply("whatever")).isInstanceOf(ChaosInjectedException.class);
    }

    @Test
    @DisplayName("null includedMethods is treated as match-all")
    void nullMatcherMatchesAll() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, null);

        assertThatThrownBy(() -> policy.apply("whatever")).isInstanceOf(ChaosInjectedException.class);
    }

    @Test
    @DisplayName("matcher matches on exact simple-name equality")
    void matcherExactMatch() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of("createOrder"));

        assertThatThrownBy(() -> policy.apply("createOrder")).isInstanceOf(ChaosInjectedException.class);
    }

    @Test
    @DisplayName("matcher matches on substring containment")
    void matcherSubstringMatch() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of("Order"));

        assertThatThrownBy(() -> policy.apply("createOrder")).isInstanceOf(ChaosInjectedException.class);
    }

    @Test
    @DisplayName("non-matching method name is a no-op")
    void matcherNoMatch() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of("Order"));

        assertThatCode(() -> policy.apply("pay")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null method name with a non-empty matcher does not match")
    void nullMethodNameWithMatcher() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of("Order"));

        assertThatCode(() -> policy.apply(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blank matcher entries are ignored")
    void blankMatcherEntriesIgnored() {
        ChaosPolicy policy = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of(""));

        // the only matcher entry is blank -> nothing matches
        assertThatCode(() -> policy.apply("createOrder")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("latency injection sleeps for at least the configured (fixed) delay")
    void latencyInjectionFixedDelay() {
        ChaosPolicy policy = new ChaosPolicy(true, true, 30, 30, false, 0, List.of());

        long start = System.nanoTime();
        policy.apply("slow");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(25);
    }

    @Test
    @DisplayName("latency injection with a range sleeps within reasonable bounds")
    void latencyInjectionRange() {
        ChaosPolicy policy = new ChaosPolicy(true, true, 10, 25, false, 0, List.of());

        long start = System.nanoTime();
        policy.apply("slow");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("zero latency range performs no sleep and does not throw")
    void zeroLatencyNoSleep() {
        ChaosPolicy policy = new ChaosPolicy(true, true, 0, 0, false, 0, List.of());

        assertThatCode(() -> policy.apply("slow")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("negative min latency is clamped to zero and max is raised to min")
    void latencyBoundsClamped() {
        ChaosPolicy policy = new ChaosPolicy(true, true, -50, -100, false, 0, List.of());

        // min clamped to 0, max raised to 0 -> no sleep, no error
        assertThatCode(() -> policy.apply("slow")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an interrupt during latency injection surfaces as a ChaosInjectedException")
    void latencyInterruptWrapped() {
        ChaosPolicy policy = new ChaosPolicy(true, true, 50, 50, false, 0, List.of());

        Thread.currentThread().interrupt(); // pre-set so Thread.sleep throws immediately

        assertThatThrownBy(() -> policy.apply("slow"))
                .isInstanceOf(ChaosInjectedException.class)
                .hasMessageContaining("interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("both latency and error injection apply: latency runs before the injected error")
    void latencyThenError() {
        ChaosPolicy policy = new ChaosPolicy(true, true, 10, 10, true, 1.0, List.of());

        long start = System.nanoTime();
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> policy.apply("both"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(thrown).isInstanceOf(ChaosInjectedException.class);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("ChaosInjectedException exposes its cause when constructed with one")
    void injectedExceptionWithCause() {
        Throwable cause = new IllegalStateException("boom");
        ChaosInjectedException ex = new ChaosInjectedException("wrapped", cause);

        assertThat(ex).hasMessage("wrapped");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
