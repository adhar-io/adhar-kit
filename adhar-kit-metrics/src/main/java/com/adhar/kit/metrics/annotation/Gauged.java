package com.adhar.kit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for gauge metrics collection.
 * <p>
 * When applied to a method, this annotation will automatically create a gauge metric
 * that tracks the return value of the method using Micrometer Gauge.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @Gauged(name = "user.service.active.sessions", description = "Number of active user sessions")
 * public int getActiveSessionCount() {
 *     return sessionManager.getActiveSessionCount();
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Gauged {

    /**
     * The name of the gauge metric.
     * If not specified, a name will be generated based on the class and method name.
     *
     * @return the gauge name
     */
    String name() default "";

    /**
     * The description of the gauge metric.
     *
     * @return the gauge description
     */
    String description() default "";

    /**
     * Additional tags to be added to the gauge metric.
     * Should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * The base unit of measurement for the gauge.
     * Examples: "bytes", "seconds", "percent", etc.
     *
     * @return the base unit
     */
    String baseUnit() default "";

    /**
     * Whether this gauge should be registered with the registry.
     * Set to false if you want to handle registration manually.
     *
     * @return true to auto-register the gauge
     */
    boolean autoRegister() default true;

    /**
     * Whether to update the gauge value on every method call.
     * If false, the gauge will only be created once and not updated automatically.
     *
     * @return true to update on every call
     */
    boolean updateOnCall() default true;
}