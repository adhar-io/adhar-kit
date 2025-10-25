package com.adhar.adharkit.ai.service;

import com.adhar.adharkit.ai.model.AiChatRequest;
import com.adhar.adharkit.ai.model.AiChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Core AI service interface providing chat, embedding, and RAG capabilities.
 * Implements enterprise patterns including caching, rate limiting, and monitoring.
 */
public interface AiService {

    /**
     * Synchronous chat completion.
     */
    AiChatResponse chat(AiChatRequest request);

    /**
     * Asynchronous chat completion.
     */
    Mono<AiChatResponse> chatAsync(AiChatRequest request);

    /**
     * Streaming chat completion.
     */
    Flux<AiChatResponse> chatStream(AiChatRequest request);

    /**
     * Generate embeddings for text.
     */
    List<Float> embed(String text);

    /**
     * Similarity search using vector store.
     */
    List<SimilarityResult> search(String query, int limit);

    /**
     * Retrieval-Augmented Generation (RAG).
     */
    AiChatResponse ragChat(AiChatRequest request, String knowledgeBase);

    /**
     * Add documents to vector store for RAG.
     */
    void addDocuments(List<DocumentChunk> documents, String knowledgeBase);

    /**
     * Validate AI request.
     */
    void validateRequest(AiChatRequest request);

    /**
     * Get available models.
     */
    List<String> getAvailableModels();

    /**
     * Health check for AI services.
     */
    boolean isHealthy();

    /**
     * Document chunk for RAG operations.
     */
    record DocumentChunk(
        String id,
        String content,
        String source,
        java.util.Map<String, Object> metadata
    ) {}

    /**
     * Similarity search result.
     */
    record SimilarityResult(
        String id,
        String content,
        double similarity,
        java.util.Map<String, Object> metadata
    ) {}
}
