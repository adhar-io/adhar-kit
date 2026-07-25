package com.adhar.kit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tagging a method parameter's value onto the span created by one of the
 * tracing annotations ({@link NewSpan}, {@link ContinueSpan}, {@link DatabaseSpan},
 * {@link HttpClientSpan}, {@link MessagingSpan}, {@link AsyncSpan}).
 * <p>
 * When {@link TracingAspect} (see {@code com.adhar.kit.tracing.aspect.TracingAspect}) creates
 * or continues a span for a method annotated with one of the above, it also inspects the
 * method's parameters for {@code @SpanTag} and adds a tag for each one using either the
 * parameter's {@code toString()} value, or the result of evaluating {@link #expression()}
 * (a SpEL expression evaluated with the parameter value as the root object, and all method
 * parameters available as {@code #paramName} variables).
 * </p>
 *
 * <pre>{@code
 * @NewSpan("user.create")
 * public User createUser(@SpanTag("user.email") String email) {
 *     return userRepository.save(new User(email));
 * }
 *
 * @NewSpan("order.process")
 * public Order processOrder(@SpanTag(value = "order.id", expression = "id") Order order) {
 *     return process(order);
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpanTag {

    /**
     * The tag name/key. If empty, the parameter name will be used (requires the code to be
     * compiled with {@code -parameters}, otherwise a synthetic name such as {@code arg0} will
     * be used).
     *
     * @return the tag name
     */
    String value() default "";

    /**
     * Optional SpEL expression evaluated with the annotated parameter's value as the root
     * object, to derive the tag value (e.g. {@code "id"} to tag a property of the parameter).
     * If empty, {@code String.valueOf(parameterValue)} is used.
     *
     * @return the SpEL expression
     */
    String expression() default "";
}
