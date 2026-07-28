package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CacheMonitor;
import com.adhar.adharkit.cache.annotation.Cacheable;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.metrics.CacheMetricsBinder;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Aspect implementing {@link CacheMonitor}: records hit/miss counters and
 * invocation latency for annotated cache operations into Micrometer, and raises
 * alert meters/log warnings when the underlying cache degrades.
 *
 * <p>Registered meters (under the annotation's {@code metricsPrefix}, default
 * {@code cache}):</p>
 * <ul>
 *   <li>{@code <prefix>.gets} tagged {@code method=<Class.method>} and
 *       {@code result=hit|miss} - incremented per invocation when the method is
 *       co-annotated with {@link Cacheable} (the hit/miss outcome is determined
 *       by probing the cache with the same generated key before proceeding).</li>
 *   <li>{@code <prefix>.latency} tagged {@code method=<Class.method>} - a timer
 *       recording the end-to-end invocation latency (cache hit or full load).</li>
 *   <li>{@code <prefix>.alerts} tagged {@code method} and
 *       {@code type=hit-rate|eviction-rate} - incremented (with a WARN log) when
 *       the cache's hit rate drops below {@code alertOnHitRateBelow} or its
 *       eviction rate (evictions / requests) exceeds
 *       {@code alertOnEvictionRateAbove}.</li>
 * </ul>
 *
 * <p>{@code detailedLogging=true} additionally logs each invocation's outcome
 * and latency at INFO level. Cache-level Caffeine meters are bound lazily via
 * the shared {@link CacheMetricsBinder} whenever a monitored cache is seen.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class CacheMonitorAspect {

    private final CacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final MeterRegistry meterRegistry;
    private final CacheMetricsBinder metricsBinder;

    /**
     * Creates the aspect.
     *
     * @param cacheManager  the cache manager providing named caches
     * @param keyGenerator  the key evaluator (mirrors {@link CachingAspect} keys)
     * @param meterRegistry the Micrometer registry receiving the meters
     * @param metricsBinder the shared cache metrics binder (may be null)
     */
    public CacheMonitorAspect(CacheManager cacheManager,
                              CacheKeyGenerator keyGenerator,
                              MeterRegistry meterRegistry,
                              CacheMetricsBinder metricsBinder) {
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.meterRegistry = meterRegistry;
        this.metricsBinder = metricsBinder;
    }

    /**
     * Records hit/miss, latency and alert meters around {@link CacheMonitor} methods.
     *
     * @param joinPoint    the intercepted invocation
     * @param cacheMonitor the annotation instance
     * @return the method result
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cacheMonitor)")
    public Object aroundCacheMonitor(ProceedingJoinPoint joinPoint, CacheMonitor cacheMonitor) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String methodTag = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        String prefix = cacheMonitor.metricsPrefix() == null || cacheMonitor.metricsPrefix().isBlank()
            ? "cache" : cacheMonitor.metricsPrefix();

        String cacheName = resolveMonitoredCacheName(joinPoint, method);
        Boolean hit = null;
        if (cacheName != null) {
            hit = probeHit(joinPoint, method, cacheName);
            meterRegistry.counter(prefix + ".gets",
                "method", methodTag, "result", hit ? "hit" : "miss").increment();
        }

        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            Timer.builder(prefix + ".latency")
                .tag("method", methodTag)
                .description("Latency of monitored cache operations")
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

            if (cacheMonitor.detailedLogging()) {
                log.info("CacheMonitor [{}]: cache='{}' result={} latency={} ms",
                    methodTag, cacheName,
                    hit == null ? "n/a" : (hit ? "hit" : "miss"),
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
            }
            if (cacheName != null) {
                if (metricsBinder != null) {
                    metricsBinder.bindCache(cacheName);
                }
                checkAlerts(cacheMonitor, prefix, methodTag, cacheName);
            }
        }
    }

    /**
     * Resolves the cache name monitored by this method from a co-located
     * {@link Cacheable} annotation (blank cacheName falls back to the method name,
     * mirroring {@link CachingAspect}).
     *
     * @return the cache name, or null when the method has no {@link Cacheable}
     */
    private String resolveMonitoredCacheName(ProceedingJoinPoint joinPoint, Method method) {
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        if (cacheable == null) {
            return null;
        }
        return cacheable.cacheName().isBlank() ? method.getName() : cacheable.cacheName();
    }

    /**
     * Probes the cache for the invocation's key without touching Caffeine stats.
     */
    private boolean probeHit(ProceedingJoinPoint joinPoint, Method method, String cacheName) {
        CacheFacade cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return false;
        }
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        Object key = keyGenerator.generate(
            cacheable.key(), method, joinPoint.getTarget(), joinPoint.getArgs());
        return cache.containsKey(key);
    }

    /**
     * Raises alert meters/logs based on the cache's aggregate statistics.
     */
    private void checkAlerts(CacheMonitor cacheMonitor, String prefix, String methodTag, String cacheName) {
        CacheFacade cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        CacheStats stats = cache.stats();
        long requests = stats.requestCount();
        if (requests <= 0) {
            return;
        }
        double hitRate = stats.hitRate();
        if (hitRate < cacheMonitor.alertOnHitRateBelow()) {
            log.warn("CacheMonitor alert [{}]: hit rate {} of cache '{}' is below threshold {}",
                methodTag, String.format("%.2f", hitRate), cacheName, cacheMonitor.alertOnHitRateBelow());
            meterRegistry.counter(prefix + ".alerts",
                "method", methodTag, "type", "hit-rate").increment();
        }
        double evictionRate = (double) stats.evictionCount() / requests;
        if (evictionRate > cacheMonitor.alertOnEvictionRateAbove()) {
            log.warn("CacheMonitor alert [{}]: eviction rate {} of cache '{}' exceeds threshold {}",
                methodTag, String.format("%.2f", evictionRate), cacheName,
                cacheMonitor.alertOnEvictionRateAbove());
            meterRegistry.counter(prefix + ".alerts",
                "method", methodTag, "type", "eviction-rate").increment();
        }
    }
}
