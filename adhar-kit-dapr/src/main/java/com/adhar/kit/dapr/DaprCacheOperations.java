package com.adhar.kit.dapr;

import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A TTL cache on a Dapr state store: entries are saved with {@code ttlInSeconds} metadata under
 * the {@value #KEY_PREFIX} key namespace, so they expire in the store itself and never collide
 * with the service's own state.
 *
 * <p>{@code getOrSet} treats the store as an optimisation: a failed read falls through to the
 * loader and a failed write is logged, so an unavailable cache slows a request down rather than
 * failing it. {@code get}, {@code set} and {@code delete} report failures.</p>
 *
 * <pre>{@code
 * Rates rates = dapr.cache().getOrSet("fx-rates", Duration.ofMinutes(5), Rates.class, fx::fetch);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public final class DaprCacheOperations {

    /** Prefix of every cache key in the state store. */
    public static final String KEY_PREFIX = "cache:";

    private final DaprFacade dapr;
    private final String store;

    DaprCacheOperations(DaprFacade dapr, String store) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /** The state store backing the cache. */
    public String store() {
        return store;
    }

    /** The same cache on another state store. */
    public DaprCacheOperations withStore(String storeName) {
        return new DaprCacheOperations(dapr, storeName);
    }

    /** The cached value, or {@code null} on a miss. */
    public <T> T get(String key, Class<T> type) {
        return dapr.getState(store, KEY_PREFIX + key, type);
    }

    public <T> T get(String key, TypeRef<T> type) {
        return dapr.getState(store, KEY_PREFIX + key, type);
    }

    /** Caches a value for {@code ttlSeconds} (at least one second). */
    public void set(String key, Object value, long ttlSeconds) {
        set(key, value, Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    public void set(String key, Object value, Duration ttl) {
        dapr.saveStateWithTTL(store, KEY_PREFIX + key, value, ttl);
    }

    public void delete(String key) {
        dapr.deleteState(store, KEY_PREFIX + key);
    }

    /** The cached value, or the loader's result cached for {@code ttlSeconds}. */
    public <T> T getOrSet(String key, long ttlSeconds, Class<T> type, Supplier<T> loader) {
        return getOrSet(key, Duration.ofSeconds(Math.max(1, ttlSeconds)), TypeRef.get(type), loader);
    }

    public <T> T getOrSet(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        return getOrSet(key, ttl, TypeRef.get(type), loader);
    }

    public <T> T getOrSet(String key, Duration ttl, TypeRef<T> type, Supplier<T> loader) {
        try {
            T cached = dapr.getState(store, KEY_PREFIX + key, type);
            if (cached != null) {
                return cached;
            }
        } catch (DaprError e) {
            log.warn("Cache read of '{}' from store '{}' failed, loading instead: {}", key, store, e.getMessage());
        }
        T loaded = loader.get();
        if (loaded != null) {
            try {
                set(key, loaded, ttl);
            } catch (DaprError e) {
                log.warn("Cache write of '{}' to store '{}' failed: {}", key, store, e.getMessage());
            }
        }
        return loaded;
    }
}
