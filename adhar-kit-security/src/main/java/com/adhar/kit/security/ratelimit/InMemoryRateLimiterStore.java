package com.adhar.kit.security.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default in-memory {@link RateLimiterStore} backed by a {@link ConcurrentHashMap}
 * of per-client fixed-window counters.
 *
 * <p>Suitable for single-node deployments and tests. For multi-node deployments a
 * distributed store (e.g. {@link RedisRateLimiterStore}) should be used instead so
 * limits are enforced across instances.</p>
 *
 * <p>This preserves the exact sliding-window-reset behaviour that previously lived
 * inside the rate-limiting filter, including periodic cleanup of stale entries.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class InMemoryRateLimiterStore implements RateLimiterStore {

    private static final long CLEANUP_INTERVAL_MS = 60_000L; // 1 minute

    private final Map<String, RateLimitEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    /**
     * Per-client fixed-window counter.
     */
    private static class RateLimitEntry {
        final AtomicInteger requestCount = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();

        synchronized boolean tryAcquire(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                windowStart = now;
                requestCount.set(1);
                return true;
            }
            if (requestCount.get() < maxRequests) {
                requestCount.incrementAndGet();
                return true;
            }
            return false;
        }

        int getRemaining(int maxRequests) {
            return Math.max(0, maxRequests - requestCount.get());
        }

        long getResetTime(long windowMs) {
            return windowStart + windowMs;
        }
    }

    @Override
    public Decision tryAcquire(String clientId, int maxRequests, long windowMs) {
        cleanupOldEntries(windowMs);

        RateLimitEntry entry = cache.computeIfAbsent(clientId, k -> new RateLimitEntry());
        boolean allowed = entry.tryAcquire(maxRequests, windowMs);
        return new Decision(allowed, entry.getRemaining(maxRequests), entry.getResetTime(windowMs));
    }

    /**
     * Removes counters whose window is well in the past to prevent unbounded growth.
     */
    private void cleanupOldEntries(long windowMs) {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MS) {
            return;
        }
        if (lastCleanup.compareAndSet(last, now)) {
            long expirationThreshold = now - (windowMs * 2);
            cache.entrySet().removeIf(e -> e.getValue().windowStart < expirationThreshold);
            log.debug("Rate limit cache cleanup completed. Remaining entries: {}", cache.size());
        }
    }

    /**
     * Number of tracked clients (for monitoring/testing).
     *
     * @return current cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
}
