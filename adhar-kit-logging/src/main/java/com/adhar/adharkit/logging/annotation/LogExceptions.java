package com.adhar.adharkit.logging.annotation;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Logs exceptions thrown by methods with detailed context.
 * Useful for centralized exception logging and monitoring.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LogExceptions {

    /** Custom operation name for exception logs. */
    String value() default "";

    /** Log level for exception logs. */
    Level level() default Level.ERROR;

    /** Include method arguments in exception logs. */
    boolean includeArgs() default true;

    /** Include stack trace in the log. */
    boolean includeStackTrace() default true;

    /** Only log specific exception types (empty means all exceptions). */
    Class<? extends Throwable>[] only() default {};

    /** Exclude specific exception types from logging. */
    Class<? extends Throwable>[] exclude() default {};
}
