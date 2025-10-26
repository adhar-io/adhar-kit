package com.adhar.adharkit.ai.controller;

import com.adhar.adharkit.ai.model.AiChatRequest;
import com.adhar.adharkit.ai.model.AiChatResponse;
import com.adhar.adharkit.ai.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AiController aiController = new AiController(aiService);
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
}
