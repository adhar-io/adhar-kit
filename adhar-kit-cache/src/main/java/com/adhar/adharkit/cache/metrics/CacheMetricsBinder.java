package com.adhar.adharkit.cache.metrics;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.manager.CacheManager;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds Caffeine {@code CacheStats} of every {@link CacheManager}-managed cache
 * to a Micrometer {@link MeterRegistry}.
 *
 * <p>Registered meters (all tagged with {@code cache=<name>}):</p>
 * <ul>
 *   <li>{@code adhar.cache.gets} tagged {@code result=hit|miss} (function counters)</li>
 *   <li>{@code adhar.cache.evictions} (function counter)</li>
 *   <li>{@code adhar.cache.hit.ratio} (gauge)</li>
 *   <li>{@code adhar.cache.size} (gauge, estimated entry count)</li>
 * </ul>
 *
 * <p>Binding is lazy and idempotent: {@link #bindAll()} may be called repeatedly
 * (e.g. after new caches are created) and each cache is bound exactly once.
 * Caches created after the initial {@link #bindTo(MeterRegistry)} can be picked
 * up by calling {@link #bindAll()} or {@link #bindCache(String)}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class CacheMetricsBinder implements MeterBinder {

    private final CacheManager cacheManager;
    private final Set<String> boundCaches = ConcurrentHashMap.newKeySet();
    private volatile MeterRegistry registry;

    /**
     * Creates the binder.
     *
     * @param cacheManager the cache manager whose caches are instrumented
     */
    public CacheMetricsBinder(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        bindAll();
    }

    /**
     * Binds all currently known caches that are not yet bound. Idempotent.
     */
    public void bindAll() {
        if (registry == null) {
            log.debug("No MeterRegistry bound yet; skipping cache metrics binding");
            return;
        }
        cacheManager.getCacheNames().forEach(this::bindCache);
    }

    /**
     * Binds a single cache by name. No-op if already bound, unknown, or no
     * registry is available yet.
     *
     * @param cacheName the cache to bind
     */
    public void bindCache(String cacheName) {
        MeterRegistry meterRegistry = this.registry;
        if (meterRegistry == null || !boundCaches.add(cacheName)) {
            return;
        }
        CacheFacade cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            boundCaches.remove(cacheName);
            return;
        }

        Tags tags = Tags.of("cache", cacheName);

        FunctionCounter.builder("adhar.cache.gets", cache, c -> c.stats().hitCount())
            .tags(tags.and("result", "hit"))
            .description("Number of cache lookups that returned a cached value")
            .register(meterRegistry);

        FunctionCounter.builder("adhar.cache.gets", cache, c -> c.stats().missCount())
            .tags(tags.and("result", "miss"))
            .description("Number of cache lookups that missed")
            .register(meterRegistry);

        FunctionCounter.builder("adhar.cache.evictions", cache, c -> c.stats().evictionCount())
            .tags(tags)
            .description("Number of cache evictions")
            .register(meterRegistry);

        Gauge.builder("adhar.cache.hit.ratio", cache, c -> c.stats().hitRate())
            .tags(tags)
            .description("Ratio of cache lookups that were hits")
            .register(meterRegistry);

        Gauge.builder("adhar.cache.size", cache, CacheFacade::size)
            .tags(tags)
            .description("Estimated number of entries in the cache")
            .register(meterRegistry);

        log.debug("Bound Micrometer meters for cache '{}'", cacheName);
    }

    /**
     * Number of caches currently bound (visible for tests/monitoring).
     *
     * @return bound cache count
     */
    public int getBoundCacheCount() {
        return boundCaches.size();
    }
}
