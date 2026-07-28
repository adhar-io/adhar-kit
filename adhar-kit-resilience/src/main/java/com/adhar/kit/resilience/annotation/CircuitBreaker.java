package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply circuit breaker pattern to methods.
 * Uses Resilience4j circuit breaker implementation.
 *
 * <p>Numeric attributes default to {@code -1}, which means "use the default (or
 * property-driven) configuration of the registry". Any explicitly set attribute is
 * honored when the named circuit breaker instance is first created. If an instance
 * with the same name already exists (e.g. configured via
 * {@code adhar.resilience.circuit-breaker.<name>.*} properties), the existing
 * configuration wins.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreaker {

    /**
     * Name of the circuit breaker instance.
     * @return circuit breaker name
     */
    String name() default "default";

    /**
     * Fallback method name in the same class.
     * Method signature must match the annotated method.
     * @return fallback method name
     */
    String fallbackMethod() default "";

    /**
     * Enables the "last known good" fallback cache for this method. When {@code true},
     * successful results are cached and, if a later call fails (e.g. while the breaker is
     * open) with no {@link #fallbackMethod()} configured, the most recent cached result is
     * returned instead of propagating the failure. Requires the fallback cache bean.
     * @return whether the fallback cache is enabled for this method
     */
    boolean fallbackCache() default false;

    /**
     * Failure rate threshold in percentage. {@code -1} means use default config.
     * @return failure rate threshold
     */
    float failureRateThreshold() default -1f;

    /**
     * Slow call rate threshold in percentage. {@code -1} means use default config.
     * @return slow call rate threshold
     */
    float slowCallRateThreshold() default -1f;

    /**
     * Duration threshold in milliseconds above which calls are considered slow.
     * {@code -1} means use default config.
     * @return slow call duration threshold in milliseconds
     */
    long slowCallDurationThreshold() default -1L;

    /**
     * Size of the sliding window used to record call outcomes.
     * {@code -1} means use default config.
     * @return sliding window size
     */
    int slidingWindowSize() default -1;

    /**
     * Minimum number of calls required before the failure rate is calculated.
     * {@code -1} means use default config.
     * @return minimum number of calls
     */
    int minimumNumberOfCalls() default -1;

    /**
     * Number of permitted calls when the circuit breaker is half-open.
     * {@code -1} means use default config.
     * @return permitted number of calls in half-open state
     */
    int permittedNumberOfCallsInHalfOpenState() default -1;

    /**
     * Wait duration in milliseconds before transitioning from open to half-open.
     * {@code -1} means use default config.
     * @return wait duration in open state in milliseconds
     */
    long waitDurationInOpenState() default -1L;
}
