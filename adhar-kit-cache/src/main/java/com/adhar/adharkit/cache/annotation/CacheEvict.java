package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Annotation for cache eviction.
 *
 * <p>Automatically removes entries from cache when method is called.</p>
 *
 * <p><b>Example - Evict Single Entry:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @CacheEvict(cacheName = "users", key = "#userId")
 *     public void updateUser(String userId, User user) {
 *         userRepository.save(user);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Evict All Entries:</b></p>
 * <pre>{@code
 * @CacheEvict(cacheName = "users", allEntries = true)
 * public void clearAllUsers() {
 *     userRepository.deleteAll();
 * }
 * }</pre>
 *
 * <p><b>Example - Evict Before Method:</b></p>
 * <pre>{@code
 * @CacheEvict(
 *     cacheName = "products",
 *     key = "#productId",
 *     beforeInvocation = true
 * )
 * public void deleteProduct(String productId) {
 *     productRepository.delete(productId);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvict {

    /**
     * Name of the cache to evict from.
     */
    String cacheName() default "";

    /**
     * Cache key expression.
     *
     * <p>Ignored if allEntries is true.</p>
     */
    String key() default "";

    /**
     * Evict all entries in the cache.
     */
    boolean allEntries() default false;

    /**
     * Evict before method invocation.
     *
     * <p>If true, evicts before method runs (useful for delete operations).
     * If false, evicts after method completes successfully.</p>
     */
    boolean beforeInvocation() default false;

    /**
     * Condition for eviction (SpEL expression).
     */
    String condition() default "";
}

