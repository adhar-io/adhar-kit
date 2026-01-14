package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Caches AI responses to reduce costs and improve performance.
 *
 * <p>Automatically caches AI responses based on input parameters,
 * avoiding redundant API calls for identical requests.</p>
 *
 * <p><b>Example - Cache Translations:</b></p>
 * <pre>{@code
 * @Service
 * public class TranslationService {
 *
 *     @AiCache(ttl = 86400)  // 24 hours
 *     @AiChat(prompt = "Translate to {lang}: {text}")
 *     public String translate(String text, String lang) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Cache Embeddings:</b></p>
 * <pre>{@code
 * @Service
 * public class EmbeddingService {
 *
 *     @AiCache(
 *         ttl = 604800,  // 7 days
 *         maxEntries = 10000,
 *         cacheProvider = "redis"
 *     )
 *     public List<Float> generateEmbedding(String text) {
 *         return ai.embed(text);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Conditional Caching:</b></p>
 * <pre>{@code
 * @Service
 * public class ContentService {
 *
 *     @AiCache(
 *         ttl = 3600,
 *         condition = "#result.length() > 100",  // Only cache long responses
 *         keyGenerator = "customKeyGenerator"
 *     )
 *     @AiChat(prompt = "Generate content about: {topic}")
 *     public String generateContent(String topic) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiCache {

    /**
     * Cache TTL in seconds.
     */
    int ttl() default 3600;

    /**
     * Maximum cache entries.
     */
    int maxEntries() default 1000;

    /**
     * Cache provider (caffeine, redis, hazelcast).
     */
    String cacheProvider() default "caffeine";

    /**
     * Cache name.
     */
    String cacheName() default "ai-cache";

    /**
     * SpEL expression for conditional caching.
     */
    String condition() default "";

    /**
     * Custom key generator bean name.
     */
    String keyGenerator() default "";

    /**
     * Include all parameters in cache key.
     */
    boolean includeAllParams() default true;

    /**
     * Specific parameters to include in key.
     */
    String[] includeParams() default {};

    /**
     * Parameters to exclude from key.
     */
    String[] excludeParams() default {};
}

