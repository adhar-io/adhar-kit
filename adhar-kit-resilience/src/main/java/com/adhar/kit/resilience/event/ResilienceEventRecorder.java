package com.adhar.kit.resilience.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe in-memory recorder for Resilience4j events.
 *
 * <p>Counts circuit breaker state transitions, retry attempts, rate limiter
 * rejections, bulkhead rejections and time limiter timeouts per instance name.
 * Exposed through {@code ResilienceMetricsService} and the {@code resilience}
 * actuator endpoint.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ResilienceEventRecorder {

    private final ConcurrentMap<String, LongAdder> circuitBreakerTransitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> circuitBreakerLastTransitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> retryAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> rateLimiterRejections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> bulkheadRejections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> timeLimiterTimeouts = new ConcurrentHashMap<>();

    /**
     * Records a circuit breaker state transition.
     *
     * @param name circuit breaker name
     * @param fromState previous state
     * @param toState new state
     */
    public void recordCircuitBreakerTransition(String name, String fromState, String toState) {
        increment(circuitBreakerTransitions, name);
        circuitBreakerLastTransitions.put(name, fromState + "->" + toState);
    }

    /**
     * Records a retry attempt.
     *
     * @param name retry instance name
     */
    public void recordRetryAttempt(String name) {
        increment(retryAttempts, name);
    }

    /**
     * Records a rate limiter rejection (permission acquisition failure).
     *
     * @param name rate limiter name
     */
    public void recordRateLimiterRejection(String name) {
        increment(rateLimiterRejections, name);
    }

    /**
     * Records a bulkhead call rejection.
     *
     * @param name bulkhead name
     */
    public void recordBulkheadRejection(String name) {
        increment(bulkheadRejections, name);
    }

    /**
     * Records a time limiter timeout.
     *
     * @param name time limiter name
     */
    public void recordTimeLimiterTimeout(String name) {
        increment(timeLimiterTimeouts, name);
    }

    /** @return state transition count per circuit breaker name */
    public Map<String, Long> getCircuitBreakerTransitionCounts() {
        return snapshot(circuitBreakerTransitions);
    }

    /** @return last observed state transition (e.g. {@code CLOSED->OPEN}) per circuit breaker name */
    public Map<String, String> getCircuitBreakerLastTransitions() {
        return new LinkedHashMap<>(circuitBreakerLastTransitions);
    }

    /** @return retry attempt count per retry name */
    public Map<String, Long> getRetryAttemptCounts() {
        return snapshot(retryAttempts);
    }

    /** @return rejection count per rate limiter name */
    public Map<String, Long> getRateLimiterRejectionCounts() {
        return snapshot(rateLimiterRejections);
    }

    /** @return rejection count per bulkhead name */
    public Map<String, Long> getBulkheadRejectionCounts() {
        return snapshot(bulkheadRejections);
    }

    /** @return timeout count per time limiter name */
    public Map<String, Long> getTimeLimiterTimeoutCounts() {
        return snapshot(timeLimiterTimeouts);
    }

    /** Clears all recorded events. */
    public void reset() {
        circuitBreakerTransitions.clear();
        circuitBreakerLastTransitions.clear();
        retryAttempts.clear();
        rateLimiterRejections.clear();
        bulkheadRejections.clear();
        timeLimiterTimeouts.clear();
    }

    private static void increment(ConcurrentMap<String, LongAdder> counters, String name) {
        counters.computeIfAbsent(name, key -> new LongAdder()).increment();
    }

    private static Map<String, Long> snapshot(ConcurrentMap<String, LongAdder> counters) {
        Map<String, Long> result = new LinkedHashMap<>();
        counters.forEach((name, adder) -> result.put(name, adder.sum()));
        return result;
    }
}
