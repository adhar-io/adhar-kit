package com.adhar.kit.resilience;

import com.adhar.kit.resilience.api.CircuitBreakerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link CircuitBreakerFacade}'s delegation contract.
 *
 * <p>The facade can never be built through its constructor in this module (every framework
 * branch throws because the adapter classes are excluded from compilation), so the instance
 * is allocated without invoking the constructor and a mock delegate is injected. This lets the
 * pure delegation behaviour and the {@code requireDelegate} guard be exercised directly.</p>
 */
@DisplayName("CircuitBreakerFacade Delegation Tests")
class CircuitBreakerFacadeDelegationTest {

    private CircuitBreakerFacade facade;
    private CircuitBreakerService delegate;

    @BeforeEach
    void setUp() {
        facade = new ObjenesisStd().newInstance(CircuitBreakerFacade.class);
        delegate = mock(CircuitBreakerService.class);
    }

    private void injectDelegate(CircuitBreakerService value) throws Exception {
        Field field = CircuitBreakerFacade.class.getDeclaredField("delegate");
        field.setAccessible(true);
        field.set(facade, value);
    }

    @Test
    @DisplayName("execute forwards to the delegate and returns its result")
    void executeDelegates() throws Exception {
        injectDelegate(delegate);
        Supplier<String> supplier = () -> "x";
        when(delegate.execute(eq("svc"), any())).thenReturn("result");

        assertThat(facade.execute("svc", supplier)).isEqualTo("result");
        verify(delegate).execute("svc", supplier);
    }

    @Test
    @DisplayName("executeWithFallback forwards supplier and fallback to the delegate")
    void executeWithFallbackDelegates() throws Exception {
        injectDelegate(delegate);
        Supplier<String> supplier = () -> "x";
        Supplier<String> fallback = () -> "fb";
        when(delegate.executeWithFallback(eq("svc"), any(), any())).thenReturn("done");

        assertThat(facade.executeWithFallback("svc", supplier, fallback)).isEqualTo("done");
        verify(delegate).executeWithFallback("svc", supplier, fallback);
    }

    @Test
    @DisplayName("getState forwards to the delegate")
    void getStateDelegates() throws Exception {
        injectDelegate(delegate);
        when(delegate.getState("svc")).thenReturn(CircuitBreakerService.State.OPEN);

        assertThat(facade.getState("svc")).isEqualTo(CircuitBreakerService.State.OPEN);
        verify(delegate).getState("svc");
    }

    @Test
    @DisplayName("getMetrics forwards to the delegate")
    void getMetricsDelegates() throws Exception {
        injectDelegate(delegate);
        CircuitBreakerService.Metrics metrics = mock(CircuitBreakerService.Metrics.class);
        when(delegate.getMetrics("svc")).thenReturn(metrics);

        assertThat(facade.getMetrics("svc")).isSameAs(metrics);
        verify(delegate).getMetrics("svc");
    }

    @Test
    @DisplayName("reset forwards to the delegate")
    void resetDelegates() throws Exception {
        injectDelegate(delegate);

        facade.reset("svc");
        verify(delegate).reset("svc");
    }

    @Test
    @DisplayName("operations fail clearly when the delegate is missing")
    void missingDelegateFailsClearly() throws Exception {
        injectDelegate(null);

        assertThatThrownBy(() -> facade.execute("svc", () -> "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delegate is not initialized");
        assertThatThrownBy(() -> facade.executeWithFallback("svc", () -> "x", () -> "fb"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> facade.getState("svc"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> facade.getMetrics("svc"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> facade.reset("svc"))
                .isInstanceOf(IllegalStateException.class);
    }
}
