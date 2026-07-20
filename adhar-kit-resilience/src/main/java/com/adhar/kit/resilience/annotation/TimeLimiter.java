package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply time limiter pattern to methods.
 * Uses Resilience4j time limiter to handle timeouts.
 *
 * <p>Methods returning {@code CompletableFuture}/{@code CompletionStage} are timed out
 * asynchronously; synchronous methods are executed on a separate thread and awaited.</p>
 *
 * <p>Numeric attributes default to {@code -1}, which means "use the default (or
 * property-driven) configuration of the registry". Any explicitly set attribute is
 * honored when the named time limiter instance is first created. If an instance
 * with the same name already exists (e.g. configured via
 * {@code adhar.resilience.time-limiter.<name>.*} properties), the existing
 * configuration wins.</p>
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
     * {@code -1} means use default config (1000ms).
     * @return timeout duration
     */
    long timeoutDuration() default -1L;

    /**
     * Whether to cancel the running future on timeout.
     * @return cancel running future
     */
    boolean cancelRunningFuture() default true;
}
