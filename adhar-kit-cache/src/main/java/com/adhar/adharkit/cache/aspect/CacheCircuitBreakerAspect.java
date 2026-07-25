package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Aspect implementing {@link CacheCircuitBreaker}: a self-contained
 * closed/open/half-open circuit breaker around cache-backed methods (no
 * resilience4j dependency).
 *
 * <p>State machine (one breaker per {@code cacheName}):</p>
 * <ul>
 *   <li><b>CLOSED</b> - invocations proceed normally. An invocation that throws,
 *       or that takes longer than {@code timeout} milliseconds (a "slow call"),
 *       counts as a failure. After {@code failureThreshold} consecutive failures
 *       the circuit opens.</li>
 *   <li><b>OPEN</b> - the protected invocation is skipped entirely for
 *       {@code waitDurationInOpenState} milliseconds; the {@code fallbackMethod}
 *       is invoked instead. When no fallback is resolvable the invocation falls
 *       through to the method itself (bypassing nothing at this aspect's level,
 *       but never counting failures while open).</li>
 *   <li><b>HALF_OPEN</b> - after the wait duration, a single trial invocation is
 *       allowed through. Success closes the circuit; failure re-opens it.
 *       Concurrent callers during the trial receive the fallback.</li>
 * </ul>
 *
 * <p>When an invocation fails while the circuit is closed or half-open, the
 * failure is recorded and the {@code fallbackMethod} is invoked; if no fallback
 * is resolvable the original exception propagates. The fallback method is looked
 * up on the target class by name, preferring an exact parameter-type match with
 * the intercepted method.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CacheCircuitBreakerAspect {

    /**
     * Circuit breaker states.
     */
    public enum State {
        /** Failures below threshold; calls proceed normally. */
        CLOSED,
        /** Threshold reached; calls short-circuit to the fallback. */
        OPEN,
        /** Wait elapsed; a single trial call is in flight. */
        HALF_OPEN
    }

    private final ConcurrentHashMap<String, CircuitBreakerState> breakers = new ConcurrentHashMap<>();

    /**
     * Applies circuit-breaker semantics around {@link CacheCircuitBreaker} methods.
     *
     * @param joinPoint      the intercepted invocation
     * @param circuitBreaker the annotation instance
     * @return the method result or the fallback result
     * @throws Throwable if the underlying method (and fallback) fail
     */
    @Around("@annotation(circuitBreaker)")
    public Object aroundCircuitBreaker(ProceedingJoinPoint joinPoint,
                                       CacheCircuitBreaker circuitBreaker) throws Throwable {
        CircuitBreakerState breaker = breakers.computeIfAbsent(circuitBreaker.cacheName(),
            name -> new CircuitBreakerState(
                circuitBreaker.failureThreshold(),
                circuitBreaker.waitDurationInOpenState()));

        if (!breaker.tryAcquire()) {
            log.debug("Circuit '{}' is {}; short-circuiting to fallback '{}'",
                circuitBreaker.cacheName(), breaker.getState(), circuitBreaker.fallbackMethod());
            Object fallback = invokeFallback(joinPoint, circuitBreaker, null);
            if (fallback != NO_FALLBACK) {
                return fallback;
            }
            // No resolvable fallback: fall through to the method invocation
            // without touching breaker state.
            return joinPoint.proceed();
        }

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (elapsedMillis > circuitBreaker.timeout()) {
                log.debug("Circuit '{}': slow call ({} ms > {} ms) counted as failure",
                    circuitBreaker.cacheName(), elapsedMillis, circuitBreaker.timeout());
                breaker.recordFailure();
            } else {
                breaker.recordSuccess();
            }
            return result;
        } catch (Throwable t) {
            breaker.recordFailure();
            log.debug("Circuit '{}': invocation failed ({}); state is now {}",
                circuitBreaker.cacheName(), t.toString(), breaker.getState());
            Object fallback = invokeFallback(joinPoint, circuitBreaker, t);
            if (fallback != NO_FALLBACK) {
                return fallback;
            }
            throw t;
        }
    }

    /**
     * Current state of the breaker guarding the given cache (for tests/monitoring).
     *
     * @param cacheName the cache name
     * @return the state, or {@link State#CLOSED} if no breaker exists yet
     */
    public State getState(String cacheName) {
        CircuitBreakerState breaker = breakers.get(cacheName);
        return breaker == null ? State.CLOSED : breaker.getState();
    }

    /**
     * Consecutive failure count of the breaker guarding the given cache.
     *
     * @param cacheName the cache name
     * @return the consecutive failure count (0 if no breaker exists yet)
     */
    public int getFailureCount(String cacheName) {
        CircuitBreakerState breaker = breakers.get(cacheName);
        return breaker == null ? 0 : breaker.getFailureCount();
    }

    private static final Object NO_FALLBACK = new Object();

    /**
     * Invokes the fallback method, if resolvable.
     *
     * @return the fallback result, or {@link #NO_FALLBACK} when no fallback is
     *         configured/resolvable
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint,
                                  CacheCircuitBreaker circuitBreaker,
                                  Throwable failure) throws Throwable {
        String fallbackName = circuitBreaker.fallbackMethod();
        Object target = joinPoint.getTarget();
        if (fallbackName == null || fallbackName.isBlank() || target == null) {
            return NO_FALLBACK;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method fallback = resolveFallback(target.getClass(), fallbackName, method.getParameterTypes());
        if (fallback == null) {
            log.warn("Fallback method '{}' not found on {}; propagating original behavior",
                fallbackName, target.getClass().getName());
            return NO_FALLBACK;
        }
        try {
            fallback.setAccessible(true);
            return fallback.invoke(target, joinPoint.getArgs());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (failure != null && cause != null) {
                cause.addSuppressed(failure);
            }
            throw cause != null ? cause : e;
        }
    }

    private static Method resolveFallback(Class<?> type, String name, Class<?>[] parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            // fall through to a lenient search below
        }
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (candidate.getName().equals(name)
                    && candidate.getParameterCount() == parameterTypes.length) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Self-contained circuit breaker state machine.
     */
    static final class CircuitBreakerState {

        private final int failureThreshold;
        private final long waitDurationNanos;

        private State state = State.CLOSED;
        private int consecutiveFailures;
        private long openedAtNanos;

        CircuitBreakerState(int failureThreshold, long waitDurationMillis) {
            this.failureThreshold = Math.max(1, failureThreshold);
            this.waitDurationNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, waitDurationMillis));
        }

        /**
         * Decides whether an invocation may proceed, transitioning
         * OPEN to HALF_OPEN when the wait duration has elapsed.
         *
         * @return true if the invocation may proceed
         */
        synchronized boolean tryAcquire() {
            switch (state) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (System.nanoTime() - openedAtNanos >= waitDurationNanos) {
                        state = State.HALF_OPEN;
                        return true; // this caller is the single trial
                    }
                    return false;
                case HALF_OPEN:
                default:
                    return false; // a trial is already in flight
            }
        }

        synchronized void recordSuccess() {
            consecutiveFailures = 0;
            state = State.CLOSED;
        }

        synchronized void recordFailure() {
            if (state == State.HALF_OPEN) {
                open();
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                open();
            }
        }

        private void open() {
            state = State.OPEN;
            openedAtNanos = System.nanoTime();
            consecutiveFailures = 0;
        }

        synchronized State getState() {
            return state;
        }

        synchronized int getFailureCount() {
            return consecutiveFailures;
        }
    }
}
