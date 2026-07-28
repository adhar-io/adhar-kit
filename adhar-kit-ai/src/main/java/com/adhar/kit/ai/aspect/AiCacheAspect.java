package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    /** One bounded-LRU semantic store per {@code cacheName}, built lazily on first use. */
    private final Map<String, SemanticStore> semanticStores = new ConcurrentHashMap<>();

    /**
     * Optional embedding model enabling the semantic (similarity) cache path.
     * {@code null} keeps the aspect on the exact-hash-only behaviour.
     */
    private final EmbeddingModel embeddingModel;

    /** Whether the semantic path is enabled (also requires {@link #embeddingModel}). */
    private final boolean semanticEnabled;

    /** Minimum cosine similarity for a semantic cache hit. */
    private final double similarityThreshold;

    /** Maximum embeddings retained per semantic store. */
    private final int semanticMaxEntries;

    /**
     * Exact-hash-only constructor (no semantic path). Preserves the original
     * behaviour and is used when no {@code EmbeddingModel} is available.
     */
    public AiCacheAspect() {
        this(null, false, 0.95, 1000);
    }

    /**
     * Full constructor enabling the optional embedding-similarity path.
     *
     * @param embeddingModel      embedding model (nullable; disables semantic path)
     * @param semanticEnabled     whether the semantic path is enabled
     * @param similarityThreshold minimum cosine similarity for a semantic hit
     * @param semanticMaxEntries  bounded-LRU size per cache
     */
    public AiCacheAspect(EmbeddingModel embeddingModel, boolean semanticEnabled,
                         double similarityThreshold, int semanticMaxEntries) {
        this.embeddingModel = embeddingModel;
        this.semanticEnabled = semanticEnabled;
        this.similarityThreshold = similarityThreshold;
        this.semanticMaxEntries = Math.max(semanticMaxEntries, 1);
    }

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

        // Optional semantic (embedding-similarity) match on exact-hash miss.
        String semanticText = semanticActive() ? extractSemanticText(signature, args, aiCache) : null;
        float[] queryEmbedding = null;
        if (semanticText != null) {
            queryEmbedding = safeEmbed(semanticText);
            if (queryEmbedding != null) {
                SemanticStore store = semanticStores.computeIfAbsent(aiCache.cacheName(),
                        n -> new SemanticStore(semanticMaxEntries));
                Object semanticHit = store.findSimilar(queryEmbedding, similarityThreshold);
                if (semanticHit != null) {
                    log.debug("Semantic cache HIT for key: {}", cacheKey);
                    return semanticHit;
                }
            }
        }

        log.debug("Cache MISS for key: {}", cacheKey);

        Object result = joinPoint.proceed();

        // Caffeine cannot store null values, and a null AI response isn't worth
        // caching anyway, so mirror the original behaviour of skipping the write.
        if (result != null) {
            cache.put(cacheKey, result);
            if (queryEmbedding != null) {
                semanticStores.computeIfAbsent(aiCache.cacheName(), n -> new SemanticStore(semanticMaxEntries))
                        .put(queryEmbedding, result);
            }
        }

        return result;
    }

    private boolean semanticActive() {
        return semanticEnabled && embeddingModel != null;
    }

    private float[] safeEmbed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Semantic cache embedding failed, falling back to exact-hash only: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Derives the text used for semantic matching from the cache-key parameters:
     * the concatenation of the {@code String}-valued arguments that contribute to
     * the key. Returns {@code null} when there is no textual content to embed.
     */
    private String extractSemanticText(MethodSignature signature, Object[] args, AiCache aiCache) {
        String[] paramNames = signature.getParameterNames();
        List<Integer> indices = new ArrayList<>();

        if (aiCache.includeAllParams()) {
            int[] excluded = resolveIndices(aiCache.excludeParams(), paramNames, args.length);
            for (int i = 0; i < args.length; i++) {
                boolean skip = false;
                for (int ex : excluded) {
                    if (ex == i) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    indices.add(i);
                }
            }
        } else if (aiCache.includeParams().length > 0) {
            for (int i : resolveIndices(aiCache.includeParams(), paramNames, args.length)) {
                indices.add(i);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i : indices) {
            if (i >= 0 && i < args.length && args[i] instanceof String s && !s.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(s);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
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

    /**
     * Bounded-LRU store of {@code (embedding -> cached value)} entries supporting
     * nearest-neighbour lookup by cosine similarity. Access-ordered so that the
     * least-recently-used entry is evicted once {@code maxEntries} is exceeded.
     */
    static final class SemanticStore {

        private final int maxEntries;
        private final Map<float[], Object> entries;

        SemanticStore(int maxEntries) {
            this.maxEntries = maxEntries;
            this.entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<float[], Object> eldest) {
                    return size() > SemanticStore.this.maxEntries;
                }
            });
        }

        void put(float[] embedding, Object value) {
            entries.put(embedding, value);
        }

        /**
         * Returns the cached value whose embedding is most similar to
         * {@code query}, provided its cosine similarity meets {@code threshold};
         * otherwise {@code null}.
         */
        Object findSimilar(float[] query, double threshold) {
            Object best = null;
            double bestScore = threshold;
            synchronized (entries) {
                for (Map.Entry<float[], Object> entry : entries.entrySet()) {
                    double score = cosineSimilarity(query, entry.getKey());
                    if (score >= bestScore) {
                        bestScore = score;
                        best = entry.getValue();
                    }
                }
            }
            return best;
        }

        int size() {
            return entries.size();
        }
    }

    /**
     * Cosine similarity between two equal-length vectors. Returns {@code 0} for
     * mismatched dimensions or zero-magnitude vectors.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
