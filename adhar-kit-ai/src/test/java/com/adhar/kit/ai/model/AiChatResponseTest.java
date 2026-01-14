package com.adhar.kit.ai.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AI Chat Response model.
 */
class AiChatResponseTest {

    @Test
    void testBuilder() {
        AiChatResponse response = AiChatResponse.builder()
                .content("Hello, world!")
                .model("gpt-4")
                .finishReason(AiChatResponse.FinishReason.STOP)
                .build();

        assertThat(response.getContent()).isEqualTo("Hello, world!");
        assertThat(response.getModel()).isEqualTo("gpt-4");
        assertThat(response.getFinishReason()).isEqualTo(AiChatResponse.FinishReason.STOP);
    }

    @Test
    void testWithUsageMetrics() {
        AiChatResponse.UsageMetrics usage = AiChatResponse.UsageMetrics.builder()
                .promptTokens(10)
                .completionTokens(20)
                .totalTokens(30)
                .build();

        AiChatResponse response = AiChatResponse.builder()
                .content("Test")
                .usage(usage)
                .build();

        assertThat(response.getContent()).isEqualTo("Test");
        assertThat(response.getUsage()).isNotNull();
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(10);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(20);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(30);
    }

    @Test
    void testWithMetadata() {
        Map<String, Object> providerSpecific = new HashMap<>();
        providerSpecific.put("key", "value");

        AiChatResponse.ResponseMetadata metadata = AiChatResponse.ResponseMetadata.builder()
                .version("v1")
                .cached(false)
                .providerSpecific(providerSpecific)
                .build();

        AiChatResponse response = AiChatResponse.builder()
                .content("Response")
                .metadata(metadata)
                .build();

        assertThat(response.getContent()).isEqualTo("Response");
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getVersion()).isEqualTo("v1");
        assertThat(response.getMetadata().getCached()).isFalse();
    }

    @Test
    void testAllFields() {
        AiChatResponse.UsageMetrics usage = AiChatResponse.UsageMetrics.builder()
                .promptTokens(15)
                .completionTokens(25)
                .totalTokens(40)
                .processingTimeMs(100L)
                .cost(0.01)
                .build();

        AiChatResponse.ResponseMetadata metadata = AiChatResponse.ResponseMetadata.builder()
                .version("v1")
                .cached(true)
                .confidence(0.95)
                .build();

        AiChatResponse response = AiChatResponse.builder()
                .content("Complete response")
                .model("gpt-4")
                .provider("openai")
                .sessionId("session-123")
                .requestId("req-456")
                .usage(usage)
                .metadata(metadata)
                .finishReason(AiChatResponse.FinishReason.STOP)
                .timestamp(LocalDateTime.now())
                .build();

        assertThat(response.getContent()).isEqualTo("Complete response");
        assertThat(response.getModel()).isEqualTo("gpt-4");
        assertThat(response.getProvider()).isEqualTo("openai");
        assertThat(response.getSessionId()).isEqualTo("session-123");
        assertThat(response.getRequestId()).isEqualTo("req-456");
        assertThat(response.getUsage()).isNotNull();
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getFinishReason()).isEqualTo(AiChatResponse.FinishReason.STOP);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void testEmptyContent() {
        AiChatResponse response = AiChatResponse.builder()
                .content("")
                .build();

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testNullFinishReason() {
        AiChatResponse response = AiChatResponse.builder()
                .content("Test")
                .finishReason(null)
                .build();

        assertThat(response.getContent()).isEqualTo("Test");
        assertThat(response.getFinishReason()).isNull();
    }

    @Test
    void testFinishReasonEnum() {
        assertThat(AiChatResponse.FinishReason.STOP).isNotNull();
        assertThat(AiChatResponse.FinishReason.LENGTH).isNotNull();
        assertThat(AiChatResponse.FinishReason.CONTENT_FILTER).isNotNull();
        assertThat(AiChatResponse.FinishReason.TOOL_CALLS).isNotNull();
        assertThat(AiChatResponse.FinishReason.FUNCTION_CALL).isNotNull();
        assertThat(AiChatResponse.FinishReason.ERROR).isNotNull();
    }
}

