package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Aspect for processing @AiCache annotations.
 *
 * <p>Caches AI responses to reduce costs and improve performance.</p>
 *
 * <p><b>Implementation notes:</b> each distinct {@link AiCache#cacheName()} gets its
 * own Caffeine {@link Cache}, sized/expired according to that annotation's
 * {@code maxEntries()}/{@code ttl()} (Caffeine handles both TTL and size-based
 * eviction natively, replacing the previous unbounded {@code ConcurrentHashMap}
 * that never evicted anything until it happened to exceed {@code maxEntries()} on
 * a cache write, and even then only via a manual timestamp sort). The cache key
 * generation also honours {@code includeParams()}/{@code excludeParams()} against
 * the intercepted method's actual parameter names/positions, instead of always
 * defaulting to parameter index 0.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(50)  // Execute before other AI aspects
@Slf4j
public class AiCacheAspect {

    /** One Caffeine cache per logical {@code cacheName}, built lazily on first use. */
    private final Map<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();

    /**
     * Intercepts methods annotated with @AiCache.
     */
    @Around("@annotation(aiCache)")
    public Object processCacheAnnotation(ProceedingJoinPoint joinPoint, AiCache aiCache) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();

        Cache<String, Object> cache = caches.computeIfAbsent(aiCache.cacheName(), name -> buildCache(aiCache));

        String cacheKey = generateCacheKey(signature, args, aiCache);

        Object cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT for key: {}", cacheKey);
            return cached;
        }

        log.debug("Cache MISS for key: {}", cacheKey);

        Object result = joinPoint.proceed();

        // Caffeine cannot store null values, and a null AI response isn't worth
        // caching anyway, so mirror the original behaviour of skipping the write.
        if (result != null) {
            cache.put(cacheKey, result);
        }

        return result;
    }

    /**
     * Builds a new Caffeine cache configured from the annotation's TTL/size settings.
     * A synchronous executor is used so that size-based eviction (relevant to callers
     * that immediately re-check cache size, e.g. tests) is applied deterministically
     * rather than on Caffeine's default async maintenance schedule.
     */
    private Cache<String, Object> buildCache(AiCache aiCache) {
        return Caffeine.newBuilder()
                .maximumSize(Math.max(aiCache.maxEntries(), 0))
                .expireAfterWrite(Math.max(aiCache.ttl(), 0), TimeUnit.SECONDS)
                .executor(Runnable::run)
                .build();
    }

    /**
     * Generates cache key from method and parameters, honouring the annotation's
     * {@code includeAllParams}/{@code includeParams}/{@code excludeParams} configuration.
     */
    private String generateCacheKey(MethodSignature signature, Object[] args, AiCache aiCache) {
        StringBuilder key = new StringBuilder();
        key.append(aiCache.cacheName()).append("::");
        key.append(signature.getMethod().toString()).append("::");

        String[] paramNames = signature.getParameterNames();

        if (aiCache.includeAllParams()) {
            Object[] keyArgs = excludeParams(args, paramNames, aiCache.excludeParams());
            key.append(Arrays.deepHashCode(keyArgs));
        } else if (aiCache.includeParams().length > 0) {
            for (int i : resolveIndices(aiCache.includeParams(), paramNames, args.length)) {
                if (i >= 0 && i < args.length) {
                    key.append(args[i]).append("::");
                }
            }
        }

        return key.toString();
    }

    /**
     * Resolves the {@code includeParams()}/{@code excludeParams()} entries (which may
     * be either numeric positions - "0", "1" - or actual parameter names) to concrete
     * argument indices.
     */
    private int[] resolveIndices(String[] paramRefs, String[] paramNames, int totalParams) {
        List<Integer> indices = new ArrayList<>();
        for (String ref : paramRefs) {
            Integer index = resolveIndex(ref, paramNames, totalParams);
            if (index != null && !indices.contains(index)) {
                indices.add(index);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private Integer resolveIndex(String ref, String[] paramNames, int totalParams) {
        if (ref == null) {
            return null;
        }
        String trimmed = ref.trim();
        try {
            int idx = Integer.parseInt(trimmed);
            return (idx >= 0 && idx < totalParams) ? idx : null;
        } catch (NumberFormatException ignored) {
            // Not numeric - try resolving by declared parameter name.
        }
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if (trimmed.equals(paramNames[i])) {
                    return i;
                }
            }
        }
        log.warn("Could not resolve @AiCache parameter reference '{}' to an argument index", ref);
        return null;
    }

    private Object[] excludeParams(Object[] args, String[] paramNames, String[] excludeParams) {
        if (excludeParams == null || excludeParams.length == 0) {
            return args;
        }
        int[] excluded = resolveIndices(excludeParams, paramNames, args.length);
        if (excluded.length == 0) {
            return args;
        }
        List<Object> filtered = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            boolean skip = false;
            for (int ex : excluded) {
                if (ex == i) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                filtered.add(args[i]);
            }
        }
        return filtered.toArray();
    }
}
