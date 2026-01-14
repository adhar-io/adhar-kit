package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for declarative caching.
 *
 * <p>Automatically caches method return values. Supports TTL, conditional caching,
 * and dynamic key generation.</p>
 *
 * <p><b>Example - Basic Caching:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @Cacheable(cacheName = "users", key = "#userId")
 *     public User findById(String userId) {
 *         return userRepository.findById(userId);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - With TTL:</b></p>
 * <pre>{@code
 * @Cacheable(
 *     cacheName = "products",
 *     key = "#productId",
 *     ttl = 10,
 *     ttlUnit = TimeUnit.MINUTES
 * )
 * public Product findProduct(String productId) {
 *     return productRepository.findById(productId);
 * }
 * }</pre>
 *
 * <p><b>Example - Conditional Caching:</b></p>
 * <pre>{@code
 * @Cacheable(
 *     cacheName = "orders",
 *     key = "#orderId",
 *     condition = "#result != null && #result.status != 'PENDING'"
 * )
 * public Order findOrder(String orderId) {
 *     return orderRepository.findById(orderId);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /**
     * Name of the cache to use.
     *
     * <p>If not specified, uses method name as cache name.</p>
     */
    String cacheName() default "";

    /**
     * Cache key expression.
     *
     * <p>Supports SpEL expressions:</p>
     * <ul>
     *   <li><code>#paramName</code> - Method parameter</li>
     *   <li><code>#result</code> - Method return value (in condition)</li>
     *   <li><code>#root.methodName</code> - Method name</li>
     *   <li><code>#root.target</code> - Target object</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <pre>{@code
     * key = "#userId"                    // Single parameter
     * key = "#user.id"                   // Object property
     * key = "#userId + '-' + #type"      // Composite key
     * key = "#root.methodName + #userId" // Method name + param
     * }</pre>
     */
    String key() default "";

    /**
     * Condition for caching (SpEL expression).
     *
     * <p>Cache only if condition evaluates to true.</p>
     *
     * <p><b>Examples:</b></p>
     * <pre>{@code
     * condition = "#result != null"              // Cache non-null
     * condition = "#result.size() > 0"           // Cache non-empty
     * condition = "#userId.startsWith('PREM')"   // Cache premium users
     * }</pre>
     */
    String condition() default "";

    /**
     * Unless condition (SpEL expression).
     *
     * <p>Skip caching if condition evaluates to true.</p>
     */
    String unless() default "";

    /**
     * Time-to-live in cache.
     */
    long ttl() default -1;

    /**
     * Time unit for TTL.
     */
    TimeUnit ttlUnit() default TimeUnit.MINUTES;

    /**
     * Sync cache access (prevents cache stampede).
     *
     * <p>If true, only one thread computes the value while others wait.</p>
     */
    boolean sync() default false;
}

