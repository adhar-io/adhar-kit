package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheRefresh;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.refresh.CacheRefreshScheduler;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Aspect implementing {@link CacheRefresh}: on the first invocation of an
 * annotated method (per argument combination), a (cache, key, loader) entry is
 * registered with {@link CacheRefreshScheduler}, which then re-invokes the
 * underlying method every {@code refreshInterval} minutes (after
 * {@code initialDelay} minutes) and stores the fresh result in the cache.
 *
 * <p>The refresh loader invokes the raw target method reflectively, bypassing
 * proxies/aspects so the refresh always recomputes rather than hitting the
 * cache. Refresh execution is inherently asynchronous (scheduler threads);
 * {@code refreshAsync=false} is treated the same way.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class CacheRefreshAspect {

    private final CacheRefreshScheduler scheduler;
    private final CacheKeyGenerator keyGenerator;

    /**
     * Creates the aspect.
     *
     * @param scheduler    the background refresh scheduler
     * @param keyGenerator the key generator (default className.methodName+args key)
     */
    public CacheRefreshAspect(CacheRefreshScheduler scheduler, CacheKeyGenerator keyGenerator) {
        this.scheduler = scheduler;
        this.keyGenerator = keyGenerator;
    }

    /**
     * Proceeds with the invocation and registers the refresh entry.
     *
     * @param joinPoint    the intercepted invocation
     * @param cacheRefresh the annotation instance
     * @return the method result
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cacheRefresh)")
    public Object aroundCacheRefresh(ProceedingJoinPoint joinPoint, CacheRefresh cacheRefresh) throws Throwable {
        Object result = joinPoint.proceed();

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        Object key = keyGenerator.generate(null, method, target, args);

        boolean registered = scheduler.register(
            cacheRefresh.cacheName(),
            key,
            Duration.ofMinutes(cacheRefresh.initialDelay()),
            Duration.ofMinutes(cacheRefresh.refreshInterval()),
            buildLoader(method, target, args));
        if (registered) {
            log.debug("Registered background refresh for cache '{}' key={} every {} minute(s)",
                cacheRefresh.cacheName(), key, cacheRefresh.refreshInterval());
        }
        return result;
    }

    /**
     * Builds a loader that re-invokes the raw target method.
     *
     * @param method the method to invoke
     * @param target the raw target object
     * @param args   the original invocation arguments
     * @return the loader supplier
     */
    Supplier<Object> buildLoader(Method method, Object target, Object[] args) {
        return () -> {
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Cache refresh invocation failed", e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cache refresh invocation not accessible", e);
            }
        };
    }
}
