package com.adhar.adharkit.cache.manager;

import com.adhar.adharkit.cache.CacheFacade;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache Manager for managing multiple cache instances.
 *
 * <p>Provides centralized management of all application caches including:</p>
 * <ul>
 *   <li><b>Cache Creation</b> - Create and configure caches</li>
 *   <li><b>Cache Retrieval</b> - Get existing caches by name</li>
 *   <li><b>Bulk Operations</b> - Clear all, get stats for all</li>
 *   <li><b>Monitoring</b> - Aggregate statistics across caches</li>
 *   <li><b>Health Checks</b> - Cache health and performance metrics</li>
 * </ul>
 *
 * <p><b>Example - Create and Manage Caches:</b></p>
 * <pre>{@code
 * CacheManager manager = CacheManager.getInstance();
 *
 * // Create caches
 * CacheFacade usersCache = manager.createCache("users")
 *     .maximumSize(1000)
 *     .expireAfterWrite(Duration.ofMinutes(10))
 *     .recordStats(true)
 *     .build();
 *
 * CacheFacade productsCache = manager.createCache("products")
 *     .maximumSize(5000)
 *     .expireAfterWrite(Duration.ofHours(1))
 *     .recordStats(true)
 *     .build();
 *
 * // Get cache by name
 * CacheFacade cache = manager.getCache("users");
 *
 * // Get all cache names
 * Set<String> cacheNames = manager.getCacheNames();
 *
 * // Clear all caches
 * manager.clearAll();
 * }</pre>
 *
 * <p><b>Example - Monitoring:</b></p>
 * <pre>{@code
 * Map<String, CacheStats> stats = manager.getAllStats();
 * stats.forEach((name, stat) -> {
 *     log.info("Cache: {}, Hit Rate: {}%, Evictions: {}",
 *         name, stat.hitRate() * 100, stat.evictionCount());
 * });
 *
 * Map<String, Object> health = manager.getHealthMetrics();
 * log.info("Total caches: {}", health.get("totalCaches"));
 * log.info("Total entries: {}", health.get("totalEntries"));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class CacheManager {

    /**
     * Singleton instance.
     */
    private static final CacheManager INSTANCE = new CacheManager();

    /**
     * Registry of all managed caches.
     */
    private final Map<String, CacheFacade> caches = new ConcurrentHashMap<>();

    /**
     * Default cache configuration.
     */
    private long defaultMaxSize = 1000;
    private Duration defaultExpireAfterWrite = Duration.ofMinutes(10);
    private boolean defaultRecordStats = true;

    /**
     * Private constructor for singleton.
     */
    private CacheManager() {
        log.info("Initializing Cache Manager");
    }

    /**
     * Gets the singleton instance.
     *
     * @return cache manager instance
     */
    public static CacheManager getInstance() {
        return INSTANCE;
    }

    // ==================== CACHE CREATION ====================

    /**
     * Creates a new cache with default configuration.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * CacheFacade cache = manager.createCache("myCache")
     *     .maximumSize(2000)
     *     .expireAfterWrite(Duration.ofMinutes(15))
     *     .build();
     * }</pre>
     *
     * @param cacheName unique cache name
     * @return cache builder for configuration
     */
    public CacheFacade.Builder createCache(String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name must not be null");

        if (caches.containsKey(cacheName)) {
            log.warn("Cache '{}' already exists, returning existing instance", cacheName);
            return null;
        }

        log.debug("Creating new cache: {}", cacheName);
        return CacheFacade.builder()
            .cacheName(cacheName)
            .maximumSize(defaultMaxSize)
            .expireAfterWrite(defaultExpireAfterWrite)
            .recordStats(defaultRecordStats);
    }

    /**
     * Creates or gets an existing cache.
     *
     * @param cacheName cache name
     * @return cache instance
     */
    public CacheFacade getOrCreateCache(String cacheName) {
        return caches.computeIfAbsent(cacheName, name ->
            CacheFacade.builder()
                .cacheName(name)
                .maximumSize(defaultMaxSize)
                .expireAfterWrite(defaultExpireAfterWrite)
                .recordStats(defaultRecordStats)
                .build()
        );
    }

    /**
     * Registers an existing cache.
     *
     * @param cacheName cache name
     * @param cache cache instance
     */
    public void registerCache(String cacheName, CacheFacade cache) {
        Objects.requireNonNull(cacheName, "Cache name must not be null");
        Objects.requireNonNull(cache, "Cache must not be null");

        caches.put(cacheName, cache);
        log.info("Registered cache: {}", cacheName);
    }

    // ==================== CACHE RETRIEVAL ====================

    /**
     * Gets a cache by name.
     *
     * @param cacheName cache name
     * @return cache instance or null if not found
     */
    public CacheFacade getCache(String cacheName) {
        return caches.get(cacheName);
    }

    /**
     * Gets all cache names.
     *
     * @return set of cache names
     */
    public Set<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }

    /**
     * Gets all caches.
     *
     * @return map of all caches
     */
    public Map<String, CacheFacade> getAllCaches() {
        return Collections.unmodifiableMap(caches);
    }

    /**
     * Checks if a cache exists.
     *
     * @param cacheName cache name
     * @return true if cache exists
     */
    public boolean hasCache(String cacheName) {
        return caches.containsKey(cacheName);
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Clears all caches.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * manager.clearAll();
     * log.info("All caches cleared");
     * }</pre>
     */
    public void clearAll() {
        log.info("Clearing all {} caches", caches.size());
        caches.values().forEach(CacheFacade::clear);
    }

    /**
     * Removes a cache.
     *
     * @param cacheName cache name to remove
     * @return true if cache was removed
     */
    public boolean removeCache(String cacheName) {
        CacheFacade removed = caches.remove(cacheName);
        if (removed != null) {
            removed.clear();
            log.info("Removed cache: {}", cacheName);
            return true;
        }
        return false;
    }

    /**
     * Performs cleanup on all caches.
     */
    public void cleanUpAll() {
        log.debug("Performing cleanup on all caches");
        caches.values().forEach(CacheFacade::cleanUp);
    }

    // ==================== STATISTICS & MONITORING ====================

    /**
     * Gets statistics for all caches.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Map<String, CacheStats> stats = manager.getAllStats();
     * stats.forEach((name, stat) -> {
     *     log.info("{}: hit rate = {}%, misses = {}",
     *         name,
     *         String.format("%.2f", stat.hitRate() * 100),
     *         stat.missCount());
     * });
     * }</pre>
     *
     * @return map of cache name to statistics
     */
    public Map<String, CacheStats> getAllStats() {
        Map<String, CacheStats> stats = new HashMap<>();
        caches.forEach((name, cache) -> stats.put(name, cache.stats()));
        return stats;
    }

    /**
     * Gets health metrics for all caches.
     *
     * <p>Includes:</p>
     * <ul>
     *   <li>totalCaches - Number of caches</li>
     *   <li>totalEntries - Total cached entries</li>
     *   <li>totalHitRate - Average hit rate</li>
     *   <li>totalEvictions - Total evictions</li>
     * </ul>
     *
     * @return health metrics map
     */
    public Map<String, Object> getHealthMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        int totalCaches = caches.size();
        long totalEntries = caches.values().stream()
            .mapToLong(CacheFacade::size)
            .sum();

        double totalHitRate = caches.values().stream()
            .map(CacheFacade::stats)
            .mapToDouble(CacheStats::hitRate)
            .average()
            .orElse(0.0);

        long totalEvictions = caches.values().stream()
            .map(CacheFacade::stats)
            .mapToLong(CacheStats::evictionCount)
            .sum();

        metrics.put("totalCaches", totalCaches);
        metrics.put("totalEntries", totalEntries);
        metrics.put("averageHitRate", totalHitRate);
        metrics.put("totalEvictions", totalEvictions);
        metrics.put("cacheNames", getCacheNames());

        return metrics;
    }

    /**
     * Gets detailed stats for a specific cache.
     *
     * @param cacheName cache name
     * @return detailed statistics map
     */
    public Map<String, Object> getCacheStats(String cacheName) {
        CacheFacade cache = getCache(cacheName);
        if (cache == null) {
            return Collections.emptyMap();
        }

        CacheStats stats = cache.stats();
        Map<String, Object> result = new HashMap<>();

        result.put("cacheName", cacheName);
        result.put("size", cache.size());
        result.put("hitCount", stats.hitCount());
        result.put("missCount", stats.missCount());
        result.put("hitRate", stats.hitRate());
        result.put("missRate", stats.missRate());
        result.put("loadCount", stats.loadCount());
        result.put("loadSuccessCount", stats.loadSuccessCount());
        result.put("loadFailureCount", stats.loadFailureCount());
        result.put("evictionCount", stats.evictionCount());
        result.put("averageLoadPenalty", stats.averageLoadPenalty());

        return result;
    }

    // ==================== CONFIGURATION ====================

    /**
     * Sets default maximum size for new caches.
     *
     * @param maxSize maximum size
     */
    public void setDefaultMaxSize(long maxSize) {
        this.defaultMaxSize = maxSize;
        log.info("Set default max size: {}", maxSize);
    }

    /**
     * Sets default expiration for new caches.
     *
     * @param duration expiration duration
     */
    public void setDefaultExpireAfterWrite(Duration duration) {
        this.defaultExpireAfterWrite = duration;
        log.info("Set default expire after write: {}", duration);
    }

    /**
     * Sets whether to record stats by default.
     *
     * @param recordStats true to record stats
     */
    public void setDefaultRecordStats(boolean recordStats) {
        this.defaultRecordStats = recordStats;
        log.info("Set default record stats: {}", recordStats);
    }

    /**
     * Gets cache count.
     *
     * @return number of managed caches
     */
    public int getCacheCount() {
        return caches.size();
    }

    /**
     * Gets total entry count across all caches.
     *
     * @return total entries
     */
    public long getTotalEntries() {
        return caches.values().stream()
            .mapToLong(CacheFacade::size)
            .sum();
    }
}

