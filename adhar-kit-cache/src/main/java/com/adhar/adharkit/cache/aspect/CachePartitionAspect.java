package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CachePartition;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.partition.KeyPartitionResolver;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;

/**
 * Aspect implementing {@link CachePartition}: tenant-partitioned get-or-compute
 * caching for multi-tenant applications.
 *
 * <p>The tenant is resolved from the method parameter named by
 * {@code tenantParam}; when that parameter is absent or null/blank, the
 * configured {@link KeyPartitionResolver} (by default the thread-local tenant
 * from {@code TenantContextHolder}) is consulted as a fallback. Each tenant gets
 * its own physical cache named {@code <cacheName><separator><tenant>} created
 * with the annotation's per-tenant {@code ttl} (minutes), so tenants never share
 * entries or eviction pressure. When no tenant is resolvable at all, the base
 * {@code cacheName} is used unpartitioned.</p>
 *
 * <p>The entry key is the annotation's SpEL {@code key} expression evaluated
 * against the invocation (same semantics as {@link CachingAspect}). Semantics
 * are get-or-compute: a hit skips the invocation; a non-null result is stored.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class CachePartitionAspect {

    private final CacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final KeyPartitionResolver partitionResolver;
    private final String partitionSeparator;

    /**
     * Creates the aspect with the default {@code ::} separator.
     *
     * @param cacheManager      the cache manager providing named caches
     * @param keyGenerator      the SpEL key evaluator
     * @param partitionResolver fallback tenant resolver (may be null)
     */
    public CachePartitionAspect(CacheManager cacheManager,
                                CacheKeyGenerator keyGenerator,
                                KeyPartitionResolver partitionResolver) {
        this(cacheManager, keyGenerator, partitionResolver,
            CacheKeyGenerator.DEFAULT_PARTITION_SEPARATOR);
    }

    /**
     * Creates the aspect.
     *
     * @param cacheManager       the cache manager providing named caches
     * @param keyGenerator       the SpEL key evaluator
     * @param partitionResolver  fallback tenant resolver (may be null)
     * @param partitionSeparator separator between base cache name and tenant
     */
    public CachePartitionAspect(CacheManager cacheManager,
                                CacheKeyGenerator keyGenerator,
                                KeyPartitionResolver partitionResolver,
                                String partitionSeparator) {
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.partitionResolver = partitionResolver;
        this.partitionSeparator = partitionSeparator == null || partitionSeparator.isBlank()
            ? CacheKeyGenerator.DEFAULT_PARTITION_SEPARATOR : partitionSeparator;
    }

    /**
     * Applies tenant-partitioned get-or-compute semantics around
     * {@link CachePartition} methods.
     *
     * @param joinPoint      the intercepted invocation
     * @param cachePartition the annotation instance
     * @return the cached or computed value
     * @throws Throwable if the underlying method fails
     */
    @Around("@annotation(cachePartition)")
    public Object aroundCachePartition(ProceedingJoinPoint joinPoint, CachePartition cachePartition) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();

        String tenant = resolveTenant(cachePartition.tenantParam(), method, args);
        String cacheName = tenant == null
            ? cachePartition.cacheName()
            : cachePartition.cacheName() + partitionSeparator + tenant;
        if (tenant == null) {
            log.debug("No tenant resolvable for {}; using unpartitioned cache '{}'",
                method.getName(), cacheName);
        }

        Object key = keyGenerator.generate(cachePartition.key(), method, target, args);
        CacheFacade cache = resolveCache(cacheName, cachePartition.ttl());

        Object cached = cache.get(key);
        if (cached != null) {
            log.trace("Partition cache hit for '{}' key={}", cacheName, key);
            return cached;
        }
        Object result = joinPoint.proceed();
        if (result != null) {
            cache.put(key, result);
        }
        return result;
    }

    /**
     * Resolves the tenant: first from the parameter named {@code tenantParam},
     * then from the {@link KeyPartitionResolver} fallback.
     *
     * @return the tenant, or null when none is resolvable
     */
    private String resolveTenant(String tenantParam, Method method, Object[] args) {
        if (tenantParam != null && !tenantParam.isBlank() && args != null) {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length && i < args.length; i++) {
                if (tenantParam.equals(parameters[i].getName())) {
                    String value = args[i] == null ? null : String.valueOf(args[i]);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                    break;
                }
            }
        }
        String fallback = partitionResolver != null ? partitionResolver.resolvePartition() : null;
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    /**
     * Resolves (or lazily creates) the tenant's cache, honoring the per-tenant
     * TTL (minutes) on creation.
     */
    private CacheFacade resolveCache(String cacheName, long ttlMinutes) {
        CacheFacade existing = cacheManager.getCache(cacheName);
        if (existing != null) {
            return existing;
        }
        if (ttlMinutes > 0) {
            CacheFacade cache = CacheFacade.builder()
                .cacheName(cacheName)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats(true)
                .build();
            cacheManager.registerCache(cacheName, cache);
            return cache;
        }
        return cacheManager.getOrCreateCache(cacheName);
    }
}
