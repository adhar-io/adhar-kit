package com.adhar.kit.analytics.flag;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user, per-flag cache of PostHog {@code /decide} results with a
 * time-to-live so entries neither trigger a network call on every request
 * nor are served forever once PostHog's decision has changed.
 *
 * <p>Uses lazy expiry: an expired entry is detected (and evicted) the next
 * time it is read via {@link #get(String, String)}, rather than a background
 * sweep thread. This keeps the cache dependency-free and trivially testable
 * with an injectable {@link Clock}; the tradeoff is that an entry which is
 * never read again after expiring lingers in memory until the next
 * {@link #clear()} or a fresh write for the same key. Given per-user/per-flag
 * cardinality is bounded by active users, this is an acceptable tradeoff.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class FeatureFlagCache {

    /**
     * A cached flag decision: both the boolean interpretation and the raw
     * (possibly multivariate) value, plus when this entry expires.
     */
    public record CachedFlag(Object value, boolean enabled, Instant expiresAt) {
    }

    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Map<String, CachedFlag>> cache = new ConcurrentHashMap<>();

    public FeatureFlagCache(Duration ttl, Clock clock) {
        this.ttl = (ttl == null || ttl.isZero() || ttl.isNegative()) ? Duration.ofSeconds(60) : ttl;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Returns the cached flag for the given user/key, or empty if absent or expired.
     */
    public Optional<CachedFlag> get(String userId, String flagKey) {
        Map<String, CachedFlag> userFlags = cache.get(userId);
        if (userFlags == null) {
            return Optional.empty();
        }
        CachedFlag flag = userFlags.get(flagKey);
        if (flag == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(flag.expiresAt())) {
            userFlags.remove(flagKey, flag);
            return Optional.empty();
        }
        return Optional.of(flag);
    }

    /**
     * Caches a flag decision, resetting its TTL.
     */
    public void put(String userId, String flagKey, Object value, boolean enabled) {
        cache.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(flagKey, new CachedFlag(value, enabled, clock.instant().plus(ttl)));
    }

    /**
     * Clears every cached decision for every user (used by {@code reloadFeatureFlags()}).
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Number of distinct users currently holding cached entries (diagnostic use).
     */
    public int userCount() {
        return cache.size();
    }

    public Duration ttl() {
        return ttl;
    }
}
