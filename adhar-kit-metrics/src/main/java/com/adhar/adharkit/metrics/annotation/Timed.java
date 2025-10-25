package com.adhar.adharkit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for timing metrics collection.
 * <p>
 * When applied to a method, this annotation will automatically record timing metrics
 * for method execution using Micrometer Timer.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @Timed(name = "user.service.findUser", description = "Time taken to find a user")
 * public User findUser(Long id) {
 *     // method implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {

    /**
     * The name of the timer metric.
     * If not specified, a name will be generated based on the class and method name.
     *
     * @return the timer name
     */
    String name() default "";

    /**
     * The description of the timer metric.
     *
     * @return the timer description
     */
    String description() default "";

    /**
     * Additional tags to be added to the timer metric.
     * Should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to record timing for successful executions only.
     * If false, timing will be recorded for all executions regardless of outcome.
     *
     * @return true to record only successful executions
     */
    boolean successOnly() default false;

    /**
     * The percentiles to calculate for the timer.
     *
     * @return array of percentile values (e.g., 0.5, 0.95, 0.99)
     */
    double[] percentiles() default {};

    /**
     * Whether this timer should be registered with the registry.
     * Set to false if you want to handle registration manually.
     *
     * @return true to auto-register the timer
     */
    boolean autoRegister() default true;
}