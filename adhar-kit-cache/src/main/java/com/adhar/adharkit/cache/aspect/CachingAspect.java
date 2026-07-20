package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CacheEvict;
import com.adhar.adharkit.cache.annotation.CachePut;
import com.adhar.adharkit.cache.annotation.Cacheable;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Aspect implementing the core caching trio: {@link Cacheable}, {@link CachePut}
 * and {@link CacheEvict}.
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li><b>@Cacheable</b> - get-or-compute. A cache hit skips the method invocation.
 *       {@code condition} is evaluated before lookup when it does not reference
 *       {@code #result}, otherwise after invocation (with {@code #result} bound).
 *       {@code unless} vetoes storing after invocation. {@code sync=true} uses the
 *       cache's atomic per-key compute so only one thread loads a given key
 *       (condition/unless post-checks are skipped in sync mode, mirroring Spring).
 *       {@code ttl}/{@code ttlUnit} are applied when the cache is first created.</li>
 *   <li><b>@CachePut</b> - always executes the method, then stores the non-null
 *       result when {@code condition} passes and {@code unless} does not veto.</li>
 *   <li><b>@CacheEvict</b> - evicts a single key or clears the whole cache
 *       ({@code allEntries=true}), before or after the invocation depending on
 *       {@code beforeInvocation}, guarded by {@code condition}.</li>
 * </ul>
 *
 * <p>When {@code cacheName} is blank, the method name is used as cache name.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class CachingAspect {

    private final CacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;

    /**
     * Creates the aspect.
     *
     * @param cacheManager the cache manager providing named caches
     * @param keyGenerator the key/condition evaluator
     */
    public CachingAspect(CacheManager cacheManager, CacheKeyGenerator keyGenerator) {
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
    }

    /**
     * Handles {@link Cacheable} methods with get-or-compute semantics.
     *
     * @param joinPoint the intercepted invocation
     * @param cacheable the annotation instance
     * @return the cached or computed value
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        String cacheName = resolveCacheName(cacheable.cacheName(), method);

        String condition = cacheable.condition();
        boolean conditionUsesResult = keyGenerator.referencesResult(condition);
        if (!conditionUsesResult
            && !keyGenerator.evaluateCondition(condition, method, target, args, null)) {
            log.trace("Condition '{}' vetoed caching for {}", condition, method.getName());
            return joinPoint.proceed();
        }

        Object key = keyGenerator.generate(cacheable.key(), method, target, args);
        CacheFacade cache = resolveCache(cacheName, cacheable.ttl(), cacheable.ttlUnit());

        if (cacheable.sync()) {
            try {
                return cache.get(key, k -> proceedUnchecked(joinPoint));
            } catch (ProceedException e) {
                throw e.getCause();
            }
        }

        Object cached = cache.get(key);
        if (cached != null) {
            log.trace("Cache hit for '{}' key={}", cacheName, key);
            return cached;
        }

        Object result = joinPoint.proceed();
        if (result != null && shouldStore(cacheable, conditionUsesResult, method, target, args, result)) {
            cache.put(key, result);
        }
        return result;
    }

    /**
     * Handles {@link CachePut} methods: execute then store.
     *
     * @param joinPoint the intercepted invocation
     * @param cachePut  the annotation instance
     * @return the method result
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cachePut)")
    public Object aroundCachePut(ProceedingJoinPoint joinPoint, CachePut cachePut) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();

        Object result = joinPoint.proceed();

        if (result != null
            && keyGenerator.evaluateCondition(cachePut.condition(), method, target, args, result)
            && !(!cachePut.unless().isBlank()
                && keyGenerator.evaluateCondition(cachePut.unless(), method, target, args, result))) {
            String cacheName = resolveCacheName(cachePut.cacheName(), method);
            Object key = keyGenerator.generate(cachePut.key(), method, target, args);
            resolveCache(cacheName, cachePut.ttl(), cachePut.ttlUnit()).put(key, result);
            log.trace("CachePut stored key={} into '{}'", key, cacheName);
        }
        return result;
    }

    /**
     * Handles {@link CacheEvict} methods: evict a key or clear the cache.
     *
     * @param joinPoint  the intercepted invocation
     * @param cacheEvict the annotation instance
     * @return the method result
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cacheEvict)")
    public Object aroundCacheEvict(ProceedingJoinPoint joinPoint, CacheEvict cacheEvict) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        boolean conditionOk =
            keyGenerator.evaluateCondition(cacheEvict.condition(), method, target, args, null);

        if (conditionOk && cacheEvict.beforeInvocation()) {
            evict(cacheEvict, method, target, args);
        }
        Object result = joinPoint.proceed();
        if (conditionOk && !cacheEvict.beforeInvocation()) {
            evict(cacheEvict, method, target, args);
        }
        return result;
    }

    private void evict(CacheEvict cacheEvict, Method method, Object target, Object[] args) {
        String cacheName = resolveCacheName(cacheEvict.cacheName(), method);
        CacheFacade cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.trace("No cache named '{}' to evict from", cacheName);
            return;
        }
        if (cacheEvict.allEntries()) {
            cache.clear();
            log.trace("Cleared all entries of cache '{}'", cacheName);
        } else {
            Object key = keyGenerator.generate(cacheEvict.key(), method, target, args);
            cache.evict(key);
            log.trace("Evicted key={} from cache '{}'", key, cacheName);
        }
    }

    private boolean shouldStore(Cacheable cacheable, boolean conditionUsesResult,
                                Method method, Object target, Object[] args, Object result) {
        if (conditionUsesResult
            && !keyGenerator.evaluateCondition(cacheable.condition(), method, target, args, result)) {
            return false;
        }
        String unless = cacheable.unless();
        return unless.isBlank()
            || !keyGenerator.evaluateCondition(unless, method, target, args, result);
    }

    /**
     * Resolves (or lazily creates) the cache, honoring the annotation TTL on creation.
     */
    private CacheFacade resolveCache(String cacheName, long ttl, TimeUnit ttlUnit) {
        CacheFacade existing = cacheManager.getCache(cacheName);
        if (existing != null) {
            return existing;
        }
        if (ttl > 0) {
            CacheFacade cache = CacheFacade.builder()
                .cacheName(cacheName)
                .expireAfterWrite(Duration.ofMillis(ttlUnit.toMillis(ttl)))
                .recordStats(true)
                .build();
            cacheManager.registerCache(cacheName, cache);
            return cache;
        }
        return cacheManager.getOrCreateCache(cacheName);
    }

    private static String resolveCacheName(String cacheName, Method method) {
        return cacheName == null || cacheName.isBlank() ? method.getName() : cacheName;
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        return ((MethodSignature) joinPoint.getSignature()).getMethod();
    }

    private static Object proceedUnchecked(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            throw new ProceedException(t);
        }
    }

    /**
     * Wraps checked throwables thrown inside cache loader lambdas.
     */
    static final class ProceedException extends RuntimeException {
        ProceedException(Throwable cause) {
            super(cause);
        }
    }
}
