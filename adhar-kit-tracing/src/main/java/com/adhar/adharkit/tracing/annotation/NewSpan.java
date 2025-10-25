package com.adhar.adharkit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to create a new span for the annotated method.
 * <p>
 * This annotation creates a new span with the specified name and optional tags.
 * The span will automatically capture method execution time and any exceptions thrown.
 * </p>
 *
 * <pre>{@code
 * @NewSpan("user.service.find")
 * public User findUser(String userId) {
 *     return userRepository.findById(userId);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NewSpan {

    /**
     * The name of the span. If empty, the method name will be used.
     *
     * @return the span name
     */
    String value() default "";

    /**
     * Additional tags to add to the span.
     * Tags should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};
}
