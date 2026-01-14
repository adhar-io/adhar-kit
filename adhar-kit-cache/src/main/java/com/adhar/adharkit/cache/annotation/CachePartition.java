package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Partition cache by tenant for multi-tenancy.
 *
 * <p><b>Example - Tenant-Specific Cache:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @CachePartition(
 *         cacheName = "orders",
 *         tenantParam = "tenantId",
 *         key = "#orderId"
 *     )
 *     public Order findOrder(String tenantId, String orderId) {
 *         return orderRepository.findByTenantAndId(tenantId, orderId);
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
public @interface CachePartition {

    /**
     * Base cache name.
     */
    String cacheName();

    /**
     * Tenant parameter name.
     */
    String tenantParam();

    /**
     * Cache key expression.
     */
    String key();

    /**
     * TTL per tenant (minutes).
     */
    long ttl() default 10;
}

