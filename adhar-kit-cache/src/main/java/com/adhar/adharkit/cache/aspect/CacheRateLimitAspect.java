package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheRateLimit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Aspect implementing {@link CacheRateLimit}: token-bucket throughput limiting
 * plus a concurrency cap on cache-backed methods, protecting the backing loader
 * from stampedes.
 *
 * <p>One limiter is kept per annotated method. Attribute semantics:</p>
 * <ul>
 *   <li>{@code maxPerSecond} - a token bucket with capacity and refill rate of
 *       {@code maxPerSecond} tokens/second; each invocation consumes one token
 *       and waits (up to {@code timeout}) for the next token when the bucket is
 *       empty.</li>
 *   <li>{@code maxConcurrent} - a semaphore bounding the number of invocations
 *       executing simultaneously; acquisition also honors {@code timeout}.</li>
 *   <li>{@code timeout} - maximum milliseconds to wait for both permits; when
 *       exceeded, {@link CacheRateLimitExceededException} is thrown instead of
 *       invoking the method.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CacheRateLimitAspect {

    private final ConcurrentHashMap<String, MethodRateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * Applies rate limiting around {@link CacheRateLimit} methods.
     *
     * @param joinPoint     the intercepted invocation
     * @param cacheRateLimit the annotation instance
     * @return the method result
     * @throws CacheRateLimitExceededException when no permit becomes available
     *                                         within {@code timeout}
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cacheRateLimit)")
    public Object aroundCacheRateLimit(ProceedingJoinPoint joinPoint, CacheRateLimit cacheRateLimit) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String limiterKey = method.getDeclaringClass().getName() + "." + method.getName();
        MethodRateLimiter limiter = limiters.computeIfAbsent(limiterKey,
            key -> new MethodRateLimiter(cacheRateLimit.maxConcurrent(), cacheRateLimit.maxPerSecond()));

        long deadlineNanos = System.nanoTime()
            + TimeUnit.MILLISECONDS.toNanos(Math.max(0, cacheRateLimit.timeout()));

        if (!limiter.tryAcquireToken(deadlineNanos)) {
            throw new CacheRateLimitExceededException(
                "Rate limit of " + cacheRateLimit.maxPerSecond() + "/s exceeded for " + limiterKey
                    + " (timeout=" + cacheRateLimit.timeout() + " ms)");
        }
        if (!limiter.tryAcquireConcurrency(deadlineNanos)) {
            throw new CacheRateLimitExceededException(
                "Concurrency limit of " + cacheRateLimit.maxConcurrent() + " exceeded for " + limiterKey
                    + " (timeout=" + cacheRateLimit.timeout() + " ms)");
        }
        try {
            return joinPoint.proceed();
        } finally {
            limiter.releaseConcurrency();
        }
    }

    /**
     * Number of methods with an active limiter (visible for tests/monitoring).
     *
     * @return limiter count
     */
    public int getLimiterCount() {
        return limiters.size();
    }

    /**
     * Per-method limiter combining a token bucket (throughput) with a
     * semaphore (concurrency).
     */
    static final class MethodRateLimiter {

        private final Semaphore concurrency;
        private final double capacity;
        private final double tokensPerNano;

        private double tokens;
        private long lastRefillNanos;

        MethodRateLimiter(int maxConcurrent, int maxPerSecond) {
            this.concurrency = new Semaphore(Math.max(1, maxConcurrent), true);
            this.capacity = Math.max(1, maxPerSecond);
            this.tokensPerNano = this.capacity / TimeUnit.SECONDS.toNanos(1);
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        /**
         * Consumes one token, waiting until the bucket refills or the deadline
         * passes.
         *
         * @param deadlineNanos absolute {@link System#nanoTime()} deadline
         * @return true if a token was consumed before the deadline
         */
        boolean tryAcquireToken(long deadlineNanos) throws InterruptedException {
            while (true) {
                long waitNanos;
                synchronized (this) {
                    refill();
                    if (tokens >= 1.0) {
                        tokens -= 1.0;
                        return true;
                    }
                    waitNanos = (long) Math.ceil((1.0 - tokens) / tokensPerNano);
                }
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0 || waitNanos > remaining) {
                    return false;
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException("Interrupted while waiting for rate-limit token");
                }
                LockSupport.parkNanos(Math.min(waitNanos, TimeUnit.MILLISECONDS.toNanos(10)));
            }
        }

        /**
         * Acquires a concurrency permit within the remaining deadline budget.
         *
         * @param deadlineNanos absolute {@link System#nanoTime()} deadline
         * @return true if a permit was acquired before the deadline
         */
        boolean tryAcquireConcurrency(long deadlineNanos) throws InterruptedException {
            long remaining = Math.max(0, deadlineNanos - System.nanoTime());
            return concurrency.tryAcquire(remaining, TimeUnit.NANOSECONDS);
        }

        void releaseConcurrency() {
            concurrency.release();
        }

        int availableConcurrency() {
            return concurrency.availablePermits();
        }

        private void refill() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = now;
        }
    }

    /**
     * Thrown when the rate or concurrency limit cannot be satisfied within the
     * annotation's {@code timeout}.
     */
    public static class CacheRateLimitExceededException extends RuntimeException {
        public CacheRateLimitExceededException(String message) {
            super(message);
        }
    }
}
