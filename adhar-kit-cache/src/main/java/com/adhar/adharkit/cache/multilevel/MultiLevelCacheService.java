package com.adhar.adharkit.cache.multilevel;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.manager.CacheManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * L1/L2 cache composition backing the {@code @MultiLevelCache} annotation.
 *
 * <p>L1 is the Caffeine-backed {@link CacheFacade} (fast, process-local);
 * L2 is a pluggable {@link SecondLevelCache} (shared/distributed).</p>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li><b>Read-through</b>: L1 hit → return; L1 miss → L2 lookup (promoting hits
 *       into L1); L2 miss → load, then populate L1 (and L2 when write-through).</li>
 *   <li><b>Write-through</b>: {@link #put} writes L1 and, when enabled, L2.</li>
 * </ul>
 *
 * <p>Note: the class is named {@code MultiLevelCacheService} (not
 * {@code MultiLevelCache}) to avoid clashing with the annotation of that name.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class MultiLevelCacheService {

    private final CacheManager l1CacheManager;
    private final SecondLevelCache secondLevelCache;

    /**
     * Creates the composition.
     *
     * @param l1CacheManager   manager providing L1 Caffeine caches
     * @param secondLevelCache the L2 backend
     */
    public MultiLevelCacheService(CacheManager l1CacheManager, SecondLevelCache secondLevelCache) {
        this.l1CacheManager = l1CacheManager;
        this.secondLevelCache = secondLevelCache;
    }

    /**
     * Read-through get across both levels.
     *
     * @param l1CacheName  L1 cache name
     * @param l2CacheName  L2 cache name
     * @param key          cache key
     * @param l1Ttl        TTL used when the L1 cache is first created
     * @param l2Ttl        TTL passed to L2 on write-through
     * @param writeThrough whether loaded values are also written to L2
     * @param loader       computes the value on a full miss
     * @return the cached or loaded value (may be null if the loader returns null)
     */
    public Object get(String l1CacheName, String l2CacheName, Object key,
                      Duration l1Ttl, Duration l2Ttl, boolean writeThrough,
                      Supplier<Object> loader) {
        CacheFacade l1 = resolveL1(l1CacheName, l1Ttl);

        Object value = l1.get(key);
        if (value != null) {
            log.trace("L1 hit for '{}' key={}", l1CacheName, key);
            return value;
        }

        value = secondLevelCache.get(l2CacheName, key);
        if (value != null) {
            log.trace("L2 hit for '{}' key={}, promoting to L1", l2CacheName, key);
            l1.put(key, value);
            return value;
        }

        value = loader.get();
        if (value != null) {
            l1.put(key, value);
            if (writeThrough) {
                secondLevelCache.put(l2CacheName, key, value, l2Ttl);
            }
        }
        return value;
    }

    /**
     * Write-through put into L1 and (optionally) L2.
     *
     * @param l1CacheName  L1 cache name
     * @param l2CacheName  L2 cache name
     * @param key          cache key
     * @param value        value to store (ignored when null)
     * @param l1Ttl        TTL used when the L1 cache is first created
     * @param l2Ttl        TTL passed to L2
     * @param writeThrough whether to also write L2
     */
    public void put(String l1CacheName, String l2CacheName, Object key, Object value,
                    Duration l1Ttl, Duration l2Ttl, boolean writeThrough) {
        if (value == null) {
            return;
        }
        resolveL1(l1CacheName, l1Ttl).put(key, value);
        if (writeThrough) {
            secondLevelCache.put(l2CacheName, key, value, l2Ttl);
        }
    }

    /**
     * Evicts a key from both levels.
     *
     * @param l1CacheName L1 cache name
     * @param l2CacheName L2 cache name
     * @param key         cache key
     */
    public void evict(String l1CacheName, String l2CacheName, Object key) {
        CacheFacade l1 = l1CacheManager.getCache(l1CacheName);
        if (l1 != null) {
            l1.evict(key);
        }
        secondLevelCache.evict(l2CacheName, key);
    }

    private CacheFacade resolveL1(String cacheName, Duration ttl) {
        CacheFacade existing = l1CacheManager.getCache(cacheName);
        if (existing != null) {
            return existing;
        }
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            CacheFacade cache = CacheFacade.builder()
                .cacheName(cacheName)
                .expireAfterWrite(ttl)
                .recordStats(true)
                .build();
            l1CacheManager.registerCache(cacheName, cache);
            return cache;
        }
        return l1CacheManager.getOrCreateCache(cacheName);
    }
}
