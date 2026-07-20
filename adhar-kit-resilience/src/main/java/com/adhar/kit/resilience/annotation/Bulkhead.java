package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply bulkhead pattern to methods.
 * Uses Resilience4j bulkhead implementation for limiting concurrent executions.
 *
 * <p>Numeric attributes default to {@code -1}, which means "use the default (or
 * property-driven) configuration of the registry". Any explicitly set attribute is
 * honored when the named bulkhead instance is first created. If an instance with
 * the same name already exists (e.g. configured via
 * {@code adhar.resilience.bulkhead.<name>.*} properties), the existing
 * configuration wins.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Bulkhead {

    /**
     * Name of the bulkhead instance.
     * @return bulkhead name
     */
    String name() default "default";

    /**
     * Fallback method name in the same class.
     * @return fallback method name
     */
    String fallbackMethod() default "";

    /**
     * Maximum number of concurrent calls.
     * {@code -1} means use default config (25).
     * @return max concurrent calls
     */
    int maxConcurrentCalls() default -1;

    /**
     * Maximum wait duration in milliseconds when the bulkhead is full.
     * {@code -1} means use default config (0ms).
     * @return max wait duration
     */
    long maxWaitDuration() default -1L;

    /**
     * Type of bulkhead (SEMAPHORE or THREADPOOL).
     * <p>Note: only SEMAPHORE isolation is currently applied by the aspect;
     * THREADPOOL falls back to semaphore isolation.</p>
     * @return bulkhead type
     */
    Type type() default Type.SEMAPHORE;

    enum Type {
        SEMAPHORE,
        THREADPOOL
    }
}
