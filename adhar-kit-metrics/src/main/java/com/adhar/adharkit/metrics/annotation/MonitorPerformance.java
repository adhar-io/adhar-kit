package com.adhar.adharkit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for performance monitoring metrics collection.
 * <p>
 * This annotation combines multiple metrics (timing, counting, and monitoring)
 * to provide comprehensive performance insights for critical methods.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @MonitorPerformance(
 *     name = "user.authentication",
 *     description = "User authentication performance",
 *     recordThroughput = true,
 *     recordErrorRate = true,
 *     alertOnSlowExecution = true,
 *     slowThresholdMs = 5000
 * )
 * public AuthResult authenticate(String username, String password) {
 *     // method implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitorPerformance {

    /**
     * The base name for the performance metrics.
     * Multiple metrics will be created with this base name.
     *
     * @return the base metric name
     */
    String name() default "";

    /**
     * The description of the performance monitoring.
     *
     * @return the monitoring description
     */
    String description() default "";

    /**
     * Additional tags to be added to all performance metrics.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to record throughput (requests per second) metrics.
     *
     * @return true to record throughput metrics
     */
    boolean recordThroughput() default true;

    /**
     * Whether to record error rate metrics.
     *
     * @return true to record error rate metrics
     */
    boolean recordErrorRate() default true;

    /**
     * Whether to record response time percentiles.
     *
     * @return true to record percentile metrics
     */
    boolean recordPercentiles() default true;

    /**
     * The percentiles to calculate for response times.
     *
     * @return array of percentile values
     */
    double[] percentiles() default {0.5, 0.75, 0.95, 0.99};

    /**
     * Whether to create an alert metric for slow executions.
     *
     * @return true to monitor slow executions
     */
    boolean alertOnSlowExecution() default false;

    /**
     * The threshold in milliseconds for considering an execution as slow.
     *
     * @return the slow execution threshold in ms
     */
    long slowThresholdMs() default 1000;

    /**
     * Whether to record method parameter metrics (e.g., parameter sizes).
     *
     * @return true to record parameter metrics
     */
    boolean recordParameters() default false;

    /**
     * Whether to record return value metrics (e.g., result sizes).
     *
     * @return true to record return value metrics
     */
    boolean recordReturnValue() default false;
}
