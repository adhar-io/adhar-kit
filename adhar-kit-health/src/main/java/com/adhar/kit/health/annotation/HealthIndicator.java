package com.adhar.kit.health.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a custom health indicator.
 *
 * <p>Automatically registers the health indicator with the health registry.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @HealthIndicator(name = "payment-gateway")
 * public class PaymentGatewayHealthIndicator implements AdharHealthIndicator {
 *
 *     @Override
 *     public Health check() {
 *         try {
 *             // Check payment gateway connectivity
 *             paymentGateway.ping();
 *             return Health.up()
 *                 .withDetail("gateway", "available")
 *                 .build();
 *         } catch (Exception e) {
 *             return Health.down(e)
 *                 .withDetail("gateway", "unavailable")
 *                 .build();
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HealthIndicator {

    /**
     * Health indicator name.
     */
    String name() default "";

    /**
     * Enable this indicator.
     */
    boolean enabled() default true;

    /**
     * Indicator priority (lower = higher priority).
     */
    int priority() default 100;

    /**
     * Timeout in milliseconds.
     */
    long timeout() default 5000;
}

