package com.adhar.kit.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a method for asynchronous execution.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Async
 * public CompletableFuture<Order> processOrder(OrderRequest request) {
 *     Order order = orderService.create(request);
 *     return CompletableFuture.completedFuture(order);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Async {

    /**
     * Thread pool name (optional).
     */
    String value() default "default";

    /**
     * Timeout in milliseconds (0 = no timeout).
     */
    long timeout() default 0;
}

