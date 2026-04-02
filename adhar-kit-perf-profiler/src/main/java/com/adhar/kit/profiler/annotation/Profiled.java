package com.adhar.kit.profiler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class for performance profiling.
 * When applied to a class, all public methods are profiled.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Profiled {

    /** Custom metric name. Defaults to class.method format. */
    String value() default "";

    /** Whether to log slow executions. */
    boolean logSlow() default true;

    /** Threshold in milliseconds to consider an execution slow. */
    long slowThresholdMs() default 500;

    /** Whether to record detailed histogram metrics. */
    boolean histogram() default false;
}
