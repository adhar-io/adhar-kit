package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.annotation.AiCache;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect for processing @AiCache annotations.
 *
 * <p>Caches AI responses to reduce costs and improve performance.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(50)  // Execute before other AI aspects
@Slf4j
public class AiCacheAspect {

    // Simple in-memory cache (in production, use Caffeine, Redis, etc.)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Intercepts methods annotated with @AiCache.
     */
    @Around("@annotation(aiCache)")
    public Object processCacheAnnotation(ProceedingJoinPoint joinPoint, AiCache aiCache) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // Generate cache key
        String cacheKey = generateCacheKey(
            signature.getMethod().toString(),
            joinPoint.getArgs(),
            aiCache
        );

        // Check cache
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired(aiCache.ttl())) {
            log.debug("Cache HIT for key: {}", cacheKey);
            return entry.getValue();
        }

        log.debug("Cache MISS for key: {}", cacheKey);

        // Execute method
        Object result = joinPoint.proceed();

        // Cache result
        if (result != null) {
            cache.put(cacheKey, new CacheEntry(result));

            // Cleanup old entries if cache is too large
            if (cache.size() > aiCache.maxEntries()) {
                cleanupCache(aiCache.maxEntries());
            }
        }

        return result;
    }

    /**
     * Generates cache key from method and parameters.
     */
    private String generateCacheKey(String methodName, Object[] args, AiCache aiCache) {
        StringBuilder key = new StringBuilder();
        key.append(aiCache.cacheName()).append("::");
        key.append(methodName).append("::");

        if (aiCache.includeAllParams()) {
            key.append(Arrays.deepHashCode(args));
        } else if (aiCache.includeParams().length > 0) {
            // Only include specific parameters
            for (int i : getIncludeIndices(aiCache.includeParams(), args.length)) {
                if (i < args.length) {
                    key.append(args[i]).append("::");
                }
            }
        }

        return key.toString();
    }

    /**
     * Gets indices of parameters to include.
     */
    private int[] getIncludeIndices(String[] includeParams, int totalParams) {
        // Simple implementation - assumes parameter names are "0", "1", etc.
        // In production, use parameter name resolution
        return new int[]{0}; // Default to first parameter
    }

    /**
     * Cleanup old cache entries.
     */
    private void cleanupCache(int maxEntries) {
        if (cache.size() <= maxEntries) {
            return;
        }

        // Remove oldest entries
        int toRemove = cache.size() - maxEntries;
        cache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e1.getValue().getTimestamp(), e2.getValue().getTimestamp()))
            .limit(toRemove)
            .forEach(e -> cache.remove(e.getKey()));

        log.debug("Cleaned up {} cache entries", toRemove);
    }

    /**
     * Cache entry with timestamp.
     */
    private static class CacheEntry {
        private final Object value;
        private final long timestamp;

        public CacheEntry(Object value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public Object getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isExpired(int ttlSeconds) {
            long ttlMillis = ttlSeconds * 1000L;
            return (System.currentTimeMillis() - timestamp) > ttlMillis;
        }
    }
}

