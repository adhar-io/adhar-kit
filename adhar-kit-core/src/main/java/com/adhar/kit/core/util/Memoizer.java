package com.adhar.kit.core.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe memoization utility for caching expensive computations.
 *
 * <p>Caches function results to avoid redundant calculations.</p>
 *
 * <p><b>Example - Basic Memoization:</b></p>
 * <pre>{@code
 * Memoizer<String, User> userCache = new Memoizer<>();
 *
 * User user = userCache.get("userId123", userId -> {
 *     // Expensive database query - executed only once
 *     return userRepository.findById(userId);
 * });
 *
 * // Subsequent calls return cached value
 * User cached = userCache.get("userId123", ...);  // From cache
 * }</pre>
 *
 * <p><b>Example - Method Memoization:</b></p>
 * <pre>{@code
 * public class PriceService {
 *     private final Memoizer<String, BigDecimal> priceCache = new Memoizer<>();
 *
 *     public BigDecimal getPrice(String productId) {
 *         return priceCache.get(productId, id -> {
 *             // Expensive price calculation
 *             return calculatePrice(id);
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - With Expiration:</b></p>
 * <pre>{@code
 * Memoizer<String, Stock> stockCache = new Memoizer<>(300000); // 5 min TTL
 *
 * Stock stock = stockCache.get("AAPL", symbol -> {
 *     return stockService.getQuote(symbol);
 * });
 * }</pre>
 *
 * @param <K> key type
 * @param <V> value type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class Memoizer<K, V> {

    private final Map<K, CachedValue<V>> cache = new ConcurrentHashMap<>();
    private final Map<K, Lock> locks = new ConcurrentHashMap<>();
    private final long ttlMillis;

    /**
     * Constructor with no expiration.
     */
    public Memoizer() {
        this(-1);
    }

    /**
     * Constructor with TTL.
     *
     * @param ttlMillis time-to-live in milliseconds
     */
    public Memoizer(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Gets value, computing if necessary.
     *
     * @param key cache key
     * @param supplier value supplier
     * @return cached or computed value
     */
    public V get(K key, java.util.function.Function<K, V> supplier) {
        CachedValue<V> cached = cache.get(key);

        // Check if cached and not expired
        if (cached != null && !cached.isExpired(ttlMillis)) {
            log.debug("Cache hit for key: {}", key);
            return cached.value;
        }

        // Compute new value
        Lock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock
            cached = cache.get(key);
            if (cached != null && !cached.isExpired(ttlMillis)) {
                return cached.value;
            }

            log.debug("Cache miss for key: {}, computing value", key);
            V value = supplier.apply(key);
            cache.put(key, new CachedValue<>(value));
            return value;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears cache.
     */
    public void clear() {
        cache.clear();
        locks.clear();
        log.debug("Cache cleared");
    }

    /**
     * Invalidates a key.
     *
     * @param key the key to invalidate
     */
    public void invalidate(K key) {
        cache.remove(key);
        locks.remove(key);
        log.debug("Invalidated key: {}", key);
    }

    /**
     * Gets cache size.
     *
     * @return number of cached entries
     */
    public int size() {
        return cache.size();
    }

    /**
     * Checks if key is cached.
     *
     * @param key the key
     * @return true if cached
     */
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    /**
     * Cached value with timestamp.
     */
    private static class CachedValue<V> {
        private final V value;
        private final long timestamp;

        CachedValue(V value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            if (ttlMillis < 0) {
                return false; // Never expires
            }
            return (System.currentTimeMillis() - timestamp) > ttlMillis;
        }
    }
}

