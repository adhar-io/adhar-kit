package com.adhar.kit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for cache metrics monitoring.
 * <p>
 * This annotation automatically tracks cache-related metrics including
 * hit rates, miss rates, evictions, and cache sizes.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @CacheMetrics(
 *     cacheName = "userCache",
 *     recordHitRate = true,
 *     recordSize = true,
 *     recordEvictions = true
 * )
 * public User getUserFromCache(String userId) {
 *     // cache lookup implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheMetrics {

    /**
     * The name of the cache being monitored.
     *
     * @return the cache name
     */
    String cacheName() default "";

    /**
     * The base name for cache metrics.
     *
     * @return the base metric name
     */
    String name() default "";

    /**
     * Additional tags for the cache metrics.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to record cache hit/miss rates.
     *
     * @return true to record hit rates
     */
    boolean recordHitRate() default true;

    /**
     * Whether to record cache size metrics.
     *
     * @return true to record cache size
     */
    boolean recordSize() default true;

    /**
     * Whether to record cache eviction metrics.
     *
     * @return true to record evictions
     */
    boolean recordEvictions() default true;

    /**
     * Whether to record cache load times.
     *
     * @return true to record load times
     */
    boolean recordLoadTime() default false;

    /**
     * The method return value that indicates a cache hit.
     * If the return value equals this, it's considered a hit.
     *
     * @return the value indicating a cache hit
     */
    String hitIndicator() default "";

    /**
     * Whether null return values should be considered cache misses.
     *
     * @return true if null means cache miss
     */
    boolean nullIsMiss() default true;
}
