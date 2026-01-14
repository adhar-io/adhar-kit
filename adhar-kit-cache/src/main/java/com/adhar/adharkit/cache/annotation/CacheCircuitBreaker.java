package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Cache with circuit breaker for fallback handling.
 *
 * <p><b>Example - Resilient Caching:</b></p>
 * <pre>{@code
 * @Service
 * public class ExternalDataService {
 *
 *     @CacheCircuitBreaker(
 *         cacheName = "external-data",
 *         fallbackMethod = "getFallbackData",
 *         failureThreshold = 5,
 *         timeout = 3000
 *     )
 *     @Cacheable(cacheName = "external-data", key = "#dataId")
 *     public Data fetchExternalData(String dataId) {
 *         return externalApi.getData(dataId);
 *     }
 *
 *     public Data getFallbackData(String dataId) {
 *         return Data.createDefault(dataId);
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
public @interface CacheCircuitBreaker {

    /**
     * Cache name.
     */
    String cacheName();

    /**
     * Fallback method name.
     */
    String fallbackMethod();

    /**
     * Failure threshold before opening circuit.
     */
    int failureThreshold() default 5;

    /**
     * Timeout for cache operations (milliseconds).
     */
    long timeout() default 5000;

    /**
     * Wait duration in open state (milliseconds).
     */
    long waitDurationInOpenState() default 60000;
}

