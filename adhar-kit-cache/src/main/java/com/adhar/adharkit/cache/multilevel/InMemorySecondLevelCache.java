package com.adhar.adharkit.cache.multilevel;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local {@link SecondLevelCache} backed by {@link ConcurrentHashMap}s
 * with per-entry TTL. Default L2 when no distributed backend is configured;
 * also convenient in tests.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class InMemorySecondLevelCache implements SecondLevelCache {

    private final Map<String, Map<Object, Entry>> caches = new ConcurrentHashMap<>();

    @Override
    public Object get(String cacheName, Object key) {
        Map<Object, Entry> cache = caches.get(cacheName);
        if (cache == null) {
            return null;
        }
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        long expiresAt = (ttl == null || ttl.isZero() || ttl.isNegative())
            ? Long.MAX_VALUE
            : System.currentTimeMillis() + ttl.toMillis();
        caches.computeIfAbsent(cacheName, name -> new ConcurrentHashMap<>())
            .put(key, new Entry(value, expiresAt));
    }

    @Override
    public void evict(String cacheName, Object key) {
        Map<Object, Entry> cache = caches.get(cacheName);
        if (cache != null) {
            cache.remove(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        Map<Object, Entry> cache = caches.get(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Number of live (possibly including expired-but-not-purged) entries.
     *
     * @param cacheName logical cache name
     * @return entry count
     */
    public int size(String cacheName) {
        Map<Object, Entry> cache = caches.get(cacheName);
        return cache == null ? 0 : cache.size();
    }

    private record Entry(Object value, long expiresAt) {
        boolean isExpired() {
            return expiresAt != Long.MAX_VALUE && System.currentTimeMillis() >= expiresAt;
        }
    }
}
