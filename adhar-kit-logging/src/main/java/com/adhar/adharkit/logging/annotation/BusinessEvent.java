package com.adhar.adharkit.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Publishes a BUSINESS {@link com.adhar.adharkit.logging.event.AppLogEvent} when the annotated
 * method completes: SUCCESS on normal return, FAILURE (with error details) when it throws.
 *
 * <pre>{@code
 * @BusinessEvent(value = "ORDER_PLACED", category = "order")
 * public Order placeOrder(Cart cart) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessEvent {

    /** Business event name (e.g. "ORDER_PLACED"); defaults to the method name. */
    String value() default "";

    /** Business domain/category (e.g. "order"). */
    String category() default "";

    /** Include (masked) method arguments in the event metadata. */
    boolean includeArgs() default false;

    /** Include the (masked) method result in the event metadata. */
    boolean includeResult() default false;

    /** Additional categorization tags. */
    String[] tags() default {};
}
