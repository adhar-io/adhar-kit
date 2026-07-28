package com.adhar.kit.kubernetes.discovery;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.ServiceInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A time-to-live (TTL) cache placed in front of
 * {@link KubernetesClient#discoverServices(String)}.
 *
 * <p>{@link KubernetesClient#discoverServices(String)} performs a live
 * {@code Service} list against the API server on <em>every</em> call. Discovery
 * results rarely change between calls, so this decorator caches the result per
 * label selector for a configurable TTL, collapsing bursts of lookups into a
 * single API call.</p>
 *
 * <p><b>Manual invalidation:</b> callers (e.g. a {@code Service} informer that
 * observes a change) can drop cached entries via {@link #invalidate(String)} or
 * {@link #invalidateAll()} so the next lookup re-lists from the API server. This
 * is the clean integration point for informer-driven refresh; wiring an actual
 * informer is left to callers since the underlying Fabric8 client is owned by
 * {@link KubernetesClient}.</p>
 *
 * <p><b>Uncached path:</b> {@link #discoverServicesUncached(String)} always hits
 * the API server, bypassing the cache entirely.</p>
 *
 * <p>Thread-safe: entries are held in a {@link ConcurrentHashMap}. A benign race
 * may cause two threads to refresh the same selector concurrently; both simply
 * re-list and the last writer wins, which is harmless.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class CachedServiceDiscovery {

    /**
     * A cached discovery result together with the wall-clock instant (millis) at
     * which it expires.
     */
    private record Entry(List<ServiceInfo> services, long expiresAtMillis) {
    }

    private final KubernetesClient delegate;
    private final long ttlMillis;
    private final LongSupplier clockMillis;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Creates a cache with the given TTL, using the system clock.
     *
     * @param delegate the underlying client that performs live lookups
     * @param ttl      how long a result stays fresh; {@code null}, zero or negative
     *                 disables caching (every call delegates live)
     */
    public CachedServiceDiscovery(KubernetesClient delegate, Duration ttl) {
        this(delegate, ttl, System::currentTimeMillis);
    }

    /**
     * Package-visible constructor allowing a deterministic clock in tests.
     *
     * @param delegate    the underlying client that performs live lookups
     * @param ttl         cache TTL; non-positive disables caching
     * @param clockMillis supplier of the current time in milliseconds
     */
    CachedServiceDiscovery(KubernetesClient delegate, Duration ttl, LongSupplier clockMillis) {
        this.delegate = delegate;
        this.ttlMillis = (ttl == null) ? 0L : ttl.toMillis();
        this.clockMillis = clockMillis;
    }

    /**
     * Discovers services by label selector, returning a cached result when a fresh
     * one is available and otherwise performing a live lookup and caching it.
     *
     * @param labelSelector label selector (e.g. {@code "app=order-service"})
     * @return list of service information (never {@code null})
     */
    public List<ServiceInfo> discoverServices(String labelSelector) {
        if (ttlMillis <= 0) {
            return delegate.discoverServices(labelSelector);
        }
        Entry entry = cache.get(labelSelector);
        long now = clockMillis.getAsLong();
        if (entry != null && now < entry.expiresAtMillis()) {
            log.trace("Service discovery cache hit for selector '{}'", labelSelector);
            return entry.services();
        }
        return refresh(labelSelector);
    }

    /**
     * Performs a live lookup and (re)populates the cache for the given selector,
     * regardless of any existing entry's freshness.
     *
     * @param labelSelector label selector
     * @return the freshly listed service information
     */
    public List<ServiceInfo> refresh(String labelSelector) {
        List<ServiceInfo> services = delegate.discoverServices(labelSelector);
        if (ttlMillis > 0) {
            cache.put(labelSelector, new Entry(services, clockMillis.getAsLong() + ttlMillis));
        }
        log.debug("Refreshed service discovery cache for selector '{}' ({} services)",
                labelSelector, services.size());
        return services;
    }

    /**
     * Performs a live lookup, bypassing the cache and leaving it untouched.
     *
     * @param labelSelector label selector
     * @return the freshly listed service information
     */
    public List<ServiceInfo> discoverServicesUncached(String labelSelector) {
        return delegate.discoverServices(labelSelector);
    }

    /**
     * Drops the cached entry for a single selector so the next lookup re-lists.
     *
     * @param labelSelector label selector to invalidate
     */
    public void invalidate(String labelSelector) {
        cache.remove(labelSelector);
    }

    /**
     * Clears the entire cache so every subsequent lookup re-lists.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * @return the number of selectors currently held in the cache
     */
    public int cachedSelectorCount() {
        return cache.size();
    }

    /**
     * @return the underlying client backing this cache (the uncached path)
     */
    public KubernetesClient getDelegate() {
        return delegate;
    }

    /**
     * @return the configured TTL in milliseconds; {@code 0} means caching is disabled
     */
    public long getTtlMillis() {
        return ttlMillis;
    }
}
