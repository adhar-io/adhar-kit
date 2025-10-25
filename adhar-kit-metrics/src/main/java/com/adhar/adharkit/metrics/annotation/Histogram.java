package com.adhar.adharkit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for histogram metrics collection.
 * <p>
 * When applied to a method, this annotation will automatically record histogram metrics
 * for the method execution time or return value using Micrometer Timer with histogram support.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @Histogram(name = "request.processing.time", description = "Time to process requests", buckets = {0.1, 0.5, 1.0, 2.0, 5.0})
 * public Response processRequest(Request request) {
 *     // method implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Histogram {

    /**
     * The name of the histogram metric.
     * If not specified, a name will be generated based on the class and method name.
     *
     * @return the histogram name
     */
    String name() default "";

    /**
     * The description of the histogram metric.
     *
     * @return the histogram description
     */
    String description() default "";

    /**
     * Additional tags to be added to the histogram metric.
     * Should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * The base unit of measurement for the histogram.
     * Examples: "seconds", "bytes", "requests", etc.
     *
     * @return the base unit
     */
    String baseUnit() default "";

    /**
     * The histogram buckets (for Prometheus) or SLA boundaries.
     *
     * @return array of bucket boundaries
     */
    double[] buckets() default {};

    /**
     * Whether to record timing (execution time) or value (return value).
     * If true, records execution time. If false, records return value.
     *
     * @return true to record timing, false to record value
     */
    boolean recordTiming() default true;

    /**
     * The field or property name to extract the value from the return object when recordTiming=false.
     *
     * @return the field name to extract value from
     */
    String valueField() default "";

    /**
     * Whether to record for successful executions only.
     *
     * @return true to record only successful executions
     */
    boolean successOnly() default false;

    /**
     * Whether this histogram should be registered with the registry.
     * Set to false if you want to handle registration manually.
     *
     * @return true to auto-register the histogram
     */
    boolean autoRegister() default true;
}
