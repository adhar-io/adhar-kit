package com.adhar.kit.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request model for AI chat operations.
 * Supports various AI providers with consistent interface.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatRequest {

    @NotBlank(message = "Message cannot be blank")
    private String message;

    private String model;
    private String provider; // openai, azure, ollama
    private String sessionId;
    private String userId;
    private String tenantId;

    private AiParameters parameters;
    private List<ChatMessage> history;
    private Map<String, Object> context;
    private List<String> tools;
    private Boolean streamResponse;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiParameters {
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private List<String> stopSequences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        @NotNull
        private MessageRole role;
        @NotBlank
        private String content;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM, FUNCTION
    }
}
