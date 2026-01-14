package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Enables Retrieval Augmented Generation (RAG) for a service or method.
 *
 * <p>Automatically retrieves relevant documents from a vector store
 * before generating AI responses, providing context-aware answers.</p>
 *
 * <p><b>Example - Service-Level RAG:</b></p>
 * <pre>{@code
 * @Service
 * @AiRag(
 *     vectorStore = "company-docs",
 *     topK = 5
 * )
 * public class DocumentQAService {
 *
 *     @AiChat(prompt = "Based on company documents, {question}")
 *     public String answerQuestion(String question) {
 *         // RAG automatically retrieves relevant docs
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Method-Level RAG:</b></p>
 * <pre>{@code
 * @Service
 * public class SupportService {
 *
 *     @AiRag(
 *         vectorStore = "knowledge-base",
 *         topK = 3,
 *         includeMetadata = true
 *     )
 *     @AiChat(
 *         systemPrompt = "You are a technical support agent",
 *         prompt = "Answer this support question: {query}"
 *     )
 *     public String handleSupportQuery(String query) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - With Filters:</b></p>
 * <pre>{@code
 * @Service
 * public class PolicyService {
 *
 *     @AiRag(
 *         vectorStore = "policies",
 *         topK = 5,
 *         filter = "department == '{dept}'"
 *     )
 *     public String getPolicyInfo(String question, String dept) {
 *         // Only searches policies for specific department
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiRag {

    /**
     * Vector store name to query.
     */
    String vectorStore();

    /**
     * Number of documents to retrieve.
     */
    int topK() default 3;

    /**
     * Minimum similarity score (0.0-1.0).
     */
    double minScore() default 0.7;

    /**
     * Include document metadata in context.
     */
    boolean includeMetadata() default false;

    /**
     * Include source citations in response.
     */
    boolean includeSources() default true;

    /**
     * Metadata filter expression.
     * Use {paramName} for parameter substitution.
     */
    String filter() default "";

    /**
     * Rerank results using AI.
     */
    boolean rerank() default false;

    /**
     * Chunk size for document splitting.
     */
    int chunkSize() default 1000;

    /**
     * Overlap between chunks.
     */
    int chunkOverlap() default 200;
}

