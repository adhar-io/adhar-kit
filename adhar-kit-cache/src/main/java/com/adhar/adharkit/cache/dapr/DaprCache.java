package com.adhar.adharkit.cache.dapr;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.GetOptions;
import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Spring {@link org.springframework.cache.Cache} in a Dapr state store, so {@code @Cacheable}
 * goes through the sidecar to whatever store the platform configured (Redis/Valkey, Postgres,
 * Cosmos DB, ...), shared by every replica.
 *
 * <p>Entries are written with {@code ttlInSeconds} metadata, so they expire in the store. Keys are
 * {@code cache:<cache>:<generation>:<key>}: a state store cannot list its keys, so {@link #clear()}
 * starts a new generation instead of deleting entries (the old ones are no longer read and expire
 * on their own). Other replicas notice a clear within {@code generationTtl}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class DaprCache extends AbstractValueAdaptingCache {

    private final String name;
    private final DaprFacade dapr;
    private final String store;
    private final String keyPrefix;
    private final Duration ttl;
    private final Duration generationTtl;
    private final DaprCacheValueCodec codec;
    private final ConcurrentHashMap<Object, ReentrantLock> loadLocks = new ConcurrentHashMap<>();
    private volatile long generation = -1;
    private volatile long generationReadAt;

    /**
     * @param name            the cache name
     * @param dapr            the facade
     * @param store           the state store component
     * @param keyPrefix       prefix of every key ({@code cache:})
     * @param ttl             entry time-to-live ({@code null} or non-positive for none)
     * @param generationTtl   how long a replica trusts its view of the cache's generation
     * @param allowNullValues whether {@code null} results are cached
     * @param codec           the value codec
     */
    public DaprCache(String name, DaprFacade dapr, String store, String keyPrefix, Duration ttl,
                     Duration generationTtl, boolean allowNullValues, DaprCacheValueCodec codec) {
        super(allowNullValues);
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.keyPrefix = keyPrefix == null ? "cache:" : keyPrefix;
        this.ttl = ttl;
        this.generationTtl = generationTtl == null ? Duration.ofSeconds(2) : generationTtl;
        this.codec = codec != null ? codec : new DaprCacheValueCodec();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return dapr;
    }

    /** The state store the entries live in. */
    public String getStore() {
        return store;
    }

    @Override
    protected Object lookup(Object key) {
        byte[] stored = dapr.getState(store, stateKey(key), byte[].class);
        return stored == null || stored.length == 0 ? null : codec.decode(stored);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return (T) cached.get();
        }
        // One loader per key and replica; Dapr has no cross-replica single-flight.
        ReentrantLock lock = loadLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            cached = get(key);
            if (cached != null) {
                return (T) cached.get();
            }
            T value;
            try {
                value = valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
            put(key, value);
            return value;
        } finally {
            lock.unlock();
            loadLocks.remove(key, lock);
        }
    }

    @Override
    public void put(Object key, Object value) {
        put(key, value, ttl);
    }

    /**
     * Stores a value with its own time-to-live.
     *
     * @param key   the key
     * @param value the value
     * @param entryTtl the time-to-live, or {@code null} for none
     */
    public void put(Object key, Object value, Duration entryTtl) {
        Object storeValue = toStoreValue(value);
        dapr.saveState(store, stateKey(key), codec.encode(storeValue), options(entryTtl));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        String stateKey = stateKey(key);
        try {
            // First-write without an ETag is an insert: atomic in the store.
            dapr.saveState(store, stateKey, codec.encode(toStoreValue(value)),
                    options(ttl).withConcurrency(com.adhar.kit.dapr.state.Concurrency.FIRST_WRITE));
            return null;
        } catch (DaprError e) {
            if (!e.isConflict() && (e.getDaprMessage() == null || !e.getDaprMessage().toLowerCase().contains("etag"))) {
                throw e;
            }
            return get(key);
        }
    }

    @Override
    public void evict(Object key) {
        dapr.deleteState(store, stateKey(key));
    }

    @Override
    public void clear() {
        String generationKey = generationKey();
        for (int attempt = 1; attempt <= 5; attempt++) {
            StateEntry<Long> current = dapr.getStateEntry(store, generationKey, TypeRef.get(Long.class), GetOptions.none());
            long next = (current.value() == null ? 0 : current.value()) + 1;
            SaveOptions options = current.etag() == null
                    ? SaveOptions.none().withConcurrency(com.adhar.kit.dapr.state.Concurrency.FIRST_WRITE)
                    : SaveOptions.etag(current.etag());
            try {
                dapr.saveState(store, generationKey, next, options);
                generation = next;
                generationReadAt = System.nanoTime();
                log.debug("Cleared Dapr cache '{}' (generation {})", name, next);
                return;
            } catch (DaprError e) {
                if (!e.isConflict() && (e.getDaprMessage() == null || !e.getDaprMessage().toLowerCase().contains("etag"))) {
                    throw e;
                }
            }
        }
        log.debug("Dapr cache '{}' was cleared concurrently by another replica", name);
        generation = -1;
    }

    private SaveOptions options(Duration entryTtl) {
        return entryTtl == null || entryTtl.isZero() || entryTtl.isNegative() ? SaveOptions.none() : SaveOptions.ttl(entryTtl);
    }

    String stateKey(Object key) {
        return keyPrefix + name + ":" + currentGeneration() + ":" + key;
    }

    private String generationKey() {
        return keyPrefix + name + ":generation";
    }

    private long currentGeneration() {
        long now = System.nanoTime();
        if (generation < 0 || now - generationReadAt > generationTtl.toNanos()) {
            Long stored = dapr.getState(store, generationKey(), Long.class);
            generation = stored == null ? 0 : stored;
            generationReadAt = now;
        }
        return generation;
    }
}
