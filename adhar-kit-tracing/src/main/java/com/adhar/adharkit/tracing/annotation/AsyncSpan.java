package com.adhar.adharkit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tracing asynchronous operations.
 * <p>
 * This annotation creates a span for asynchronous operations and ensures proper
 * trace context propagation across thread boundaries.
 * </p>
 *
 * <pre>{@code
 * @AsyncSpan("async.user.processing")
 * @Async
 * public CompletableFuture<User> processUserAsync(User user) {
 *     // async processing logic
 *     return CompletableFuture.completedFuture(processedUser);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncSpan {

    /**
     * The name of the span. If empty, the method name will be used.
     *
     * @return the span name
     */
    String value() default "";

    /**
     * Whether to propagate the current trace context to the async operation.
     *
     * @return true to propagate context
     */
    boolean propagateContext() default true;

    /**
     * Additional tags to add to the span.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};
}
