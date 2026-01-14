package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Monitor cache metrics and trigger alerts.
 *
 * <p><b>Example - Alert on Low Hit Rate:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @CacheMonitor(
 *         alertOnHitRateBelow = 0.7,
 *         alertOnEvictionRateAbove = 0.3,
 *         metricsPrefix = "user.cache"
 *     )
 *     @Cacheable(cacheName = "users", key = "#userId")
 *     public User findUser(String userId) {
 *         return userRepository.findById(userId);
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
public @interface CacheMonitor {

    /**
     * Alert if hit rate drops below this threshold.
     */
    double alertOnHitRateBelow() default 0.5;

    /**
     * Alert if eviction rate exceeds this threshold.
     */
    double alertOnEvictionRateAbove() default 0.5;

    /**
     * Metrics prefix for monitoring system.
     */
    String metricsPrefix() default "cache";

    /**
     * Enable detailed logging.
     */
    boolean detailedLogging() default false;
}

