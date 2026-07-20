package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply rate limiting to methods.
 * Uses Resilience4j rate limiter implementation.
 *
 * <p>Numeric attributes default to {@code -1}, which means "use the default (or
 * property-driven) configuration of the registry". Any explicitly set attribute is
 * honored when the named rate limiter instance is first created. If an instance
 * with the same name already exists (e.g. configured via
 * {@code adhar.resilience.rate-limiter.<name>.*} properties), the existing
 * configuration wins.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Name of the rate limiter instance.
     * @return rate limiter name
     */
    String name() default "default";

    /**
     * Fallback method name in the same class.
     * @return fallback method name
     */
    String fallbackMethod() default "";

    /**
     * Number of permissions available during one limit refresh period.
     * {@code -1} means use default config (10).
     * @return limit for period
     */
    int limitForPeriod() default -1;

    /**
     * Period of limit refresh in milliseconds.
     * {@code -1} means use default config (1000ms).
     * @return limit refresh period
     */
    long limitRefreshPeriod() default -1L;

    /**
     * Maximum time in milliseconds to wait for a permission.
     * {@code -1} means use default config (5000ms).
     * @return timeout duration
     */
    long timeoutDuration() default -1L;
}
