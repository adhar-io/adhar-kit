package com.adhar.kit.ai.controller;

import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.adhar.kit.commons.model.ApiResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AI Controller endpoints.
 * Tests REST API functionality, validation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiService aiService;

    private ObjectMapper objectMapper;
    private AiController aiController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        aiController = new AiController(aiService);
        mockMvc = MockMvcBuilders.standaloneSetup(aiController).build();
    }

    @Test
    void testChatEndpoint() throws Exception {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("Hello, how are you?")
                .model("gpt-3.5-turbo")
                .build();

        AiChatResponse response = AiChatResponse.builder()
                .content("I'm doing well, thank you!")
                .model("gpt-3.5-turbo")
                .timestamp(LocalDateTime.now())
                .build();

        when(aiService.chat(any(AiChatRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("I'm doing well, thank you!"))
                .andExpect(jsonPath("$.data.model").value("gpt-3.5-turbo"));
    }

    @Test
    void testChatWithValidationError() throws Exception {
        // Given
        AiChatRequest invalidRequest = AiChatRequest.builder()
                .message("") // Empty message should fail validation
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEmbedEndpoint() throws Exception {
        // Given
        Map<String, String> request = Map.of("text", "Generate embeddings for this");
        List<Float> embeddings = List.of(0.1f, 0.2f, 0.3f);

        when(aiService.embed(anyString())).thenReturn(embeddings);

        // When & Then
        mockMvc.perform(post("/api/v1/ai/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void testSearchEndpoint() throws Exception {
        // Given
        Map<String, Object> request = Map.of(
                "query", "search query",
                "limit", 5
        );

        List<AiService.SimilarityResult> results = List.of(
                new AiService.SimilarityResult("doc1", "Content 1", 0.9, Map.of())
        );

        when(aiService.search(anyString(), any(Integer.class))).thenReturn(results);

        // When & Then
        mockMvc.perform(post("/api/v1/ai/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("doc1"));
    }

    @Test
    void testRagChatEndpoint() throws Exception {
        // Given
        AiChatRequest request = AiChatRequest.builder()
                .message("What is machine learning?")
                .build();

        AiChatResponse response = AiChatResponse.builder()
                .content("Machine learning is a subset of AI...")
                .model("gpt-3.5-turbo")
                .timestamp(LocalDateTime.now())
                .build();

        when(aiService.ragChat(any(AiChatRequest.class), anyString())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/ai/rag/chat")
                .param("knowledgeBase", "ml-docs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Machine learning is a subset of AI..."));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        // Given
        when(aiService.isHealthy()).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void testGetModelsEndpoint() throws Exception {
        // Given
        List<String> models = List.of("gpt-3.5-turbo", "gpt-4", "llama2");
        when(aiService.getAvailableModels()).thenReturn(models);

        // When & Then
        mockMvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    // ==================== Error paths ====================

    @Test
    void testChatEndpointReturns500OnServiceFailure() throws Exception {
        AiChatRequest request = AiChatRequest.builder().message("Hello").build();
        when(aiService.chat(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("provider down"));

        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to process chat request"));
    }

    @Test
    void testEmbedEndpointMissingTextReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("notText", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Text parameter is required"));
    }

    @Test
    void testEmbedEndpointBlankTextReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testEmbedEndpointReturns500OnServiceFailure() throws Exception {
        when(aiService.embed(anyString())).thenThrow(new RuntimeException("embed failed"));

        mockMvc.perform(post("/api/v1/ai/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "hello"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to generate embeddings"));
    }

    @Test
    void testSearchEndpointMissingQueryReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("limit", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Query parameter is required"));
    }

    @Test
    void testSearchEndpointReturns500OnServiceFailure() throws Exception {
        when(aiService.search(anyString(), any(Integer.class)))
                .thenThrow(new RuntimeException("search boom"));

        mockMvc.perform(post("/api/v1/ai/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("query", "hi"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to perform search"));
    }

    @Test
    void testRagChatEndpointReturns500OnServiceFailure() throws Exception {
        AiChatRequest request = AiChatRequest.builder().message("q").build();
        when(aiService.ragChat(any(AiChatRequest.class), anyString()))
                .thenThrow(new RuntimeException("rag boom"));

        mockMvc.perform(post("/api/v1/ai/rag/chat")
                .param("knowledgeBase", "kb")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to process RAG chat request"));
    }

    @Test
    void testAddDocumentsSuccess() throws Exception {
        List<AiService.DocumentChunk> docs = List.of(
                new AiService.DocumentChunk("d1", "content", "src", Map.of()));

        mockMvc.perform(post("/api/v1/ai/rag/documents")
                .param("knowledgeBase", "kb")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testAddDocumentsEmptyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/rag/documents")
                .param("knowledgeBase", "kb")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Documents cannot be empty"));
    }

    @Test
    void testAddDocumentsReturns500OnServiceFailure() throws Exception {
        List<AiService.DocumentChunk> docs = List.of(
                new AiService.DocumentChunk("d1", "content", "src", Map.of()));
        org.mockito.Mockito.doThrow(new RuntimeException("ingest boom"))
                .when(aiService).addDocuments(any(), anyString());

        mockMvc.perform(post("/api/v1/ai/rag/documents")
                .param("knowledgeBase", "kb")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docs)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to add documents to knowledge base"));
    }

    @Test
    void testGetModelsReturns500OnServiceFailure() throws Exception {
        when(aiService.getAvailableModels()).thenThrow(new RuntimeException("models boom"));

        mockMvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to retrieve available models"));
    }

    @Test
    void testHealthEndpointReturnsServiceUnavailableWhenDown() throws Exception {
        when(aiService.isHealthy()).thenReturn(false);

        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("AI services are not responding"));
    }

    @Test
    void testHealthEndpointReturns500OnServiceFailure() throws Exception {
        when(aiService.isHealthy()).thenThrow(new RuntimeException("health boom"));

        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Health check failed"));
    }

    // ==================== Reactive endpoints (invoked directly) ====================

    @Test
    void testChatAsyncEndpointSuccess() {
        AiChatRequest request = AiChatRequest.builder().message("hi").build();
        AiChatResponse response = AiChatResponse.builder().content("hello back").build();
        when(aiService.chatAsync(any(AiChatRequest.class))).thenReturn(Mono.just(response));

        ResponseEntity<ApiResponse<AiChatResponse>> result =
                aiController.chatAsync(request, null).block();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData().getContent()).isEqualTo("hello back");
    }

    @Test
    void testChatAsyncEndpointErrorReturns500() {
        AiChatRequest request = AiChatRequest.builder().message("hi").build();
        when(aiService.chatAsync(any(AiChatRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("async boom")));

        ResponseEntity<ApiResponse<AiChatResponse>> result =
                aiController.chatAsync(request, null).block();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("Failed to process async chat request");
    }

    @Test
    void testChatStreamEndpointSuccess() {
        AiChatRequest request = AiChatRequest.builder().message("hi").build();
        AiChatResponse chunk = AiChatResponse.builder().content("chunk").requestId("r1").build();
        when(aiService.chatStream(any(AiChatRequest.class))).thenReturn(Flux.just(chunk));

        List<AiChatResponse> chunks = aiController.chatStream(request, null).collectList().block();

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("chunk");
    }

    @Test
    void testChatStreamEndpointPropagatesError() {
        AiChatRequest request = AiChatRequest.builder().message("hi").build();
        when(aiService.chatStream(any(AiChatRequest.class)))
                .thenReturn(Flux.error(new RuntimeException("stream boom")));

        Flux<AiChatResponse> flux = aiController.chatStream(request, null);

        assertThatThrownBy(() -> flux.collectList().block())
                .hasMessageContaining("stream boom");
    }
}
