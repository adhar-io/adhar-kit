package com.adhar.adharkit.cache.multilevel;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;

/**
 * {@link SecondLevelCache} adapter over a Spring {@link CacheManager}, allowing
 * any Spring-managed cache backend — notably the module's
 * {@link com.adhar.adharkit.cache.service.RedisCacheManager} — to serve as L2.
 *
 * <p>The per-entry {@code ttl} argument is ignored: Spring's {@link Cache} API
 * has no per-put TTL, so entries expire per the backend's cache-level TTL
 * configuration (e.g. {@code adhar.cache.time-to-live} for Redis).</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class SpringCacheSecondLevelCache implements SecondLevelCache {

    private final CacheManager cacheManager;

    /**
     * Creates the adapter.
     *
     * @param cacheManager the Spring cache manager backing L2
     */
    public SpringCacheSecondLevelCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Object get(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper != null ? wrapper.get() : null;
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
