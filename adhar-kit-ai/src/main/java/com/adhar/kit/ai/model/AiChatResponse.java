package com.adhar.kit.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response model for AI chat operations.
 * Provides comprehensive response data with usage metrics and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatResponse {

    private String content;
    private String model;
    private String provider;
    private String sessionId;
    private String requestId;

    private UsageMetrics usage;
    private ResponseMetadata metadata;
    private List<AlternativeResponse> alternatives;
    private FinishReason finishReason;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageMetrics {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Long processingTimeMs;
        private Double cost; // Estimated cost in USD
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMetadata {
        private String version;
        private Boolean cached;
        private String cacheKey;
        private Double confidence;
        private Map<String, Object> providerSpecific;
        private List<String> warnings;
        private String traceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativeResponse {
        private String content;
        private Double probability;
        private FinishReason finishReason;
    }

    public enum FinishReason {
        STOP, LENGTH, CONTENT_FILTER, TOOL_CALLS, FUNCTION_CALL, ERROR
    }
}
