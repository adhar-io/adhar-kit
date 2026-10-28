package com.adhar.adharkit.cache.dapr;

import com.adhar.kit.dapr.DaprFacade;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Spring {@link CacheManager} of {@code adhar.cache.provider=dapr}: every cache is a
 * {@link DaprCache} in the same Dapr state store, created on first use.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprCacheManager implements CacheManager {

    private final DaprFacade dapr;
    private final String store;
    private final String keyPrefix;
    private final Duration defaultTtl;
    private final Map<String, Duration> ttlByCache;
    private final Duration generationTtl;
    private final boolean allowNullValues;
    private final DaprCacheValueCodec codec = new DaprCacheValueCodec();
    private final Map<String, DaprCache> caches = new ConcurrentHashMap<>();

    public DaprCacheManager(DaprFacade dapr, String store) {
        this(dapr, store, "cache:", Duration.ofMinutes(30), Map.of(), Duration.ofSeconds(2), true);
    }

    /**
     * @param dapr            the facade
     * @param store           the state store component
     * @param keyPrefix       prefix of every key
     * @param defaultTtl      entry time-to-live ({@code null} for none)
     * @param ttlByCache      per-cache time-to-live overrides
     * @param generationTtl   how long a replica trusts its view of a cache's generation
     * @param allowNullValues whether {@code null} results are cached
     */
    public DaprCacheManager(DaprFacade dapr, String store, String keyPrefix, Duration defaultTtl,
                            Map<String, Duration> ttlByCache, Duration generationTtl, boolean allowNullValues) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.keyPrefix = keyPrefix;
        this.defaultTtl = defaultTtl;
        this.ttlByCache = ttlByCache == null ? Map.of() : Map.copyOf(ttlByCache);
        this.generationTtl = generationTtl;
        this.allowNullValues = allowNullValues;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, n -> new DaprCache(n, dapr, store, keyPrefix,
                ttlByCache.getOrDefault(n, defaultTtl), generationTtl, allowNullValues, codec));
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }

    /** The state store every cache lives in. */
    public String getStore() {
        return store;
    }
}
