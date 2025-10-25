package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply retry pattern to methods.
 * Uses Resilience4j retry implementation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

    /**
     * Name of the retry instance.
     * @return retry name
     */
    String name() default "default";

    /**
     * Fallback method name in the same class.
     * @return fallback method name
     */
    String fallbackMethod() default "";

    /**
     * Maximum number of retry attempts.
     * @return max attempts
     */
    int maxAttempts() default 3;

    /**
     * Wait duration between retries in milliseconds.
     * @return wait duration
     */
    long waitDuration() default 500L;
}

