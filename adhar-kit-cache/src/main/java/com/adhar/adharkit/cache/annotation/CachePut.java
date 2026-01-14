package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for cache update/put.
 *
 * <p>Always executes the method and updates the cache with the result.</p>
 *
 * <p><b>Example - Update Cache:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @CachePut(cacheName = "users", key = "#user.id")
 *     public User saveUser(User user) {
 *         return userRepository.save(user);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - With TTL:</b></p>
 * <pre>{@code
 * @CachePut(
 *     cacheName = "sessions",
 *     key = "#session.id",
 *     ttl = 30,
 *     ttlUnit = TimeUnit.MINUTES
 * )
 * public Session createSession(Session session) {
 *     return sessionRepository.save(session);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CachePut {

    /**
     * Name of the cache to update.
     */
    String cacheName() default "";

    /**
     * Cache key expression.
     */
    String key() default "";

    /**
     * Condition for caching (SpEL expression).
     */
    String condition() default "";

    /**
     * Unless condition (SpEL expression).
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
}

