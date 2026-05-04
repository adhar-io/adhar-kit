package com.adhar.kit.ai.service.impl;

import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive test suite for AI Service implementation.
 * Tests all enterprise features including caching, validation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private AiProperties aiProperties;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        // Setup default properties with lenient to avoid UnnecessaryStubbing errors
        lenient().when(aiProperties.getSecurity()).thenReturn(createSecurityProperties());
        lenient().when(aiProperties.getOpenAi()).thenReturn(createOpenAiProperties());

        aiService = new AiServiceImpl(chatModel, embeddingModel, vectorStore, aiProperties);
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
    void testChatStreamSuccess() {
        AiChatRequest request = AiChatRequest.builder()
                .message("stream hello")
                .model("gpt-3.5-turbo")
                .build();
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("stream response")));

        List<AiChatResponse> responses = aiService.chatStream(request).collectList().block();

        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getContent()).isEqualTo("stream response");
        assertThat(responses.getFirst().getRequestId()).isNotBlank();
    }

    @Test
    void testChatStreamValidationError() {
        AiChatRequest request = AiChatRequest.builder().message(" ").build();

        assertThatThrownBy(() -> aiService.chatStream(request))
                .isInstanceOf(com.adhar.kit.commons.exception.ValidationException.class);
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
