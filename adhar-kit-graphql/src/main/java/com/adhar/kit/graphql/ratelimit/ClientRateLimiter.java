package com.adhar.kit.graphql.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maintains a per-client {@link TokenBucket} and admits or rejects requests based on
 * their estimated query cost.
 *
 * <p>Buckets are created lazily per client id and held in a bounded LRU map so that a
 * flood of distinct client ids cannot exhaust memory; the least-recently-used bucket is
 * evicted once {@code maxClients} is exceeded. All buckets share the same capacity and
 * refill rate.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ClientRateLimiter {

    private final long capacity;
    private final double refillPerSecond;
    private final Map<String, TokenBucket> buckets;

    /**
     * Creates a rate limiter.
     *
     * @param capacity        per-client token bucket capacity (max burst cost)
     * @param refillPerSecond per-client refill rate in cost tokens per second
     * @param maxClients      maximum number of distinct client buckets to retain
     */
    public ClientRateLimiter(long capacity, double refillPerSecond, int maxClients) {
        if (maxClients <= 0) {
            throw new IllegalArgumentException("maxClients must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.buckets = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TokenBucket> eldest) {
                boolean evict = size() > maxClients;
                if (evict) {
                    log.debug("Evicting rate-limit bucket for least-recently-used client {}", eldest.getKey());
                }
                return evict;
            }
        });
    }

    /**
     * Attempts to admit a request from the given client at the given cost.
     *
     * @param clientId the resolved client identifier
     * @param cost     the estimated query cost
     * @return true if the request is within budget and admitted, false if it should be rejected
     */
    public boolean tryAcquire(String clientId, long cost) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                id -> new TokenBucket(capacity, refillPerSecond));
        boolean allowed = bucket.tryConsume(cost);
        if (!allowed) {
            log.debug("Rate limit exceeded for client {} (cost {})", clientId, cost);
        }
        return allowed;
    }

    /**
     * Returns the number of active client buckets currently retained.
     *
     * @return the bucket count
     */
    public int clientCount() {
        return buckets.size();
    }

    /**
     * Removes all buckets, resetting every client's budget.
     */
    public void clear() {
        buckets.clear();
    }
}
