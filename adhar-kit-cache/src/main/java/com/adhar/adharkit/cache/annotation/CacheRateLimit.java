package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Rate limit cache access.
 *
 * <p>Prevents cache stampede by limiting concurrent cache loads.</p>
 *
 * <p><b>Example - Rate Limited Cache:</b></p>
 * <pre>{@code
 * @Service
 * public class ApiService {
 *
 *     @CacheRateLimit(
 *         maxConcurrent = 5,
 *         maxPerSecond = 100
 *     )
 *     @Cacheable(cacheName = "api-data", key = "#endpoint")
 *     public ApiResponse callExternalApi(String endpoint) {
 *         return externalApiClient.call(endpoint);
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
public @interface CacheRateLimit {

    /**
     * Maximum concurrent cache loads.
     */
    int maxConcurrent() default 10;

    /**
     * Maximum loads per second.
     */
    int maxPerSecond() default 100;

    /**
     * Timeout for acquiring permit (milliseconds).
     */
    long timeout() default 5000;
}

