package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as idempotent.
 *
 * <p>Idempotent operations can be called multiple times with the same
 * parameters without changing the result beyond the initial application.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Service
 * public class PaymentService {
 *
 *     @Idempotent(key = "#paymentId", ttl = 300)
 *     public PaymentResult processPayment(String paymentId, Money amount) {
 *         // Process payment - safe to retry
 *         return paymentGateway.charge(amount);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * Idempotency key expression (SpEL).
     */
    String key();

    /**
     * Time-to-live for idempotency record (seconds).
     */
    long ttl() default 300;

    /**
     * Storage backend for idempotency keys.
     */
    String storage() default "default";
}

