package com.adhar.adharkit.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records the annotated method's execution time with the
 * {@link com.adhar.adharkit.logging.performance.PerformanceLogger}: aggregated statistics
 * (count/min/max/avg/failures) plus a WARN PERFORMANCE event when the execution exceeds the slow
 * threshold.
 *
 * <pre>{@code
 * @TrackPerformance(value = "db.orders.query", slowThresholdMs = 250)
 * public List<Order> findOrders(...) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TrackPerformance {

    /** Operation name; defaults to {@code ClassName.methodName}. */
    String value() default "";

    /**
     * Slow threshold in milliseconds for this operation. Negative means "use the global
     * {@code adhar.logging.performance.slow-threshold-ms}".
     */
    long slowThresholdMs() default -1;
}
