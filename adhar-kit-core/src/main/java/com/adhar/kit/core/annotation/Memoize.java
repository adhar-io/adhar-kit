package com.adhar.kit.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a method for memoization (caching results).
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Memoize(ttl = 300000) // Cache for 5 minutes
 * public BigDecimal getPrice(String productId) {
 *     return expensivePriceCalculation(productId);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Memoize {

    /**
     * Cache name.
     */
    String value() default "default";

    /**
     * Time-to-live in milliseconds (-1 = never expires).
     */
    long ttl() default -1;

    /**
     * Generate cache key from parameters.
     */
    boolean useAllParams() default true;
}

