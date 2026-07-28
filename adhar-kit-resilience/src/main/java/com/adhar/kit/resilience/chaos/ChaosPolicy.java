package com.adhar.kit.resilience.chaos;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chaos-engineering hooks applied inside the resilience execution chain.
 *
 * <p>Disabled by default. When enabled, the policy can, for matching methods:</p>
 * <ul>
 *   <li><b>Latency injection</b> - sleep a random duration within a configured
 *       millisecond range before the real invocation proceeds;</li>
 *   <li><b>Error injection</b> - throw a {@link ChaosInjectedException} with a
 *       configured probability, so downstream retry / circuit-breaker behaviour can be
 *       exercised.</li>
 * </ul>
 *
 * <p>A per-method-name matcher restricts injection to specific methods: when the
 * {@code includedMethods} list is empty every annotated method matches, otherwise a
 * method matches when its simple name equals, or contains, any configured entry.</p>
 *
 * <p>Because injection happens at the innermost point of the decorator chain (immediately
 * before the target method body), injected errors and latency are observed by the retry,
 * circuit-breaker and time-limiter decorators exactly as real failures would be.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ChaosPolicy {

    private final boolean enabled;
    private final boolean latencyEnabled;
    private final long minLatencyMs;
    private final long maxLatencyMs;
    private final boolean errorEnabled;
    private final double errorProbability;
    private final List<String> includedMethods;

    /**
     * Creates a chaos policy.
     *
     * @param enabled          master switch; when {@code false} {@link #apply(String)} is a no-op
     * @param latencyEnabled   whether latency injection is active
     * @param minLatencyMs     lower bound of injected latency in milliseconds
     * @param maxLatencyMs     upper bound of injected latency in milliseconds
     * @param errorEnabled     whether error injection is active
     * @param errorProbability probability in {@code [0,1]} of injecting an error
     * @param includedMethods  method-name matchers; empty means "match all"
     */
    public ChaosPolicy(boolean enabled, boolean latencyEnabled, long minLatencyMs, long maxLatencyMs,
                       boolean errorEnabled, double errorProbability, List<String> includedMethods) {
        this.enabled = enabled;
        this.latencyEnabled = latencyEnabled;
        this.minLatencyMs = Math.max(0, minLatencyMs);
        this.maxLatencyMs = Math.max(this.minLatencyMs, maxLatencyMs);
        this.errorEnabled = errorEnabled;
        this.errorProbability = errorProbability;
        this.includedMethods = includedMethods == null ? List.of() : List.copyOf(includedMethods);
    }

    /** @return whether this policy is active */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Applies configured chaos (latency then error) for the given method name.
     *
     * @param methodName simple name of the intercepted method
     * @throws ChaosInjectedException when error injection fires for this call
     */
    public void apply(String methodName) {
        if (!enabled || !matches(methodName)) {
            return;
        }
        if (latencyEnabled) {
            injectLatency(methodName);
        }
        if (errorEnabled && errorProbability > 0 && ThreadLocalRandom.current().nextDouble() < errorProbability) {
            log.warn("chaos.inject type=error method={} probability={}", methodName, errorProbability);
            throw new ChaosInjectedException(
                    "Chaos error injected for method '" + methodName + "'");
        }
    }

    private boolean matches(String methodName) {
        if (includedMethods.isEmpty()) {
            return true;
        }
        if (methodName == null) {
            return false;
        }
        for (String candidate : includedMethods) {
            if (candidate != null && !candidate.isEmpty()
                    && (methodName.equals(candidate) || methodName.contains(candidate))) {
                return true;
            }
        }
        return false;
    }

    private void injectLatency(String methodName) {
        long delay = maxLatencyMs <= minLatencyMs
                ? minLatencyMs
                : ThreadLocalRandom.current().nextLong(minLatencyMs, maxLatencyMs + 1);
        if (delay <= 0) {
            return;
        }
        log.warn("chaos.inject type=latency method={} delayMs={}", methodName, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChaosInjectedException("Chaos latency injection interrupted", e);
        }
    }

    /** Unchecked exception raised by {@link ChaosPolicy} when an error is injected. */
    public static class ChaosInjectedException extends RuntimeException {
        public ChaosInjectedException(String message) {
            super(message);
        }

        public ChaosInjectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
