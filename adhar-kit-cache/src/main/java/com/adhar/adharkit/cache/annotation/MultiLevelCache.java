package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Multi-level caching with L1 (local) and L2 (shared) caches.
 *
 * <p><b>Example - Two-Level Cache:</b></p>
 * <pre>{@code
 * @Service
 * public class ProductService {
 *
 *     @MultiLevelCache(
 *         l1Cache = "products-local",
 *         l2Cache = "products-redis",
 *         l1Ttl = 5,
 *         l2Ttl = 60,
 *         key = "#productId"
 *     )
 *     public Product findProduct(String productId) {
 *         return productRepository.findById(productId);
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
public @interface MultiLevelCache {

    /**
     * L1 cache name (local/in-memory).
     */
    String l1Cache();

    /**
     * L2 cache name (distributed/shared).
     */
    String l2Cache();

    /**
     * Cache key expression.
     */
    String key();

    /**
     * L1 TTL (minutes).
     */
    long l1Ttl() default 5;

    /**
     * L2 TTL (minutes).
     */
    long l2Ttl() default 60;

    /**
     * Write to both levels.
     */
    boolean writeThrough() default true;
}

