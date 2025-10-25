package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply rate limiting to methods.
 * Uses Resilience4j rate limiter implementation.
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
     * @return limit for period
     */
    int limitForPeriod() default 10;

    /**
     * Period of limit refresh in milliseconds.
     * @return limit refresh period
     */
    long limitRefreshPeriod() default 1000L;

    /**
     * Default timeout duration in milliseconds.
     * @return timeout duration
     */
    long timeoutDuration() default 5000L;
}

