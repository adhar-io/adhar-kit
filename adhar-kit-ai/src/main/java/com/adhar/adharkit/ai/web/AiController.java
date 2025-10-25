package com.adhar.adharkit.ai.web;

import com.adhar.adharkit.ai.model.AiChatRequest;
import com.adhar.adharkit.ai.model.AiChatResponse;
import com.adhar.adharkit.ai.service.AiService;
import com.adhar.kit.commons.model.ApiResponse;
import com.adhar.kit.commons.web.BaseController;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for AI operations.
 * Provides enterprise-ready endpoints with proper validation, monitoring, and error handling.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController extends BaseController {

    private final AiService aiService;

    /**
     * Synchronous chat completion endpoint.
     */
    @PostMapping("/chat")
    @Timed(value = "ai.chat.duration", description = "AI chat request duration")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "chat");

        try {
            AiChatResponse response = aiService.chat(request);
            return success(response, "Chat completed successfully");
        } catch (Exception e) {
            log.error("Chat request failed: {}", e.getMessage(), e);
            return internalServerError("Failed to process chat request");
        }
    }

    /**
     * Asynchronous chat completion endpoint.
     */
    @PostMapping("/chat/async")
    @Timed(value = "ai.chat.async.duration", description = "AI async chat request duration")
    public Mono<ResponseEntity<ApiResponse<AiChatResponse>>> chatAsync(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "chatAsync");

        return aiService.chatAsync(request)
                .map(response -> success(response, "Async chat completed successfully").getBody())
                .map(ResponseEntity::ok)
                .doOnError(error -> log.error("Async chat failed: {}", error.getMessage()))
                .onErrorReturn(internalServerError("Failed to process async chat request"));
    }

    /**
     * Streaming chat completion endpoint.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Timed(value = "ai.chat.stream.duration", description = "AI streaming chat request duration")
    public Flux<AiChatResponse> chatStream(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "chatStream");

        return aiService.chatStream(request)
                .doOnNext(response -> log.debug("Streaming chunk sent: {}", response.getRequestId()))
                .doOnComplete(() -> log.info("Streaming completed"))
                .doOnError(error -> log.error("Streaming failed: {}", error.getMessage()));
    }

    /**
     * Generate embeddings for text.
     */
    @PostMapping("/embed")
    @Timed(value = "ai.embed.duration", description = "AI embedding generation duration")
    public ResponseEntity<ApiResponse<List<Float>>> embed(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "embed");

        String text = request.get("text");
        if (text == null || text.trim().isEmpty()) {
            return badRequest("Text parameter is required");
        }

        try {
            List<Float> embeddings = aiService.embed(text);
            return success(embeddings, "Embeddings generated successfully");
        } catch (Exception e) {
            log.error("Embedding generation failed: {}", e.getMessage(), e);
            return internalServerError("Failed to generate embeddings");
        }
    }

    /**
     * Semantic search using vector store.
     */
    @PostMapping("/search")
    @Timed(value = "ai.search.duration", description = "AI similarity search duration")
    public ResponseEntity<ApiResponse<List<AiService.SimilarityResult>>> search(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "search");

        String query = (String) request.get("query");
        Integer limit = (Integer) request.getOrDefault("limit", 10);

        if (query == null || query.trim().isEmpty()) {
            return badRequest("Query parameter is required");
        }

        try {
            List<AiService.SimilarityResult> results = aiService.search(query, limit);
            return success(results, "Search completed successfully");
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage(), e);
            return internalServerError("Failed to perform search");
        }
    }

    /**
     * Retrieval-Augmented Generation (RAG) chat.
     */
    @PostMapping("/rag/chat")
    @Timed(value = "ai.rag.chat.duration", description = "AI RAG chat request duration")
    public ResponseEntity<ApiResponse<AiChatResponse>> ragChat(
            @Valid @RequestBody AiChatRequest request,
            @RequestParam String knowledgeBase,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "ragChat");

        try {
            AiChatResponse response = aiService.ragChat(request, knowledgeBase);
            return success(response, "RAG chat completed successfully");
        } catch (Exception e) {
            log.error("RAG chat failed: {}", e.getMessage(), e);
            return internalServerError("Failed to process RAG chat request");
        }
    }

    /**
     * Add documents to knowledge base.
     */
    @PostMapping("/rag/documents")
    @Timed(value = "ai.rag.documents.duration", description = "AI document ingestion duration")
    public ResponseEntity<ApiResponse<Void>> addDocuments(
            @RequestBody List<AiService.DocumentChunk> documents,
            @RequestParam String knowledgeBase,
            HttpServletRequest httpRequest) {

        logRequest(httpRequest, "addDocuments");

        if (documents == null || documents.isEmpty()) {
            return badRequest("Documents cannot be empty");
        }

        try {
            aiService.addDocuments(documents, knowledgeBase);
            return success("Documents added successfully to knowledge base: " + knowledgeBase);
        } catch (Exception e) {
            log.error("Document ingestion failed: {}", e.getMessage(), e);
            return internalServerError("Failed to add documents to knowledge base");
        }
    }

    /**
     * Get available AI models.
     */
    @GetMapping("/models")
    public ResponseEntity<ApiResponse<List<String>>> getModels(HttpServletRequest httpRequest) {
        logRequest(httpRequest, "getModels");

        try {
            List<String> models = aiService.getAvailableModels();
            return success(models, "Available models retrieved successfully");
        } catch (Exception e) {
            log.error("Failed to retrieve models: {}", e.getMessage(), e);
            return internalServerError("Failed to retrieve available models");
        }
    }

    /**
     * Health check endpoint for AI services.
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health(HttpServletRequest httpRequest) {
        logRequest(httpRequest, "health");

        try {
            boolean isHealthy = aiService.isHealthy();
            Map<String, Object> healthStatus = Map.of(
                "status", isHealthy ? "UP" : "DOWN",
                "timestamp", java.time.LocalDateTime.now(),
                "services", Map.of("ai", isHealthy ? "operational" : "degraded")
            );

            return isHealthy ?
                success(healthStatus, "AI services are healthy") :
                error(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                      "AI_UNHEALTHY", "AI services are not responding");

        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            return internalServerError("Health check failed");
        }
    }
}
