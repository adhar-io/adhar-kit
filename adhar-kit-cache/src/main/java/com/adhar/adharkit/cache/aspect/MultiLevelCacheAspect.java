package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.MultiLevelCache;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Aspect implementing {@link MultiLevelCache}: read-through L1/L2 caching with
 * optional write-through, delegating to {@link MultiLevelCacheService}.
 *
 * <p>Honors {@code l1Cache}, {@code l2Cache}, {@code key} (SpEL),
 * {@code l1Ttl}/{@code l2Ttl} (minutes) and {@code writeThrough}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class MultiLevelCacheAspect {

    private final MultiLevelCacheService multiLevelCacheService;
    private final CacheKeyGenerator keyGenerator;

    /**
     * Creates the aspect.
     *
     * @param multiLevelCacheService the L1/L2 composition
     * @param keyGenerator           the key evaluator
     */
    public MultiLevelCacheAspect(MultiLevelCacheService multiLevelCacheService,
                                 CacheKeyGenerator keyGenerator) {
        this.multiLevelCacheService = multiLevelCacheService;
        this.keyGenerator = keyGenerator;
    }

    /**
     * Applies multi-level get-or-compute around annotated methods.
     *
     * @param joinPoint       the intercepted invocation
     * @param multiLevelCache the annotation instance
     * @return the cached or computed value
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(multiLevelCache)")
    public Object aroundMultiLevelCache(ProceedingJoinPoint joinPoint,
                                        MultiLevelCache multiLevelCache) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object key = keyGenerator.generate(
            multiLevelCache.key(), method, joinPoint.getTarget(), joinPoint.getArgs());

        try {
            return multiLevelCacheService.get(
                multiLevelCache.l1Cache(),
                multiLevelCache.l2Cache(),
                key,
                Duration.ofMinutes(multiLevelCache.l1Ttl()),
                Duration.ofMinutes(multiLevelCache.l2Ttl()),
                multiLevelCache.writeThrough(),
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable t) {
                        throw new CachingAspect.ProceedException(t);
                    }
                });
        } catch (CachingAspect.ProceedException e) {
            throw e.getCause();
        }
    }
}
