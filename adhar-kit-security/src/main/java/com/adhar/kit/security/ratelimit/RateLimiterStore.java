package com.adhar.kit.security.ratelimit;

/**
 * Pluggable storage abstraction for the request-throttling counters used by the
 * rate-limiting filter.
 *
 * <p>Extracted from the filter so the default in-memory counter map
 * ({@link InMemoryRateLimiterStore}) can be swapped for a distributed
 * implementation (e.g. {@link RedisRateLimiterStore}) without changing the filter
 * logic. Implementations must be safe for concurrent use.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 * @see InMemoryRateLimiterStore
 * @see RedisRateLimiterStore
 */
public interface RateLimiterStore {

    /**
     * Result of attempting to consume a request slot for a client within a window.
     *
     * @param allowed whether the request is permitted
     * @param remaining remaining requests in the current window (never negative)
     * @param resetTimeMillis epoch millis at which the current window resets
     */
    record Decision(boolean allowed, int remaining, long resetTimeMillis) {}

    /**
     * Attempts to consume a request slot for the given client using a fixed window.
     *
     * @param clientId the client identifier (e.g. IP address)
     * @param maxRequests maximum requests allowed within the window
     * @param windowMs window length in milliseconds
     * @return the decision including remaining count and window reset time
     */
    Decision tryAcquire(String clientId, int maxRequests, long windowMs);
}
