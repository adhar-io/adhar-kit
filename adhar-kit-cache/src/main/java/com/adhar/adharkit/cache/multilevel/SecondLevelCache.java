package com.adhar.adharkit.cache.multilevel;

import java.time.Duration;

/**
 * Pluggable L2 (shared/distributed) cache abstraction used by
 * {@link MultiLevelCacheService} for read-through/write-through composition
 * with the Caffeine-backed L1.
 *
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@link InMemorySecondLevelCache} - process-local map, useful for tests
 *       and single-node deployments</li>
 *   <li>{@link SpringCacheSecondLevelCache} - adapts any Spring
 *       {@code org.springframework.cache.CacheManager}, e.g. the module's
 *       {@code RedisCacheManager}</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface SecondLevelCache {

    /**
     * Retrieves a value from the L2 cache.
     *
     * @param cacheName logical cache name
     * @param key       cache key
     * @return the cached value, or null if absent/expired
     */
    Object get(String cacheName, Object key);

    /**
     * Stores a value in the L2 cache.
     *
     * @param cacheName logical cache name
     * @param key       cache key
     * @param value     value to store
     * @param ttl       time-to-live; may be null or non-positive for "no explicit TTL"
     */
    void put(String cacheName, Object key, Object value, Duration ttl);

    /**
     * Removes a single entry.
     *
     * @param cacheName logical cache name
     * @param key       cache key
     */
    void evict(String cacheName, Object key);

    /**
     * Removes all entries of the named cache.
     *
     * @param cacheName logical cache name
     */
    void clear(String cacheName);
}
