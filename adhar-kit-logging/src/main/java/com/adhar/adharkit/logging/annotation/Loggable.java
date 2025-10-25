package com.adhar.adharkit.logging.annotation;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for common structured logging via AOP.
 *
 * Features:
 * - Logs method entry and exit
 * - Optionally logs arguments and/or result
 * - Supports sampling
 * - Supports masking via @Sensitive on parameters or by field names
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Loggable {

    /** Optional custom operation name; defaults to method signature. */
    String value() default "";

    /** Log level used for entry/exit logs. */
    Level level() default Level.INFO;

    /** Log arguments on entry (safe for non-PII). */
    boolean logArgs() default false;

    /** Log the returned result on exit (safe for non-PII). */
    boolean logResult() default false;

    /**
     * List of argument field names to mask when logging structured args.
     * Applies when args are Map-like or JavaBeans; simple strings will be completely masked.
     */
    String[] maskFields() default {};

    /**
     * Fraction of calls to log (0.0 to 1.0). 1.0 logs every call.
     */
    double sampleRate() default 1.0d;
}
