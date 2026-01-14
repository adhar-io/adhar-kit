package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Generates text embeddings for annotated fields or parameters.
 *
 * <p>Automatically converts text to embedding vectors for semantic search
 * and similarity comparisons.</p>
 *
 * <p><b>Example - Field Embedding:</b></p>
 * <pre>{@code
 * @Entity
 * public class Product {
 *
 *     @Id
 *     private String id;
 *
 *     private String description;
 *
 *     @AiEmbedding(source = "description")
 *     @Column(columnDefinition = "vector(1536)")
 *     private List<Float> descriptionEmbedding;
 * }
 * }</pre>
 *
 * <p><b>Example - Method Parameter:</b></p>
 * <pre>{@code
 * @Service
 * public class SearchService {
 *
 *     public List<Product> findSimilar(
 *         @AiEmbedding String searchQuery,
 *         int limit
 *     ) {
 *         // searchQuery is automatically converted to embedding
 *         return vectorSearch(searchQuery, limit);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Batch Embedding:</b></p>
 * <pre>{@code
 * @Service
 * public class DocumentService {
 *
 *     @AiEmbedding(batch = true)
 *     public List<List<Float>> embedDocuments(List<String> documents) {
 *         return null;  // Automatically filled with embeddings
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiEmbedding {

    /**
     * Source field name for embedding (for field annotations).
     */
    String source() default "";

    /**
     * Embedding model to use.
     */
    String model() default "text-embedding-ada-002";

    /**
     * Process as batch (for lists).
     */
    boolean batch() default false;

    /**
     * Cache embeddings.
     */
    boolean cache() default true;

    /**
     * Cache TTL in seconds.
     */
    int cacheTtl() default 86400; // 24 hours

    /**
     * Auto-update embedding when source changes.
     */
    boolean autoUpdate() default true;
}

