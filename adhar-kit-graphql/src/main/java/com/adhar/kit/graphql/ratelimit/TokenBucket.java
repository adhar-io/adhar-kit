package com.adhar.kit.graphql.ratelimit;

/**
 * A thread-safe token bucket used for cost-based rate limiting.
 *
 * <p>The bucket holds up to {@code capacity} tokens and refills continuously at
 * {@code refillPerSecond} tokens per second (fractional refill is accumulated across
 * calls, so slow drip rates are honoured exactly). A request consuming {@code cost}
 * tokens is admitted only when at least {@code cost} tokens are currently available;
 * otherwise it is rejected and no tokens are deducted.</p>
 *
 * <p>Tokens are refilled lazily on each {@link #tryConsume(long)} call based on the
 * elapsed wall-clock time since the previous refill, so an idle bucket costs nothing.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TokenBucket {

    private final long capacity;
    private final double refillPerSecond;

    private double tokens;
    private long lastRefillNanos;

    /**
     * Creates a bucket that starts full.
     *
     * @param capacity        maximum number of tokens the bucket can hold (and burst)
     * @param refillPerSecond number of tokens added per second
     */
    public TokenBucket(long capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System.nanoTime());
    }

    /**
     * Creates a bucket that starts full, using the supplied clock reading as the initial
     * refill timestamp. Package-visible to allow deterministic testing.
     *
     * @param capacity        maximum number of tokens the bucket can hold
     * @param refillPerSecond number of tokens added per second
     * @param nowNanos        the initial clock reading in nanoseconds
     */
    TokenBucket(long capacity, double refillPerSecond, long nowNanos) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /**
     * Attempts to consume the given number of tokens.
     *
     * @param cost the number of tokens the request costs (values below 1 are treated as 1)
     * @return true if the tokens were available and consumed, false otherwise
     */
    public synchronized boolean tryConsume(long cost) {
        return tryConsume(cost, System.nanoTime());
    }

    /**
     * Attempts to consume tokens using the supplied clock reading. Package-visible for tests.
     *
     * @param cost     the number of tokens the request costs
     * @param nowNanos the current clock reading in nanoseconds
     * @return true if consumed
     */
    synchronized boolean tryConsume(long cost, long nowNanos) {
        long effectiveCost = Math.max(cost, 1);
        refill(nowNanos);
        if (tokens >= effectiveCost) {
            tokens -= effectiveCost;
            return true;
        }
        return false;
    }

    /**
     * Returns the current (refilled) token count. Package-visible for tests.
     *
     * @return the number of available tokens
     */
    synchronized double availableTokens() {
        return availableTokensAt(System.nanoTime());
    }

    /**
     * Returns the token count refilled to the supplied clock reading. Package-visible for tests.
     *
     * @param nowNanos the current clock reading in nanoseconds
     * @return the number of available tokens
     */
    synchronized double availableTokensAt(long nowNanos) {
        refill(nowNanos);
        return tokens;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double refilled = (elapsed / 1_000_000_000.0) * refillPerSecond;
        if (refilled > 0) {
            tokens = Math.min(capacity, tokens + refilled);
            lastRefillNanos = nowNanos;
        }
    }
}
