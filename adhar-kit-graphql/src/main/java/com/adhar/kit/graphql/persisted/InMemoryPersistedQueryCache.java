package com.adhar.kit.graphql.persisted;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded, thread-safe in-memory implementation of {@link PersistedQueryCache}.
 *
 * <p>Backed by an access-order {@link LinkedHashMap} used as an LRU cache: once the
 * configured maximum size is reached, the least-recently-used entry is evicted to make
 * room for new ones. All access is synchronized on the backing map.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class InMemoryPersistedQueryCache implements PersistedQueryCache {

    private final int maxSize;
    private final Map<String, String> cache;

    /**
     * Creates a bounded in-memory persisted-query cache.
     *
     * @param maxSize the maximum number of entries to retain; must be positive
     * @throws IllegalArgumentException if maxSize is not positive
     */
    public InMemoryPersistedQueryCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > InMemoryPersistedQueryCache.this.maxSize;
            }
        };
    }

    @Override
    public synchronized Optional<String> get(String sha256Hash) {
        if (sha256Hash == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(sha256Hash));
    }

    @Override
    public synchronized void put(String sha256Hash, String query) {
        if (sha256Hash == null || sha256Hash.isBlank()) {
            throw new IllegalArgumentException("sha256Hash must not be null or blank");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        cache.put(sha256Hash, query);
        log.debug("Cached persisted query under hash {} (cache size={})", sha256Hash, cache.size());
    }

    @Override
    public synchronized int size() {
        return cache.size();
    }
}
