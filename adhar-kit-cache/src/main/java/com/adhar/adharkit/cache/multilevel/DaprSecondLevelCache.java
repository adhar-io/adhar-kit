package com.adhar.adharkit.cache.multilevel;

import com.adhar.adharkit.cache.dapr.DaprCache;
import com.adhar.adharkit.cache.dapr.DaprCacheManager;
import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NullValue;

import java.time.Duration;
import java.util.Objects;

/**
 * {@link SecondLevelCache} backed by a Dapr state store via {@link DaprFacade}, making the L1
 * Caffeine / L2 composition of {@link MultiLevelCacheService} distributed through the Dapr
 * sidecar (Redis, Cosmos DB, DynamoDB, ... &mdash; whatever the configured Dapr state component
 * is).
 *
 * <p>Built on {@link DaprCacheManager}: values keep their type (an L2 hit returns the class that
 * was cached, not a {@code Map}), entries expire in the store, and {@link #clear(String)} works
 * by starting a new generation of the cache's keys.</p>
 *
 * <p>Reads degrade gracefully: a sidecar/store failure on {@code get} is logged and treated as a
 * cache miss so the multi-level read-through can still fall back to the loader. Writes and
 * evictions propagate failures, since silently losing them would risk stale data.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprSecondLevelCache implements SecondLevelCache {

    private final DaprCacheManager caches;
    private final String stateStoreName;

    /**
     * Creates the Dapr-backed L2.
     *
     * @param daprFacade     the Dapr facade used for state operations
     * @param stateStoreName the Dapr state store component name (e.g. "statestore")
     */
    public DaprSecondLevelCache(DaprFacade daprFacade, String stateStoreName) {
        this(new DaprCacheManager(Objects.requireNonNull(daprFacade, "daprFacade must not be null"),
                Objects.requireNonNull(stateStoreName, "stateStoreName must not be null")));
    }

    /**
     * Creates the L2 on an existing cache manager (sharing its codec and settings).
     *
     * @param caches the Dapr cache manager
     */
    public DaprSecondLevelCache(DaprCacheManager caches) {
        this.caches = Objects.requireNonNull(caches, "caches must not be null");
        this.stateStoreName = caches.getStore();
    }

    @Override
    public Object get(String cacheName, Object key) {
        try {
            Cache.ValueWrapper wrapper = cache(cacheName).get(key);
            Object value = wrapper == null ? null : wrapper.get();
            return value == NullValue.INSTANCE ? null : value;
        } catch (RuntimeException e) {
            log.warn("Dapr L2 get failed for store='{}' cache='{}' key='{}', treating as miss: {}",
                stateStoreName, cacheName, key, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        cache(cacheName).put(key, value, ttl);
    }

    @Override
    public void evict(String cacheName, Object key) {
        cache(cacheName).evict(key);
    }

    /**
     * Empties the cache by moving it to a new generation of keys (a state store cannot list
     * keys); the old entries expire on their own.
     *
     * @param cacheName logical cache name
     */
    @Override
    public void clear(String cacheName) {
        cache(cacheName).clear();
    }

    /**
     * The Dapr state store component name this L2 writes to.
     *
     * @return the state store name
     */
    public String getStateStoreName() {
        return stateStoreName;
    }

    private DaprCache cache(String cacheName) {
        return (DaprCache) caches.getCache(cacheName);
    }
}
