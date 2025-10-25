package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply circuit breaker pattern to methods.
 * Uses Resilience4j circuit breaker implementation.
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
}

