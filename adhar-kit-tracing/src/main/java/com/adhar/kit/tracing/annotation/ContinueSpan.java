package com.adhar.kit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to continue the current span instead of creating a new one.
 * <p>
 * This annotation adds tags and events to the current active span without creating a new span.
 * Useful for adding additional context to an existing span.
 * </p>
 *
 * <pre>{@code
 * @ContinueSpan(tags = {"operation", "validate", "userId", "#{#userId}"})
 * public void validateUser(String userId) {
 *     // validation logic
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContinueSpan {

    /**
     * Additional tags to add to the current span.
     * Tags should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Event name to add to the span when method starts.
     *
     * @return the event name
     */
    String startEvent() default "";

    /**
     * Event name to add to the span when method completes.
     *
     * @return the event name
     */
    String endEvent() default "";
}
