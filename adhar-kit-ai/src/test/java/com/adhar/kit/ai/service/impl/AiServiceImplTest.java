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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive test suite for AI Service implementation.
 * Tests all enterprise features including caching, validation, guardrails,
 * token/cost tracking, streaming and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceImplTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiRateLimiter rateLimiter;

    @Mock
    private AiSecurityValidator securityValidator;

    @Mock
    private AiMetricsCollector metricsCollector;

    @Mock
    private ToolCallingService toolCallingService;

    private PromptTemplateRegistry promptTemplateRegistry;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        // Setup default properties with lenient to avoid UnnecessaryStubbing errors
        lenient().when(aiProperties.getSecurity()).thenReturn(createSecurityProperties());
        lenient().when(aiProperties.getOpenAi()).thenReturn(createOpenAiProperties());
        lenient().when(aiProperties.getCosts()).thenReturn(new AiProperties.Costs());

        // Guardrails default to "allow" and content passthrough so existing
        // behavioural assertions (content/model/etc.) aren't affected unless a
        // test explicitly configures a rejection.
        lenient().when(securityValidator.sanitizeContent(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Skip classpath scanning for deterministic, isolated registry state.
        promptTemplateRegistry = new PromptTemplateRegistry(null);

        aiService = new AiServiceImpl(chatModel, embeddingModel, vectorStore, aiProperties,
                rateLimiter, securityValidator, metricsCollector, promptTemplateRegistry, toolCallingService);
    }

    @Test
    void testChatSuccess() {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("Hello, how are you?")
                .model("gpt-3.5-turbo")
                .build();

        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("I'm doing well, thank you!")));

        // When
        AiChatResponse response = aiService.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("I'm doing well, thank you!");
        assertThat(response.getModel()).isEqualTo("gpt-3.5-turbo");
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void testChatWithValidationError() {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("") // Empty message should fail validation
                .build();

        // When & Then
        assertThatThrownBy(() -> aiService.chat(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class)
                .hasMessageContaining("Message cannot be empty");
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void testEmbeddingGeneration() {
        // Given
        String text = "Generate embeddings for this text";
        var mockEmbeddingResponse = mock(org.springframework.ai.embedding.EmbeddingResponse.class);
        var mockResult = mock(org.springframework.ai.embedding.Embedding.class);

        when(embeddingModel.embedForResponse(any())).thenReturn(mockEmbeddingResponse);
        when(mockEmbeddingResponse.getResults()).thenReturn(List.of(mockResult));
        when(mockResult.getOutput()).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        // When
        List<Float> embeddings = aiService.embed(text);

        // Then
        assertThat(embeddings).isNotNull();
        assertThat(embeddings).hasSize(3);
        assertThat(embeddings).containsExactly(0.1f, 0.2f, 0.3f);
        verify(embeddingModel).embedForResponse(any());
    }

    @Test
    void testSimilaritySearch() {
        // Given
        String query = "search query";
        int limit = 5;
        var mockDocument = org.springframework.ai.document.Document.builder()
                .id("doc1")
                .text("Sample document content")
                .build();

        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(mockDocument));

        // When
        List<AiService.SimilarityResult> results = aiService.search(query, limit);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo("doc1");
        assertThat(results.getFirst().content()).isEqualTo("Sample document content");
        verify(vectorStore).similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class));
    }

    @Test
    void testRagChat() {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("What is machine learning?")
                .build();
        String knowledgeBase = "ml-docs";

        // Mock similarity search
        var mockDocument = org.springframework.ai.document.Document.builder()
                .id("ml-doc-1")
                .text("Machine learning is a subset of AI...")
                .build();
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(mockDocument));

        // Mock chat response
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("Based on the context, machine learning is...")));

        // When
        AiChatResponse response = aiService.ragChat(request, knowledgeBase);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).contains("Based on the context");
        assertThat(response.getMetadata().getProviderSpecific()).containsKey("knowledgeBase");
        verify(vectorStore).similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class));
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        verify(metricsCollector).recordRagRequest(eq("ml-docs"), any(Duration.class), eq(1));
    }

    @Test
    void testAddDocuments() {
        // Given
        List<AiService.DocumentChunk> documents = List.of(
                new AiService.DocumentChunk("doc1", "Content 1", "source1", Map.of()),
                new AiService.DocumentChunk("doc2", "Content 2", "source2", Map.of())
        );
        String knowledgeBase = "test-kb";

        // When
        assertThatNoException().isThrownBy(() ->
            aiService.addDocuments(documents, knowledgeBase));

        // Then
        verify(vectorStore).add(any());
    }

    @Test
    void testValidateRequestWithInvalidModel() {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("Test message")
                .model("invalid-model")
                .build();

        // When & Then
        assertThatThrownBy(() -> aiService.validateRequest(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class)
                .hasMessageContaining("Model not allowed");
    }

    @Test
    void testGetAvailableModels() {
        // When
        List<String> models = aiService.getAvailableModels();

        // Then
        assertThat(models).isNotNull();
        assertThat(models).contains("gpt-3.5-turbo", "gpt-4", "llama2");
    }

    @Test
    void testHealthCheck() {
        // Given
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("Health check response")));

        // When
        boolean isHealthy = aiService.isHealthy();

        // Then
        assertThat(isHealthy).isTrue();
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void testChatAsyncSuccess() {
        AiChatRequest request = AiChatRequest.builder()
                .message("async hello")
                .model("gpt-3.5-turbo")
                .build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("async response")));

        AiChatResponse response = aiService.chatAsync(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("async response");
    }

    @Test
    void testChatStreamEmitsMultipleChunks() {
        AiChatRequest request = AiChatRequest.builder()
                .message("stream hello")
                .model("gpt-3.5-turbo")
                .build();

        ChatResponse chunk1 = mock(ChatResponse.class);
        ChatResponse chunk2 = mock(ChatResponse.class);
        ChatResponse chunk3 = mock(ChatResponse.class);
        when(chunk1.getResult()).thenReturn(createMockResult("Hello"));
        when(chunk2.getResult()).thenReturn(createMockResult(" world"));
        when(chunk3.getResult()).thenReturn(createMockResult("!"));

        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(chunk1, chunk2, chunk3));

        List<AiChatResponse> responses = aiService.chatStream(request).collectList().block();

        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(3);
        assertThat(responses.stream().map(AiChatResponse::getContent).toList())
                .containsExactly("Hello", " world", "!");
        assertThat(responses).allSatisfy(r -> assertThat(r.getRequestId()).isNotBlank());
        verify(rateLimiter).checkRateLimit(anyString());
    }

    @Test
    void testChatStreamValidationError() {
        AiChatRequest request = AiChatRequest.builder().message(" ").build();

        assertThatThrownBy(() -> aiService.chatStream(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class);
    }

    @Test
    void testChatStreamWrapsUnexpectedErrors() {
        AiChatRequest request = AiChatRequest.builder().message("boom please").build();
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("stream provider down")));

        assertThatThrownBy(() -> aiService.chatStream(request).collectList().block())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Streaming failed");
    }

    @Test
    void testChatThrowsServiceExceptionOnModelFailure() {
        AiChatRequest request = AiChatRequest.builder()
                .message("fail me")
                .model("gpt-3.5-turbo")
                .build();
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("provider unavailable"));

        assertThatThrownBy(() -> aiService.chat(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ServiceException.class)
                .hasMessageContaining("Failed to process chat request");
        verify(metricsCollector).recordError(anyString(), any(), anyString());
    }

    @Test
    void testEmbedThrowsServiceExceptionOnModelFailure() {
        when(embeddingModel.embedForResponse(any())).thenThrow(new RuntimeException("embed failed"));

        assertThatThrownBy(() -> aiService.embed("text"))
                .isInstanceOf(com.adhar.kit.commons.exception.ServiceException.class)
                .hasMessageContaining("Failed to generate embeddings");
    }

    @Test
    void testSearchThrowsServiceExceptionOnVectorStoreFailure() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(new RuntimeException("search failed"));

        assertThatThrownBy(() -> aiService.search("query", 3))
                .isInstanceOf(com.adhar.kit.commons.exception.ServiceException.class)
                .hasMessageContaining("Similarity search failed");
    }

    @Test
    void testRagChatThrowsServiceExceptionWhenSearchFails() {
        AiChatRequest request = AiChatRequest.builder().message("question").build();
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(new RuntimeException("vector failure"));

        assertThatThrownBy(() -> aiService.ragChat(request, "kb"))
                .isInstanceOf(com.adhar.kit.commons.exception.ServiceException.class)
                .hasMessageContaining("RAG processing failed");
    }

    @Test
    void testAddDocumentsThrowsServiceExceptionOnVectorStoreFailure() {
        List<AiService.DocumentChunk> documents = List.of(
                new AiService.DocumentChunk("doc1", "Content", "source", Map.of())
        );
        doThrow(new RuntimeException("vector add failed")).when(vectorStore).add(any());

        assertThatThrownBy(() -> aiService.addDocuments(documents, "kb"))
                .isInstanceOf(com.adhar.kit.commons.exception.ServiceException.class)
                .hasMessageContaining("Failed to add documents");
    }

    @Test
    void testValidateRequestRejectsNullRequest() {
        assertThatThrownBy(() -> aiService.validateRequest(null))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class)
                .hasMessageContaining("Request cannot be null");
    }

    @Test
    void testValidateRequestRejectsLongMessage() {
        String longMessage = "a".repeat(10001);
        AiChatRequest request = AiChatRequest.builder().message(longMessage).build();

        assertThatThrownBy(() -> aiService.validateRequest(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class)
                .hasMessageContaining("Message too long");
    }

    @Test
    void testHealthCheckReturnsFalseOnFailure() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("health failure"));

        assertThat(aiService.isHealthy()).isFalse();
    }

    // ==================== Guardrails ====================

    @Test
    void testChatEnforcesRateLimitBeforeCallingModel() {
        AiChatRequest request = AiChatRequest.builder().message("hi").userId("user-1").build();
        doThrow(new ServiceException("RATE_LIMIT_EXCEEDED", "Rate limit exceeded"))
                .when(rateLimiter).checkRateLimit("user-1");

        assertThatThrownBy(() -> aiService.chat(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Rate limit exceeded");

        verifyNoInteractions(chatModel);
    }

    @Test
    void testChatUsesUserIdAsRateLimitIdentifier() {
        AiChatRequest request = AiChatRequest.builder().message("hi").userId("user-42").tenantId("tenant-1").build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("ok")));

        aiService.chat(request);

        verify(rateLimiter).checkRateLimit("user-42");
    }

    @Test
    void testChatFallsBackToTenantIdWhenNoUserId() {
        AiChatRequest request = AiChatRequest.builder().message("hi").tenantId("tenant-9").build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("ok")));

        aiService.chat(request);

        verify(rateLimiter).checkRateLimit("tenant-9");
    }

    @Test
    void testChatFallsBackToAnonymousIdentifier() {
        AiChatRequest request = AiChatRequest.builder().message("hi").build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("ok")));

        aiService.chat(request);

        verify(rateLimiter).checkRateLimit("anonymous");
    }

    @Test
    void testChatBlockedBySecurityValidatorPropagatesValidationException() {
        AiChatRequest request = AiChatRequest.builder().message("my password is hunter2").build();
        doThrow(new com.adhar.kit.commons.exception.ValidationException("SENSITIVE_CONTENT", "blocked"))
                .when(securityValidator).validateRequest(anyString(), any(), any());

        assertThatThrownBy(() -> aiService.chat(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class);

        verifyNoInteractions(chatModel);
    }

    @Test
    void testChatSanitizesResponseContent() {
        AiChatRequest request = AiChatRequest.builder().message("hi").build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("contact me at a@b.com")));
        when(securityValidator.sanitizeContent("contact me at a@b.com")).thenReturn("contact me at [EMAIL_REDACTED]");

        AiChatResponse response = aiService.chat(request);

        assertThat(response.getContent()).isEqualTo("contact me at [EMAIL_REDACTED]");
        verify(securityValidator).sanitizeContent("contact me at a@b.com");
    }

    // ==================== Token / cost tracking ====================

    @Test
    void testChatRecordsTokenUsageAndCost() {
        AiChatRequest request = AiChatRequest.builder().message("hi").model("gpt-4").build();

        AiProperties.Costs costs = new AiProperties.Costs();
        costs.getModels().put("gpt-4", new AiProperties.Costs.ModelCost(0.03, 0.06));
        when(aiProperties.getCosts()).thenReturn(costs);

        ChatResponse mockResponse = mock(ChatResponse.class);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("answer")));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(100, 50))
                .build();
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);

        AiChatResponse response = aiService.chat(request);

        assertThat(response.getUsage().getPromptTokens()).isEqualTo(100);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(50);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(150);
        // (100/1000 * 0.03) + (50/1000 * 0.06) = 0.003 + 0.003 = 0.006
        assertThat(response.getUsage().getCost()).isEqualTo(0.006, within(0.0001));

        ArgumentCaptor<Long> tokensCaptor = ArgumentCaptor.forClass(Long.class);
        verify(metricsCollector).recordChatRequest(eq("openai"), eq("gpt-4"), any(Duration.class), tokensCaptor.capture());
        assertThat(tokensCaptor.getValue()).isEqualTo(150L);
        verify(metricsCollector).recordCost(eq(0.006), eq(150), eq("openai"), eq("gpt-4"));
    }

    @Test
    void testChatWithoutUsageMetadataSkipsCostRecording() {
        AiChatRequest request = AiChatRequest.builder().message("hi").model("gpt-3.5-turbo").build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("ok")));
        // getMetadata() not stubbed -> null, matching a bare-bones mock ChatResponse.

        AiChatResponse response = aiService.chat(request);

        assertThat(response.getUsage().getPromptTokens()).isNull();
        assertThat(response.getUsage().getCost()).isNull();
        verify(metricsCollector, never()).recordCost(anyDouble(), anyInt(), anyString(), anyString());
        verify(metricsCollector).recordChatRequest(anyString(), anyString(), any(Duration.class), eq(0L));
    }

    // ==================== Prompt templates ====================

    @Test
    void testRegisterAndRenderPromptTemplate() {
        aiService.registerPromptTemplate("greeting", "Hello {name}, welcome to {place}");

        String rendered = aiService.renderPromptTemplate("greeting",
                Map.of("name", "Alice", "place", "Wonderland"));

        assertThat(rendered).isEqualTo("Hello Alice, welcome to Wonderland");
    }

    @Test
    void testChatWithTemplateRendersThenChats() {
        aiService.registerPromptTemplate("ask", "Explain {topic} simply");

        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("Simple explanation")));

        AiChatResponse response = aiService.chatWithTemplate("ask", Map.of("topic", "gravity"));

        assertThat(response.getContent()).isEqualTo("Simple explanation");
        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> captor =
                ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getContents()).contains("Explain gravity simply");
    }

    // ==================== Tool calling ====================

    @Test
    void testChatWithToolsDelegatesToToolCallingService() {
        AiProperties.Tools tools = new AiProperties.Tools();
        tools.setMaxIterations(7);
        when(aiProperties.getTools()).thenReturn(tools);

        AiTool tool = AiTool.builder().name("noop").description("d").handler(a -> "r").build();
        ToolCallResult expected = new ToolCallResult("final", List.of(), 1, false);
        when(toolCallingService.chat("do it", List.of(tool), 7)).thenReturn(expected);

        ToolCallResult result = aiService.chatWithTools("do it", List.of(tool));

        assertThat(result).isSameAs(expected);
        verify(toolCallingService).chat("do it", List.of(tool), 7);
    }

    private AiProperties.Security createSecurityProperties() {
        AiProperties.Security security = new AiProperties.Security();
        security.setEnabled(true);
        security.setAllowedModels(new String[]{"gpt-3.5-turbo", "gpt-4", "llama2"});
        return security;
    }

    private AiProperties.OpenAi createOpenAiProperties() {
        AiProperties.OpenAi openAi = new AiProperties.OpenAi();
        openAi.setModel("gpt-3.5-turbo");
        return openAi;
    }

    private org.springframework.ai.chat.model.Generation createMockResult(String content) {
        // Create real AssistantMessage instead of mocking (avoids final method issues)
        var assistantMessage = new org.springframework.ai.chat.messages.AssistantMessage(content);

        // Create Generation with the message
        var metadata = org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder()
                .finishReason("stop")
                .build();

        return new org.springframework.ai.chat.model.Generation(assistantMessage, metadata);
    }
}
