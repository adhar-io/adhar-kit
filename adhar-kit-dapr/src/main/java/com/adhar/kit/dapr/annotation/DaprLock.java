package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to acquire a distributed lock before execution.
 *
 * <p>Enforced by {@code DaprLockAspect}. The lock expires after {@code expiryInSeconds} even if
 * the holder dies, so choose an expiry longer than the method can run.</p>
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
     * Lock store name; empty for the service default ({@code adhar.dapr.lock-store}).
     */
    String storeName() default "";

    /**
     * Resource ID to lock (supports SpEL expressions).
     */
    String resourceId();

    /**
     * Prefix of the lock owner; the kit appends a per-invocation id so replicas never release each
     * other's locks. Empty for the app id.
     */
    String lockOwner() default "";

    /**
     * Lock expiry in seconds.
     */
    int expiryInSeconds() default 30;

    /**
     * Throw {@code DaprLockNotAcquiredException} when the lock is held elsewhere; otherwise the
     * method is skipped and returns {@code null} (or the primitive default).
     */
    boolean failOnLockFailure() default true;
}

