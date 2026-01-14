package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Enables caching for all methods in a class.
 *
 * <p>Automatically caches all public method return values in the class.</p>
 *
 * <p><b>Example - Service-Level Caching:</b></p>
 * <pre>{@code
 * @Service
 * @EnableCaching(
 *     cacheName = "user-service",
 *     keyPrefix = "user:",
 *     ttl = 10,
 *     ttlUnit = TimeUnit.MINUTES
 * )
 * public class UserService {
 *
 *     // Automatically cached as "user:findById:userId"
 *     public User findById(String userId) {
 *         return userRepository.findById(userId);
 *     }
 *
 *     // Automatically cached as "user:findByEmail:email"
 *     public User findByEmail(String email) {
 *         return userRepository.findByEmail(email);
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
public @interface EnableCaching {

    /**
     * Cache name for all methods.
     */
    String cacheName() default "";

    /**
     * Key prefix for all cache keys.
     */
    String keyPrefix() default "";

    /**
     * Maximum cache size.
     */
    long maxSize() default 1000;

    /**
     * TTL for all cached entries.
     */
    long ttl() default 10;

    /**
     * Time unit for TTL.
     */
    java.util.concurrent.TimeUnit ttlUnit() default java.util.concurrent.TimeUnit.MINUTES;

    /**
     * Enable statistics.
     */
    boolean recordStats() default true;

    /**
     * Methods to exclude from caching.
     */
    String[] excludeMethods() default {};
}

