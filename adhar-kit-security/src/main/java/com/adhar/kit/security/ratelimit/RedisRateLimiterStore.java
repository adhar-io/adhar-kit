package com.adhar.kit.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Distributed {@link RateLimiterStore} backed by Redis via
 * {@link StringRedisTemplate}, enforcing a fixed-window limit shared across nodes.
 *
 * <p>Each request atomically increments a per-client counter and sets the window
 * expiry on first use, via a small Lua script so the {@code INCR}/{@code PEXPIRE}
 * pair cannot race:</p>
 *
 * <pre>{@code
 * local c = redis.call('INCR', KEYS[1])
 * if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
 * return {c, redis.call('PTTL', KEYS[1])}
 * }</pre>
 *
 * <p>This class is only referenced by beans gated with {@code @ConditionalOnClass},
 * so the module compiles and runs without Redis on the classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class RedisRateLimiterStore implements RateLimiterStore {

    /**
     * Default key namespace prefix.
     */
    public static final String DEFAULT_KEY_PREFIX = "adhar:security:ratelimit:";

    private static final RedisScript<List> INCR_SCRIPT = new DefaultRedisScript<>(
        "local c = redis.call('INCR', KEYS[1]) "
            + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
            + "return {c, redis.call('PTTL', KEYS[1])}",
        List.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    /**
     * Creates the store with the {@link #DEFAULT_KEY_PREFIX default key prefix}.
     *
     * @param redisTemplate the Redis template used for all operations
     */
    public RedisRateLimiterStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX);
    }

    /**
     * Creates the store with a custom key namespace prefix.
     *
     * @param redisTemplate the Redis template used for all operations
     * @param keyPrefix namespace prefix applied to every Redis key
     */
    public RedisRateLimiterStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? DEFAULT_KEY_PREFIX : keyPrefix;
        log.info("Redis rate-limiter store initialized (key prefix: {})", this.keyPrefix);
    }

    @Override
    public Decision tryAcquire(String clientId, int maxRequests, long windowMs) {
        String key = keyPrefix + clientId;
        long effectiveWindow = Math.max(1L, windowMs);

        List<?> result = redisTemplate.execute(
            INCR_SCRIPT, List.of(key), String.valueOf(effectiveWindow));

        long count;
        long pttl;
        if (result != null && result.size() >= 2) {
            count = ((Number) result.get(0)).longValue();
            pttl = ((Number) result.get(1)).longValue();
        } else {
            // Defensive fallback: treat as a single allowed request.
            count = 1;
            pttl = effectiveWindow;
        }

        boolean allowed = count <= maxRequests;
        int remaining = (int) Math.max(0, maxRequests - count);
        long resetTime = System.currentTimeMillis() + Math.max(0, pttl);
        return new Decision(allowed, remaining, resetTime);
    }
}
