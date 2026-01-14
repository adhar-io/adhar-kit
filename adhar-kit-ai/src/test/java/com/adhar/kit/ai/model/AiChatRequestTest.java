package com.adhar.kit.ai.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AI Chat Request model.
 */
class AiChatRequestTest {

    @Test
    void testBuilder() {
        AiChatRequest.AiParameters parameters = AiChatRequest.AiParameters.builder()
                .temperature(0.8)
                .maxTokens(500)
                .build();

        AiChatRequest request = AiChatRequest.builder()
                .message("Hello")
                .model("gpt-4")
                .parameters(parameters)
                .build();

        assertThat(request.getMessage()).isEqualTo("Hello");
        assertThat(request.getModel()).isEqualTo("gpt-4");
        assertThat(request.getParameters()).isNotNull();
        assertThat(request.getParameters().getTemperature()).isEqualTo(0.8);
        assertThat(request.getParameters().getMaxTokens()).isEqualTo(500);
    }

    @Test
    void testDefaultValues() {
        AiChatRequest request = AiChatRequest.builder()
                .message("Test")
                .build();

        assertThat(request.getMessage()).isEqualTo("Test");
        assertThat(request.getModel()).isNull();
        assertThat(request.getProvider()).isNull();
    }

    @Test
    void testWithProvider() {
        AiChatRequest request = AiChatRequest.builder()
                .message("User message")
                .provider("openai")
                .build();

        assertThat(request.getMessage()).isEqualTo("User message");
        assertThat(request.getProvider()).isEqualTo("openai");
    }

    @Test
    void testWithContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("key1", "value1");
        context.put("key2", 123);

        AiChatRequest request = AiChatRequest.builder()
                .message("Test")
                .context(context)
                .build();

        assertThat(request.getContext()).containsEntry("key1", "value1");
        assertThat(request.getContext()).containsEntry("key2", 123);
    }

    @Test
    void testAllFields() {
        Map<String, Object> context = new HashMap<>();
        context.put("test", "value");

        AiChatRequest.AiParameters parameters = AiChatRequest.AiParameters.builder()
                .temperature(0.7)
                .maxTokens(1000)
                .topP(0.9)
                .topK(50)
                .frequencyPenalty(0.5)
                .presencePenalty(0.5)
                .build();

        AiChatRequest request = AiChatRequest.builder()
                .message("Test message")
                .model("gpt-4")
                .provider("openai")
                .sessionId("session-123")
                .userId("user-456")
                .tenantId("tenant-789")
                .parameters(parameters)
                .context(context)
                .streamResponse(false)
                .build();

        assertThat(request.getMessage()).isEqualTo("Test message");
        assertThat(request.getModel()).isEqualTo("gpt-4");
        assertThat(request.getProvider()).isEqualTo("openai");
        assertThat(request.getSessionId()).isEqualTo("session-123");
        assertThat(request.getUserId()).isEqualTo("user-456");
        assertThat(request.getTenantId()).isEqualTo("tenant-789");
        assertThat(request.getParameters()).isNotNull();
        assertThat(request.getContext()).isNotNull();
        assertThat(request.getStreamResponse()).isFalse();
    }

    @Test
    void testParametersBuilder() {
        AiChatRequest.AiParameters parameters = AiChatRequest.AiParameters.builder()
                .temperature(0.9)
                .maxTokens(2000)
                .topP(0.95)
                .topK(100)
                .frequencyPenalty(0.3)
                .presencePenalty(0.4)
                .build();

        assertThat(parameters.getTemperature()).isEqualTo(0.9);
        assertThat(parameters.getMaxTokens()).isEqualTo(2000);
        assertThat(parameters.getTopP()).isEqualTo(0.95);
        assertThat(parameters.getTopK()).isEqualTo(100);
        assertThat(parameters.getFrequencyPenalty()).isEqualTo(0.3);
        assertThat(parameters.getPresencePenalty()).isEqualTo(0.4);
    }
}

