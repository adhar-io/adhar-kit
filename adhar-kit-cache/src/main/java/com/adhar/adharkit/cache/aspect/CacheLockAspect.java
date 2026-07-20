package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheLock;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aspect implementing {@link CacheLock}: in-JVM single-flight stampede protection.
 *
 * <p>Concurrent callers computing the same lock key are collapsed into a single
 * execution — the first caller (the "leader") runs the method while the others
 * wait on the leader's {@link CompletableFuture} and share its result.</p>
 *
 * <p>Attribute semantics:</p>
 * <ul>
 *   <li>{@code lockKey} - SpEL expression identifying the lock; scoped per method
 *       so identical keys on different methods do not collide.</li>
 *   <li>{@code waitTime} - maximum seconds a follower waits for the leader's
 *       result; on timeout the follower computes the value itself.</li>
 *   <li>{@code failFast} - if true, followers fail immediately with
 *       {@link CacheLockAcquisitionException} instead of waiting.</li>
 *   <li>{@code leaseTime} - advisory in this in-JVM implementation (the leader
 *       always releases the lock when its invocation finishes).</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class CacheLockAspect {

    private final CacheKeyGenerator keyGenerator;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    /**
     * Creates the aspect.
     *
     * @param keyGenerator the key evaluator for {@code lockKey} expressions
     */
    public CacheLockAspect(CacheKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    /**
     * Applies single-flight semantics around {@link CacheLock} methods.
     *
     * @param joinPoint the intercepted invocation
     * @param cacheLock the annotation instance
     * @return the (possibly shared) method result
     * @throws Throwable if the underlying method fails or the lock cannot be acquired
     */
    @Around("@annotation(cacheLock)")
    public Object aroundCacheLock(ProceedingJoinPoint joinPoint, CacheLock cacheLock) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object keyValue = keyGenerator.generate(
            cacheLock.lockKey(), method, joinPoint.getTarget(), joinPoint.getArgs());
        String lockKey = method.getDeclaringClass().getName() + "." + method.getName() + "::" + keyValue;

        CompletableFuture<Object> ourFuture = new CompletableFuture<>();
        CompletableFuture<Object> leaderFuture = inFlight.putIfAbsent(lockKey, ourFuture);

        if (leaderFuture == null) {
            // We are the leader: compute, publish, release.
            try {
                Object result = joinPoint.proceed();
                ourFuture.complete(result);
                return result;
            } catch (Throwable t) {
                ourFuture.completeExceptionally(t);
                throw t;
            } finally {
                inFlight.remove(lockKey, ourFuture);
            }
        }

        // We are a follower.
        if (cacheLock.failFast()) {
            throw new CacheLockAcquisitionException(
                "Cache lock '" + lockKey + "' is held by another caller (failFast=true)");
        }
        try {
            log.trace("Waiting up to {}s for in-flight computation of '{}'", cacheLock.waitTime(), lockKey);
            return leaderFuture.get(cacheLock.waitTime(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        } catch (TimeoutException e) {
            log.debug("Timed out waiting for '{}' after {}s; computing locally", lockKey, cacheLock.waitTime());
            return joinPoint.proceed();
        }
    }

    /**
     * Number of computations currently in flight (visible for tests/monitoring).
     *
     * @return in-flight count
     */
    public int getInFlightCount() {
        return inFlight.size();
    }

    /**
     * Thrown when {@code failFast=true} and the lock is already held.
     */
    public static class CacheLockAcquisitionException extends RuntimeException {
        public CacheLockAcquisitionException(String message) {
            super(message);
        }
    }
}
