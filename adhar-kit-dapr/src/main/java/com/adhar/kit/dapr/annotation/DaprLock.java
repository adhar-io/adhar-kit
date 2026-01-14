package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to acquire a distributed lock before execution.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprLock(storeName = "lockstore", resourceId = "#orderId", lockOwner = "order-service")
 * public Order processOrder(String orderId) {
 *     // Only one instance processes this order at a time
 *     return orderService.process(orderId);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprLock {

    /**
     * Lock store name.
     */
    String storeName() default "lockstore";

    /**
     * Resource ID to lock (supports SpEL expressions).
     */
    String resourceId();

    /**
     * Lock owner identifier.
     */
    String lockOwner();

    /**
     * Lock expiry in seconds.
     */
    int expiryInSeconds() default 30;

    /**
     * Fail if lock cannot be acquired.
     */
    boolean failOnLockFailure() default true;
}

