package com.adhar.adharkit.logging.annotation;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Logs business metrics and KPIs for monitoring and analytics.
 * Used to track business-relevant events and metrics.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LogMetrics {

    /** Metric name or category. */
    String metricName() default "";

    /** Custom operation description. */
    String value() default "";

    /** Log level for metric logs. */
    Level level() default Level.INFO;

    /** Include execution time as a metric. */
    boolean includeExecutionTime() default true;

    /** Include method arguments in metric logs. */
    boolean includeArgs() default false;

    /** Include method result in metric logs. */
    boolean includeResult() default false;

    /** Additional metric tags for categorization and filtering. */
    String[] tags() default {};

    /** Sample rate for metric logging (0.0 to 1.0). */
    double sampleRate() default 1.0;
}
