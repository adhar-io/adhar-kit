package com.adhar.kit.ai.service;

import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.tool.AiTool;
import com.adhar.kit.ai.tool.ToolCallResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

    // ==================== Prompt templates ====================

    /**
     * Registers a named prompt template with the shared template registry.
     *
     * @param name     the template name
     * @param template the template body (supports {@code {param}} placeholders)
     */
    void registerPromptTemplate(String name, String template);

    /**
     * Renders a registered prompt template, substituting {@code {param}} placeholders.
     *
     * @param name   the template name
     * @param params the substitution values
     * @return the rendered prompt
     */
    String renderPromptTemplate(String name, Map<String, Object> params);

    /**
     * Renders a registered prompt template and runs a chat completion with the result.
     *
     * @param templateName the template name
     * @param params       the substitution values
     * @return the chat response
     */
    AiChatResponse chatWithTemplate(String templateName, Map<String, Object> params);

    // ==================== Tool / function calling ====================

    /**
     * Runs a tool-calling loop: the model may call the supplied tools, whose results
     * are fed back until it produces a final answer (bounded by a configured cap).
     *
     * @param message the user prompt
     * @param tools   the executable tools to expose to the model
     * @return the loop result including the final answer and executed tool calls
     */
    ToolCallResult chatWithTools(String message, List<AiTool> tools);

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
