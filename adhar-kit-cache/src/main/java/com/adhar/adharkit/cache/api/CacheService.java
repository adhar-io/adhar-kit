package com.adhar.adharkit.cache.api;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Framework-agnostic Cache Service API.
 *
 * <p>This interface provides a unified caching abstraction that works across
 * Spring Boot, Quarkus, and Micronaut frameworks. It supports common caching
 * operations like get, put, evict, and conditional operations.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * CacheService cache = CacheFacade.getInstance();
 *
 * // Store value
 * cache.put("users", userId, user);
 *
 * // Retrieve value
 * Optional<User> user = cache.get("users", userId, User.class);
 *
 * // Get or compute
 * User user = cache.getOrCompute("users", userId, User.class,
 *     () -> userRepository.findById(userId));
 *
 * // Evict entry
 * cache.evict("users", userId);
 *
 * // Clear entire cache
 * cache.clear("users");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.adharkit.cache.CacheFacade
 */
public interface CacheService {

    /**
     * Retrieves a value from the cache.
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param type the expected type of the cached value
     * @param <T> the type of the cached value
     * @return an Optional containing the cached value, or empty if not found
     */
    <T> Optional<T> get(String cacheName, Object key, Class<T> type);

    /**
     * Stores a value in the cache.
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param value the value to cache
     */
    void put(String cacheName, Object key, Object value);

    /**
     * Stores a value in the cache with a time-to-live.
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param value the value to cache
     * @param ttl the time-to-live duration
     */
    void put(String cacheName, Object key, Object value, Duration ttl);

    /**
     * Stores a value only if the key doesn't exist.
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param value the value to cache
     * @return true if the value was stored, false if key already exists
     */
    boolean putIfAbsent(String cacheName, Object key, Object value);

    /**
     * Retrieves a cached value or computes it if not present.
     *
     * <p>This method is useful for implementing cache-aside pattern.
     * If the value is not in cache, it executes the valueLoader,
     * stores the result in cache, and returns it.</p>
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param type the expected type of the cached value
     * @param valueLoader the function to compute the value if not cached
     * @param <T> the type of the cached value
     * @return the cached or computed value
     */
    <T> T getOrCompute(String cacheName, Object key, Class<T> type, Callable<T> valueLoader);

    /**
     * Removes a specific entry from the cache.
     *
     * @param cacheName the name of the cache
     * @param key the cache key to evict
     */
    void evict(String cacheName, Object key);

    /**
     * Removes all entries from a specific cache.
     *
     * @param cacheName the name of the cache to clear
     */
    void clear(String cacheName);

    /**
     * Checks if a key exists in the cache.
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @return true if the key exists, false otherwise
     */
    boolean contains(String cacheName, Object key);

    /**
     * Gets the size of a cache (if supported).
     *
     * @param cacheName the name of the cache
     * @return the number of entries in the cache, or -1 if not supported
     */
    long size(String cacheName);
}

