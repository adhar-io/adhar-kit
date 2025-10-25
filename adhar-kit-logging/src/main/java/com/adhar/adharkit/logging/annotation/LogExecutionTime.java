package com.adhar.adharkit.logging.annotation;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Logs execution time of methods for performance monitoring.
 * <p>
 * This annotation provides structured logging of method execution times with optional
 * thresholds and sampling capabilities. It's particularly useful for identifying
 * performance bottlenecks and monitoring system performance.
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Basic execution time logging
 * {@code @LogExecutionTime}
 * public void processData() { ... }
 *
 * // With threshold - only log if execution time exceeds 100ms
 * {@code @LogExecutionTime(thresholdMs = 100)}
 * public void expensiveOperation() { ... }
 *
 * // With sampling - only log 10% of calls for high-volume methods
 * {@code @LogExecutionTime(sampleRate = 0.1)}
 * public void highVolumeMethod() { ... }
 *
 * // Complete configuration
 * {@code @LogExecutionTime(
 *     value = "user-data-processing",
 *     level = Level.INFO,
 *     thresholdMs = 500,
 *     includeArgs = true,
 *     sampleRate = 0.5
 * )}
 * public UserData processUserData(String userId, UserRequest request) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LogExecutionTime {

    /**
     * Custom operation name for the timing log.
     * If empty, defaults to ClassName.methodName format.
     */
    String value() default "";

    /**
     * Log level for the execution time log.
     * Default is INFO level for visibility in most logging configurations.
     */
    Level level() default Level.INFO;

    /**
     * Threshold in milliseconds - only log if execution time exceeds this value.
     * Set to 0 to log all executions regardless of time.
     * Useful for filtering out fast operations and focusing on performance issues.
     */
    long thresholdMs() default 0;

    /**
     * Include method arguments in the timing log.
     * Be cautious with sensitive data - consider using {@code @Sensitive} annotation.
     */
    boolean includeArgs() default false;

    /**
     * Sample rate for logging (0.0 to 1.0).
     * 1.0 logs every call, 0.1 logs 10% of calls.
     * Useful for high-volume methods to reduce log noise while maintaining visibility.
     */
    double sampleRate() default 1.0;
}
