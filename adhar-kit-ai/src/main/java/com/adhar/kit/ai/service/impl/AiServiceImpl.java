package com.adhar.kit.ai.service.impl;

import com.adhar.kit.ai.component.AiMetricsCollector;
import com.adhar.kit.ai.component.AiRateLimiter;
import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.prompt.PromptTemplateRegistry;
import com.adhar.kit.ai.security.AiSecurityValidator;
import com.adhar.kit.ai.service.AiService;
import com.adhar.kit.ai.tool.AiTool;
import com.adhar.kit.ai.tool.ToolCallResult;
import com.adhar.kit.ai.tool.ToolCallingService;
import com.adhar.kit.commons.exception.ServiceException;
import com.adhar.kit.commons.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Production-ready AI service implementation with enterprise features.
 *
 * <p>Every chat request goes through three guardrail/observability stages in
 * addition to the actual Spring AI {@link ChatModel} call:</p>
 * <ol>
 *   <li><b>Rate limiting</b> - {@link AiRateLimiter#checkRateLimit(String)} is
 *       invoked (keyed by user id, falling back to tenant id, then
 *       {@code "anonymous"}) before any model call is made; it throws a
 *       {@link ServiceException} when the caller has exceeded its quota.</li>
 *   <li><b>Security validation</b> - {@link AiSecurityValidator#validateRequest}
 *       screens the outgoing prompt for unsafe/PII/sensitive content
 *       (throwing {@link ValidationException} when blocked), and
 *       {@link AiSecurityValidator#sanitizeContent(String)} redacts any PII that
 *       slips through in the model's response before it is returned to the caller.</li>
 *   <li><b>Metrics</b> - token usage and processing time reported by Spring AI
 *       are recorded via {@link AiMetricsCollector#recordChatRequest} and, when a
 *       per-model cost entry is configured, {@link AiMetricsCollector#recordCost}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final AiProperties aiProperties;
    private final AiRateLimiter rateLimiter;
    private final AiSecurityValidator securityValidator;
    private final AiMetricsCollector metricsCollector;
    private final PromptTemplateRegistry promptTemplateRegistry;
    private final ToolCallingService toolCallingService;

    @Override
    @Cacheable(value = "ai-chat", key = "#request.message + '-' + #request.model",
               condition = "#request.sessionId == null")
    public AiChatResponse chat(AiChatRequest request) {
        validateRequest(request);
        enforceGuardrails(request);
        return executeChat(request);
    }

    @Override
    @Async("aiTaskExecutor")
    public Mono<AiChatResponse> chatAsync(AiChatRequest request) {
        return Mono.fromCallable(() -> chat(request))
                .doOnSuccess(response -> log.info("Async chat completed - RequestId: {}",
                        response.getRequestId()))
                .doOnError(error -> log.error("Async chat failed: {}", error.getMessage()));
    }

    @Override
    public Flux<AiChatResponse> chatStream(AiChatRequest request) {
        validateRequest(request);
        enforceGuardrails(request);

        String requestId = UUID.randomUUID().toString();
        String model = resolveModel(request);
        String provider = request.getProvider() != null ? request.getProvider() : "openai";
        Prompt prompt = createPrompt(request);
        long startTime = System.currentTimeMillis();

        return chatModel.stream(prompt)
                .map(chunk -> buildStreamChunk(chunk, requestId, model, provider))
                .doOnComplete(() -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    metricsCollector.recordChatRequest(provider, model, Duration.ofMillis(processingTime), 0);
                })
                .onErrorMap(this::mapStreamError);
    }

    @Override
    @Cacheable(value = "ai-embeddings", key = "#text.hashCode()")
    public List<Float> embed(String text) {
        try {
            log.debug("Generating embeddings for text length: {}", text.length());

            var embeddingResponse = embeddingModel.embedForResponse(List.of(text));
            float[] output = embeddingResponse.getResults().get(0).getOutput();

            // Convert float[] to List<Float>
            List<Float> result = new java.util.ArrayList<>(output.length);
            for (float f : output) {
                result.add(f);
            }
            return result;

        } catch (Exception e) {
            log.error("Error generating embeddings: {}", e.getMessage(), e);
            throw new ServiceException("AI_EMBEDDING_ERROR", "Failed to generate embeddings", e);
        }
    }

    @Override
    public List<SimilarityResult> search(String query, int limit) {
        try {
            log.debug("Performing similarity search - Query length: {}, Limit: {}",
                    query.length(), limit);

            var results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(limit).build()
            );

            return results.stream()
                    .map(doc -> new SimilarityResult(
                            doc.getId(),
                            doc.getText(),
                            0.0, // Similarity score would come from vector store
                            doc.getMetadata()
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("Error performing similarity search: {}", e.getMessage(), e);
            throw new ServiceException("AI_SEARCH_ERROR", "Similarity search failed", e);
        }
    }

    @Override
    public AiChatResponse ragChat(AiChatRequest request, String knowledgeBase) {
        // Validation/guardrails run against the original user request and outside
        // the try block below, so a rate-limit/security rejection propagates with
        // its own clear message instead of being masked as "RAG processing failed".
        validateRequest(request);
        enforceGuardrails(request);

        try {
            log.info("Processing RAG chat request - KnowledgeBase: {}", knowledgeBase);

            // 1. Search relevant documents
            List<SimilarityResult> relevantDocs = search(request.getMessage(), 5);

            // 2. Enhance prompt with context
            String enhancedPrompt = buildRagPrompt(request.getMessage(), relevantDocs);

            // 3. Create enhanced request (guardrails/rate-limiting already
            // enforced above against the original user request; the RAG-augmented
            // prompt is derived server-side from trusted document content, so it
            // is routed straight to executeChat() rather than re-entering chat()).
            AiChatRequest ragRequest = AiChatRequest.builder()
                    .message(enhancedPrompt)
                    .model(request.getModel())
                    .provider(request.getProvider())
                    .parameters(request.getParameters())
                    .userId(request.getUserId())
                    .tenantId(request.getTenantId())
                    .sessionId(request.getSessionId())
                    .build();

            // 4. Process enhanced request
            AiChatResponse response = executeChat(ragRequest);

            // Initialize providerSpecific map if null
            if (response.getMetadata().getProviderSpecific() == null) {
                response.getMetadata().setProviderSpecific(new java.util.HashMap<>());
            }

            response.getMetadata().getProviderSpecific().put("knowledgeBase", knowledgeBase);
            response.getMetadata().getProviderSpecific().put("documentsUsed", relevantDocs.size());

            metricsCollector.recordRagRequest(knowledgeBase, Duration.ofMillis(
                    response.getUsage() != null && response.getUsage().getProcessingTimeMs() != null
                            ? response.getUsage().getProcessingTimeMs() : 0L),
                    relevantDocs.size());

            return response;

        } catch (Exception e) {
            log.error("Error processing RAG chat: {}", e.getMessage(), e);
            throw new ServiceException("AI_RAG_ERROR", "RAG processing failed", e);
        }
    }

    @Override
    public void addDocuments(List<DocumentChunk> documents, String knowledgeBase) {
        try {
            log.info("Adding {} documents to knowledge base: {}", documents.size(), knowledgeBase);

            List<org.springframework.ai.document.Document> vectorDocuments = documents.stream()
                    .map(doc -> org.springframework.ai.document.Document.builder()
                            .id(doc.id())
                            .text(doc.content())
                            .metadata("source", doc.source())
                            .metadata("knowledgeBase", knowledgeBase)
                            .metadata(doc.metadata())
                            .build())
                    .toList();

            vectorStore.add(vectorDocuments);

            log.info("Successfully added documents to vector store");

        } catch (Exception e) {
            log.error("Error adding documents to vector store: {}", e.getMessage(), e);
            throw new ServiceException("AI_DOCUMENT_ERROR", "Failed to add documents", e);
        }
    }

    @Override
    public void validateRequest(AiChatRequest request) {
        if (request == null) {
            throw new ValidationException("Request cannot be null");
        }

        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new ValidationException("Message cannot be empty");
        }

        if (request.getMessage().length() > 10000) {
            throw new ValidationException("Message too long. Maximum 10000 characters allowed");
        }

        // Validate model if specified
        if (request.getModel() != null) {
            String[] allowedModels = aiProperties.getSecurity().getAllowedModels();
            boolean isAllowed = false;
            for (String allowedModel : allowedModels) {
                if (allowedModel.equals(request.getModel())) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                throw new ValidationException("Model not allowed: " + request.getModel());
            }
        }
    }

    @Override
    public List<String> getAvailableModels() {
        return List.of(aiProperties.getSecurity().getAllowedModels());
    }

    @Override
    public boolean isHealthy() {
        try {
            // Simple health check
            AiChatRequest healthRequest = AiChatRequest.builder()
                    .message("Hello")
                    .build();

            AiChatResponse response = chat(healthRequest);
            return response != null && response.getContent() != null;

        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void registerPromptTemplate(String name, String template) {
        promptTemplateRegistry.register(name, template);
    }

    @Override
    public String renderPromptTemplate(String name, Map<String, Object> params) {
        return promptTemplateRegistry.render(name, params);
    }

    @Override
    public AiChatResponse chatWithTemplate(String templateName, Map<String, Object> params) {
        String prompt = promptTemplateRegistry.render(templateName, params);
        return chat(AiChatRequest.builder().message(prompt).build());
    }

    @Override
    public ToolCallResult chatWithTools(String message, List<AiTool> tools) {
        int maxIterations = aiProperties.getTools().getMaxIterations();
        return toolCallingService.chat(message, tools, maxIterations);
    }

    /**
     * Runs rate limiting and inbound security validation for a request. Kept
     * separate from {@link #executeChat(AiChatRequest)} so callers that already
     * enforce guardrails against an original user request (e.g. {@link #ragChat})
     * can skip re-checking a derived/enriched request.
     */
    private void enforceGuardrails(AiChatRequest request) {
        String identifier = resolveIdentifier(request);
        rateLimiter.checkRateLimit(identifier);
        securityValidator.validateRequest(request.getMessage(), request.getUserId(), request.getTenantId());
    }

    private String resolveIdentifier(AiChatRequest request) {
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            return request.getUserId();
        }
        if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
            return request.getTenantId();
        }
        return "anonymous";
    }

    /**
     * Core chat execution: builds the prompt, calls the Spring AI {@link ChatModel},
     * records token usage/cost metrics, sanitizes the outbound response for PII, and
     * translates any failure into a {@link ServiceException}. Guardrails
     * (rate limiting / inbound content validation) are the caller's responsibility -
     * see {@link #chat(AiChatRequest)} and {@link #ragChat(AiChatRequest, String)}.
     */
    private AiChatResponse executeChat(AiChatRequest request) {
        try {
            log.info("Processing AI chat request - Model: {}, Provider: {}",
                    request.getModel(), request.getProvider());

            long startTime = System.currentTimeMillis();

            Prompt prompt = createPrompt(request);
            ChatResponse response = chatModel.call(prompt);

            long processingTime = System.currentTimeMillis() - startTime;

            AiChatResponse chatResponse = buildChatResponse(request, response, processingTime);
            chatResponse.setContent(securityValidator.sanitizeContent(chatResponse.getContent()));

            recordMetrics(chatResponse);

            return chatResponse;

        } catch (Exception e) {
            log.error("Error processing AI chat request: {}", e.getMessage(), e);
            metricsCollector.recordError("AI_CHAT_ERROR", request.getProvider(), "chat");
            throw new ServiceException("AI_CHAT_ERROR", "Failed to process chat request", e);
        }
    }

    private void recordMetrics(AiChatResponse response) {
        AiChatResponse.UsageMetrics usage = response.getUsage();
        long tokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0L;
        long processingTime = usage != null && usage.getProcessingTimeMs() != null ? usage.getProcessingTimeMs() : 0L;

        metricsCollector.recordChatRequest(response.getProvider(), response.getModel(),
                Duration.ofMillis(processingTime), tokens);

        if (usage != null && usage.getCost() != null) {
            metricsCollector.recordCost(usage.getCost(), (int) tokens, response.getProvider(), response.getModel());
        }
    }

    private RuntimeException mapStreamError(Throwable e) {
        if (e instanceof ServiceException || e instanceof ValidationException) {
            return (RuntimeException) e;
        }
        metricsCollector.recordError("AI_STREAM_ERROR", "unknown", "chatStream");
        return new ServiceException("AI_STREAM_ERROR", "Streaming failed", e);
    }

    private String resolveModel(AiChatRequest request) {
        return request.getModel() != null ? request.getModel() : aiProperties.getOpenAi().getModel();
    }

    private AiChatResponse buildStreamChunk(ChatResponse response, String requestId, String model, String provider) {
        String content = "";
        if (response.getResult() != null && response.getResult().getOutput() != null
                && response.getResult().getOutput().getText() != null) {
            content = response.getResult().getOutput().getText();
        }

        return AiChatResponse.builder()
                .content(content)
                .model(model)
                .provider(provider)
                .requestId(requestId)
                .metadata(AiChatResponse.ResponseMetadata.builder()
                        .cached(false)
                        .version("1.0")
                        .traceId(UUID.randomUUID().toString())
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private Prompt createPrompt(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();

        if (request.getHistory() != null) {
            for (AiChatRequest.ChatMessage historyMessage : request.getHistory()) {
                messages.add(toSpringMessage(historyMessage));
            }
        }

        messages.add(new UserMessage(request.getMessage()));

        return new Prompt(messages);
    }

    private Message toSpringMessage(AiChatRequest.ChatMessage message) {
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
            case USER, FUNCTION -> new UserMessage(message.getContent());
        };
    }

    private AiChatResponse buildChatResponse(AiChatRequest request, ChatResponse response,
                                           long processingTime) {
        String content = response.getResults().get(0).getOutput().getText();
        String model = resolveModel(request);

        AiChatResponse.UsageMetrics.UsageMetricsBuilder usageBuilder = AiChatResponse.UsageMetrics.builder()
                .processingTimeMs(processingTime);

        ChatResponseMetadata responseMetadata = response.getMetadata();
        Usage usage = responseMetadata != null ? responseMetadata.getUsage() : null;
        if (usage != null) {
            Integer promptTokens = usage.getPromptTokens();
            Integer completionTokens = usage.getCompletionTokens();
            usageBuilder.promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(usage.getTotalTokens())
                    .cost(estimateCost(model, promptTokens, completionTokens));
        }

        return AiChatResponse.builder()
                .content(content)
                .model(model)
                .provider(request.getProvider() != null ? request.getProvider() : "openai")
                .sessionId(request.getSessionId())
                .requestId(UUID.randomUUID().toString())
                .usage(usageBuilder.build())
                .metadata(AiChatResponse.ResponseMetadata.builder()
                        .cached(false)
                        .version("1.0")
                        .traceId(UUID.randomUUID().toString())
                        .build())
                .finishReason(AiChatResponse.FinishReason.STOP)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Estimates the USD cost of a request from the configured per-model cost
     * table ({@code adhar.ai.costs.models.<model-id>}), falling back to
     * {@code adhar.ai.costs.default-cost} for unknown models.
     */
    private double estimateCost(String model, Integer promptTokens, Integer completionTokens) {
        AiProperties.Costs costs = aiProperties.getCosts();
        AiProperties.Costs.ModelCost modelCost = costs.getModels().getOrDefault(model, costs.getDefaultCost());

        double promptCost = (promptTokens != null ? promptTokens : 0) / 1000.0 * modelCost.getPromptCostPer1k();
        double completionCost = (completionTokens != null ? completionTokens : 0) / 1000.0
                * modelCost.getCompletionCostPer1k();

        return promptCost + completionCost;
    }

    private String buildRagPrompt(String originalQuery, List<SimilarityResult> documents) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Based on the following context documents, please answer the question.\n\n");

        promptBuilder.append("Context Documents:\n");
        for (int i = 0; i < documents.size(); i++) {
            promptBuilder.append("Document ").append(i + 1).append(": ");
            promptBuilder.append(documents.get(i).content()).append("\n\n");
        }

        promptBuilder.append("Question: ").append(originalQuery).append("\n\n");
        promptBuilder.append("Please provide a comprehensive answer based on the context provided above.");

        return promptBuilder.toString();
    }
}
