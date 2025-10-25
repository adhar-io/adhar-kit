package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply bulkhead pattern to methods.
 * Uses Resilience4j bulkhead implementation for limiting concurrent executions.
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
     * @return max concurrent calls
     */
    int maxConcurrentCalls() default 25;

    /**
     * Maximum wait duration in milliseconds.
     * @return max wait duration
     */
    long maxWaitDuration() default 0L;

    /**
     * Type of bulkhead (SEMAPHORE or THREADPOOL).
     * @return bulkhead type
     */
    Type type() default Type.SEMAPHORE;

    enum Type {
        SEMAPHORE,
        THREADPOOL
    }
}

