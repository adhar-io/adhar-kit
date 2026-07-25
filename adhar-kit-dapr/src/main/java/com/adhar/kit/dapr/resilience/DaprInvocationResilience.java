package com.adhar.kit.dapr.resilience;

import com.adhar.kit.dapr.client.AdharDaprClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wraps Dapr service invocation with configurable retry, per-attempt timeout, and a simple
 * consecutive-failure circuit breaker with fallback support.
 *
 * <p>Closes the gap where {@link AdharDaprClient#invokeService} and
 * {@code DaprFacade#invokeService} rethrow raw exceptions with no resilience: callers get
 * linear-backoff retries, a hard timeout per attempt (run on a shared daemon executor), and
 * a circuit breaker that fails fast after {@code failureThreshold} consecutive failures until
 * {@code openStateDuration} elapses.</p>
 *
 * <p>Deliberately dependency-light: no Resilience4j or other external resilience library is
 * required, only a small internal helper built on {@link java.util.concurrent}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprInvocationResilience {

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private static final java.util.concurrent.ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "adhar-dapr-resilience");
        thread.setDaemon(true);
        return thread;
    });

    private final ResilienceSettings settings;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicLong openedAtMillis = new AtomicLong();

    public DaprInvocationResilience() {
        this(ResilienceSettings.defaults());
    }

    public DaprInvocationResilience(ResilienceSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * Invokes a service through {@link AdharDaprClient} with retry/timeout/circuit-breaker
     * applied, throwing on final failure.
     *
     * <p>Parameter order/semantics deliberately mirror
     * {@link AdharDaprClient#invokeService(String, String, String, Object, Class)} exactly
     * ({@code appId, httpMethod, endpoint, request, responseType}) to avoid any ambiguity when
     * wrapping it.</p>
     */
    public <T, R> R invokeService(AdharDaprClient client, String appId, String httpMethod, String endpoint,
                                   T request, Class<R> responseType) {
        return invokeService(client, appId, httpMethod, endpoint, request, responseType, null);
    }

    /**
     * Invokes a service through {@link AdharDaprClient} with retry/timeout/circuit-breaker
     * applied, falling back to {@code fallback} on final failure (or when the circuit is open)
     * instead of throwing.
     */
    public <T, R> R invokeService(AdharDaprClient client, String appId, String httpMethod, String endpoint,
                                   T request, Class<R> responseType, Function<Throwable, R> fallback) {
        Objects.requireNonNull(client, "client must not be null");
        return execute(() -> client.invokeService(appId, httpMethod, endpoint, request, responseType), fallback);
    }

    /**
     * Executes an arbitrary call with retry/timeout/circuit-breaker applied. This is the
     * generic engine reused by {@link #invokeService} above and by
     * {@code DaprFacade#invokeServiceResilient}.
     *
     * @param call     the call to execute (retried up to {@code maxAttempts} times)
     * @param fallback invoked with the final failure cause instead of throwing, or {@code null}
     *                 to propagate the failure
     * @return the call's result, or the fallback's result
     */
    public <R> R execute(Supplier<R> call, Function<Throwable, R> fallback) {
        Objects.requireNonNull(call, "call must not be null");

        if (!allowRequest()) {
            CircuitBreakerOpenException openException =
                    new CircuitBreakerOpenException("Circuit breaker is open; rejecting call");
            return applyFallbackOrThrow(openException, fallback);
        }

        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            try {
                R result = callWithTimeout(call);
                onSuccess();
                return result;
            } catch (Exception e) {
                lastFailure = e;
                onFailure();
                log.debug("Dapr invocation attempt {}/{} failed: {}", attempt, settings.maxAttempts(), e.toString());
                if (attempt < settings.maxAttempts()) {
                    sleepQuietly(settings.retryBackoff().multipliedBy(attempt));
                }
            }
        }
        return applyFallbackOrThrow(lastFailure, fallback);
    }

    /**
     * Whether the circuit breaker is currently open (exposed for tests/monitoring).
     */
    public boolean isCircuitOpen() {
        return state.get() == CircuitState.OPEN;
    }

    private <R> R callWithTimeout(Supplier<R> call) {
        Future<R> future = EXECUTOR.submit(call::get);
        try {
            return future.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DaprInvocationException("Dapr invocation timed out after " + settings.timeout(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new DaprInvocationException("Dapr invocation failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DaprInvocationException("Dapr invocation interrupted", e);
        }
    }

    private boolean allowRequest() {
        if (state.get() == CircuitState.OPEN) {
            if (System.currentTimeMillis() - openedAtMillis.get() >= settings.openStateDuration().toMillis()) {
                state.set(CircuitState.HALF_OPEN);
                return true;
            }
            return false;
        }
        return true;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        state.set(CircuitState.CLOSED);
    }

    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state.get() == CircuitState.HALF_OPEN || failures >= settings.failureThreshold()) {
            state.set(CircuitState.OPEN);
            openedAtMillis.set(System.currentTimeMillis());
        }
    }

    private <R> R applyFallbackOrThrow(Throwable failure, Function<Throwable, R> fallback) {
        if (fallback != null) {
            return fallback.apply(failure);
        }
        if (failure instanceof RuntimeException re) {
            throw re;
        }
        throw new DaprInvocationException("Dapr invocation failed", failure);
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(Math.max(0, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
