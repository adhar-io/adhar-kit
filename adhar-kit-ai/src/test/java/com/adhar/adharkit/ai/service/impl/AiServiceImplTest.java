package com.adhar.adharkit.ai.service.impl;

import com.adhar.adharkit.ai.config.AiProperties;
import com.adhar.adharkit.ai.model.AiChatRequest;
import com.adhar.adharkit.ai.model.AiChatResponse;
import com.adhar.adharkit.ai.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for AI Service implementation.
 * Tests all enterprise features including caching, validation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private AiProperties aiProperties;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        // Setup default properties
        when(aiProperties.getSecurity()).thenReturn(createSecurityProperties());
        when(aiProperties.getOpenAi()).thenReturn(createOpenAiProperties());

        aiService = new AiServiceImpl(chatClient, embeddingClient, vectorStore, aiProperties);
    }

    @Test
    void testChatSuccess() {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("Hello, how are you?")
                .model("gpt-3.5-turbo")
                .build();

        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatClient.call(any())).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("I'm doing well, thank you!")));

        // When
        AiChatResponse response = aiService.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("I'm doing well, thank you!");
        assertThat(response.getModel()).isEqualTo("gpt-3.5-turbo");
        verify(chatClient).call(any());
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

        when(embeddingClient.embedForResponse(any())).thenReturn(mockEmbeddingResponse);
        when(mockEmbeddingResponse.getResults()).thenReturn(List.of(mockResult));
        when(mockResult.getOutput()).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        // When
        List<Float> embeddings = aiService.embed(text);

        // Then
        assertThat(embeddings).isNotNull();
        assertThat(embeddings).hasSize(3);
        assertThat(embeddings).containsExactly(0.1f, 0.2f, 0.3f);
        verify(embeddingClient).embedForResponse(any());
    }

    @Test
    void testSimilaritySearch() {
        // Given
        String query = "search query";
        int limit = 5;
        var mockDocument = org.springframework.ai.document.Document.builder()
                .withId("doc1")
                .withContent("Sample document content")
                .build();

        when(vectorStore.similaritySearch(query, limit)).thenReturn(List.of(mockDocument));

        // When
        List<AiService.SimilarityResult> results = aiService.search(query, limit);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("doc1");
        assertThat(results.get(0).content()).isEqualTo("Sample document content");
        verify(vectorStore).similaritySearch(query, limit);
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
                .withId("ml-doc-1")
                .withContent("Machine learning is a subset of AI...")
                .build();
        when(vectorStore.similaritySearch(any(), anyInt())).thenReturn(List.of(mockDocument));

        // Mock chat response
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(chatClient.call(any())).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("Based on the context, machine learning is...")));

        // When
        AiChatResponse response = aiService.ragChat(request, knowledgeBase);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).contains("Based on the context");
        assertThat(response.getMetadata().getProviderSpecific()).containsKey("knowledgeBase");
        verify(vectorStore).similaritySearch(any(), anyInt());
        verify(chatClient).call(any());
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
        when(chatClient.call(any())).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(createMockResult("Health check response")));

        // When
        boolean isHealthy = aiService.isHealthy();

        // Then
        assertThat(isHealthy).isTrue();
        verify(chatClient).call(any());
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

    private org.springframework.ai.chat.Generation createMockResult(String content) {
        var mockGeneration = mock(org.springframework.ai.chat.Generation.class);
        var mockAssistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(mockGeneration.getOutput()).thenReturn(mockAssistantMessage);
        when(mockAssistantMessage.getContent()).thenReturn(content);

        return mockGeneration;
    }
}
