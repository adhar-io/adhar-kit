package com.adhar.adharkit.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tracks the annotated method as an application operation: an OPERATION
 * {@link com.adhar.adharkit.logging.event.AppLogEvent} is published with the method's duration,
 * outcome (SUCCESS/FAILURE) and optional arguments/result.
 *
 * <pre>{@code
 * @LogOperation(value = "order.fulfil", category = "order", includeArgs = true)
 * public Shipment fulfil(String orderId) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LogOperation {

    /** Operation name; defaults to {@code ClassName.methodName}. */
    String value() default "";

    /** Operation category (e.g. subsystem or domain name). */
    String category() default "";

    /** Include (masked) method arguments in the event metadata. */
    boolean includeArgs() default false;

    /** Include the (masked) method result in the event metadata. */
    boolean includeResult() default false;

    /** Additional categorization tags. */
    String[] tags() default {};
}
