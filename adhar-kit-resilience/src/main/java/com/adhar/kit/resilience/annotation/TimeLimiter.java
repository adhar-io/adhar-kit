package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply time limiter pattern to methods.
 * Uses Resilience4j time limiter to handle timeouts.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TimeLimiter {

    /**
     * Name of the time limiter instance.
     * @return time limiter name
     */
    String name() default "default";

    /**
     * Fallback method name in the same class.
     * @return fallback method name
     */
    String fallbackMethod() default "";

    /**
     * Timeout duration in milliseconds.
     * @return timeout duration
     */
    long timeoutDuration() default 1000L;

    /**
     * Whether to cancel the running future on timeout.
     * @return cancel running future
     */
    boolean cancelRunningFuture() default true;
}

