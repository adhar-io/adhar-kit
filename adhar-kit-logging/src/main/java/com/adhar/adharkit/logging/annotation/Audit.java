package com.adhar.adharkit.logging.annotation;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Logs audit events for security and compliance purposes.
 * Typically used for business-critical operations that need to be audited.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Audit {

    /** Audit event type/category. */
    String eventType() default "";

    /** Custom audit message or operation description. */
    String value() default "";

    /** Log level for audit logs. */
    Level level() default Level.INFO;

    /** Include method arguments in audit logs. */
    boolean includeArgs() default true;

    /** Include method result in audit logs. */
    boolean includeResult() default false;

    /** Include user context (if available). */
    boolean includeUser() default true;

    /** Additional audit tags for categorization. */
    String[] tags() default {};
}
