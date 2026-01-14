package com.adhar.kit.ai.controller;

import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.service.AiService;
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
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<AiChatResponse>builder()
                            .success(false)
                            .message("Failed to process chat request")
                            .build());
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
                .onErrorReturn(ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.<AiChatResponse>builder()
                                .success(false)
                                .message("Failed to process async chat request")
                                .build()));
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
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<Float>>builder()
                            .success(false)
                            .message("Text parameter is required")
                            .build());
        }

        try {
            List<Float> embeddings = aiService.embed(text);
            return success(embeddings, "Embeddings generated successfully");
        } catch (Exception e) {
            log.error("Embedding generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<List<Float>>builder()
                            .success(false)
                            .message("Failed to generate embeddings")
                            .build());
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
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<AiService.SimilarityResult>>builder()
                            .success(false)
                            .message("Query parameter is required")
                            .build());
        }

        try {
            List<AiService.SimilarityResult> results = aiService.search(query, limit);
            return success(results, "Search completed successfully");
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<List<AiService.SimilarityResult>>builder()
                            .success(false)
                            .message("Failed to perform search")
                            .build());
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
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<AiChatResponse>builder()
                            .success(false)
                            .message("Failed to process RAG chat request")
                            .build());
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
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Documents cannot be empty")
                            .build());
        }

        try {
            aiService.addDocuments(documents, knowledgeBase);
            return successMessage("Documents added successfully to knowledge base: " + knowledgeBase);
        } catch (Exception e) {
            log.error("Document ingestion failed: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Failed to add documents to knowledge base")
                            .build());
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
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<List<String>>builder()
                            .success(false)
                            .message("Failed to retrieve available models")
                            .build());
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
                ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.<Map<String, Object>>builder()
                                .success(false)
                                .message("AI services are not responding")
                                .build());

        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .success(false)
                            .message("Health check failed")
                            .build());
        }
    }
}
