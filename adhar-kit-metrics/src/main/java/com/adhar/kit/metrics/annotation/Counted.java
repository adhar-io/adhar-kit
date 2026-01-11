package com.adhar.kit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for counter metrics collection.
 * <p>
 * When applied to a method, this annotation will automatically increment a counter
 * each time the method is invoked using Micrometer Counter.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @Counted(name = "user.service.login.attempts", description = "Number of login attempts")
 * public LoginResult login(String username, String password) {
 *     // method implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Counted {

    /**
     * The name of the counter metric.
     * If not specified, a name will be generated based on the class and method name.
     *
     * @return the counter name
     */
    String name() default "";

    /**
     * The description of the counter metric.
     *
     * @return the counter description
     */
    String description() default "";

    /**
     * Additional tags to be added to the counter metric.
     * Should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to count successful executions only.
     * If false, all method invocations will be counted regardless of outcome.
     *
     * @return true to count only successful executions
     */
    boolean successOnly() default false;

    /**
     * Whether to count failed executions separately.
     * If true, a separate counter with ".failures" suffix will be created.
     *
     * @return true to count failures separately
     */
    boolean recordFailures() default false;

    /**
     * Whether this counter should be registered with the registry.
     * Set to false if you want to handle registration manually.
     *
     * @return true to auto-register the counter
     */
    boolean autoRegister() default true;
}