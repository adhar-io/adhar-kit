package com.adhar.kit.ai;

import com.adhar.kit.ai.api.AiService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AiFacade.
 *
 * <p>These tests verify the facade's initialization, singleton pattern,
 * and graceful degradation when no AI provider is configured.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class AiFacadeTest {

    /**
     * Test singleton pattern ensures same instance is returned.
     */
    @Test
    void testSingleton() {
        AiFacade instance1 = AiFacade.getInstance();
        AiFacade instance2 = AiFacade.getInstance();

        assertNotNull(instance1, "AiFacade instance should not be null");
        assertSame(instance1, instance2, "Should return same singleton instance");
    }

    /**
     * Test facade initializes without errors even when no provider is configured.
     */
    @Test
    void testGracefulInitialization() {
        AiFacade ai = AiFacade.getInstance();

        assertNotNull(ai, "AiFacade should initialize successfully");
        // Note: isAvailable() might be true with default provider
        assertNotNull(ai.getProvider(), "Should have a provider name");
    }

    /**
     * Test health check returns proper status.
     */
    @Test
    void testHealthCheck() {
        AiFacade ai = AiFacade.getInstance();
        Map<String, Object> health = ai.health();

        assertNotNull(health, "Health check should return status");
        assertTrue(health.containsKey("available"), "Health should include availability");
        assertTrue(health.containsKey("provider"), "Health should include provider name");
    }

    /**
     * Test chat throws exception when provider not configured.
     */
    @Test
    void testChatThrowsExceptionWhenNotConfigured() {
        AiFacade ai = AiFacade.getInstance();

        assertThrows(AiFacade.AiException.class,
            () -> ai.chat("test message"),
            "Should throw exception when provider not configured");
    }

    /**
     * Test embedding throws exception when provider not configured.
     */
    @Test
    void testEmbedThrowsExceptionWhenNotConfigured() {
        AiFacade ai = AiFacade.getInstance();

        assertThrows(AiFacade.AiException.class,
            () -> ai.embed("test text"),
            "Should throw exception when provider not configured");
    }

    /**
     * Test listModels returns empty list when provider not configured.
     */
    @Test
    void testListModelsReturnsEmpty() {
        AiFacade ai = AiFacade.getInstance();

        assertTrue(ai.listModels().isEmpty(),
            "Should return empty list when provider not configured");
    }

    /**
     * Test countTokens returns fallback estimate when provider not configured.
     */
    @Test
    void testCountTokensFallback() {
        AiFacade ai = AiFacade.getInstance();
        String text = "This is a test message with approximately 8 tokens";

        int tokens = ai.countTokens(text);

        assertTrue(tokens > 0, "Should return positive token count");
        // Rough estimate is length/4
        assertTrue(tokens >= text.length() / 5 && tokens <= text.length() / 3,
            "Should return reasonable estimate");
    }

    /**
     * Test estimateCost returns 0 when provider not configured.
     */
    @Test
    void testEstimateCostFallback() {
        AiFacade ai = AiFacade.getInstance();

        double cost = ai.estimateCost("test text");

        assertEquals(0.0, cost, "Should return 0 cost when provider not configured");
    }

    /**
     * Test provider name is accessible.
     */
    @Test
    void testProviderName() {
        AiFacade ai = AiFacade.getInstance();
        String provider = ai.getProvider();

        assertNotNull(provider, "Provider name should not be null");
        assertFalse(provider.isEmpty(), "Provider name should not be empty");
    }

    /**
     * Test exception contains helpful error message.
     */
    @Test
    void testExceptionMessage() {
        AiFacade ai = AiFacade.getInstance();

        try {
            ai.chat("test");
            fail("Should have thrown exception");
        } catch (Exception e) {
            String message = e.getMessage();
            assertNotNull(message, "Exception should have message");
            assertTrue(message.contains("not configured") || message.contains("failed"),
                "Message should indicate configuration issue");
        }
    }

    // ==================== Chat overloads ====================

    @Test
    void testChatWithSystemPromptThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.chat("system prompt", "user message"));
    }

    @Test
    void testChatWithContextThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.chatWithContext(List.of(), "message"));
    }

    @Test
    void testAdvancedChatWithMessagesThrows() {
        AiFacade ai = AiFacade.getInstance();
        AiService.ChatRequest request = mock(AiService.ChatRequest.class);
        when(request.getMessages()).thenReturn(List.of());
        assertThrows(AiFacade.AiException.class, () -> ai.chat(request));
    }

    @Test
    void testAdvancedChatWithNullMessagesThrows() {
        AiFacade ai = AiFacade.getInstance();
        // getMessages() returns null by default -> exercises the null branch of the log statement
        AiService.ChatRequest request = mock(AiService.ChatRequest.class);
        assertThrows(AiFacade.AiException.class, () -> ai.chat(request));
    }

    @Test
    void testChatStreamStringThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.chatStream("message", chunk -> { }));
    }

    @Test
    void testChatStreamRequestThrows() {
        AiFacade ai = AiFacade.getInstance();
        AiService.ChatRequest request = mock(AiService.ChatRequest.class);
        assertThrows(AiFacade.AiException.class, () -> ai.chatStream(request, chunk -> { }));
    }

    @Test
    void testChatAsyncPropagatesException() {
        AiFacade ai = AiFacade.getInstance();
        CompletableFuture<String> future = ai.chatAsync("message");
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(AiFacade.AiException.class, ex.getCause());
    }

    // ==================== Embeddings ====================

    @Test
    void testEmbedBatchThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.embedBatch(List.of("a", "b")));
    }

    @Test
    void testSimilarityThrows() {
        AiFacade ai = AiFacade.getInstance();
        // similarity() delegates to embed(), which fails with default provider
        assertThrows(AiFacade.AiException.class, () -> ai.similarity("text one", "text two"));
    }

    @Test
    void testFindSimilarThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.findSimilar("query", List.of("a", "b"), 2));
    }

    // ==================== Image Generation ====================

    @Test
    void testGenerateImageFromPromptThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.generateImage("a sunset over mountains"));
    }

    @Test
    void testGenerateImageFromRequestThrows() {
        AiFacade ai = AiFacade.getInstance();
        AiService.ImageRequest request = mock(AiService.ImageRequest.class);
        when(request.getPrompt()).thenReturn("a sunset");
        assertThrows(AiFacade.AiException.class, () -> ai.generateImage(request));
    }

    @Test
    void testAnalyzeImageThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class,
            () -> ai.analyzeImage("http://example.com/img.png", "what is this?"));
    }

    // ==================== Function Calling ====================

    @Test
    void testChatWithFunctionsThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.chatWithFunctions("message", List.of()));
    }

    @Test
    void testExecuteFunctionThrows() {
        AiFacade ai = AiFacade.getInstance();
        AiService.FunctionCall call = mock(AiService.FunctionCall.class);
        when(call.getName()).thenReturn("get_weather");
        assertThrows(AiFacade.AiException.class, () -> ai.executeFunction(call));
    }

    // ==================== RAG ====================

    @Test
    void testStoreDocumentThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class,
            () -> ai.storeDocument("doc1", "content", Map.of("k", "v")));
    }

    @Test
    void testStoreDocumentsThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.storeDocuments(List.of()));
    }

    @Test
    void testQueryDocumentsThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.queryDocuments("question", 3));
    }

    @Test
    void testDeleteDocumentThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.deleteDocument("doc1"));
    }

    // ==================== Model Management ====================

    @Test
    void testGetModelInfoThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, ai::getModelInfo);
    }

    @Test
    void testUseModelThrows() {
        AiFacade ai = AiFacade.getInstance();
        assertThrows(AiFacade.AiException.class, () -> ai.useModel("gpt-4"));
    }

    // ==================== Utilities ====================

    @Test
    void testIsAvailableWithDefaultProvider() {
        AiFacade ai = AiFacade.getInstance();
        // Default provider's test() is a no-op, so the facade reports available.
        assertTrue(ai.isAvailable());
    }

    @Test
    void testProviderNameIsDefault() {
        AiFacade ai = AiFacade.getInstance();
        assertEquals("default", ai.getProvider());
    }

    @Test
    void testHealthIncludesProviderStatus() {
        AiFacade ai = AiFacade.getInstance();
        Map<String, Object> health = ai.health();
        assertEquals("default", health.get("provider"));
        assertEquals(Boolean.TRUE, health.get("available"));
        // Default provider contributes its own status entry.
        assertEquals("not_configured", health.get("status"));
    }

    @Test
    void testEstimateCostReturnsZero() {
        AiFacade ai = AiFacade.getInstance();
        assertEquals(0.0, ai.estimateCost("some text"));
    }

    // ==================== AiException ====================

    @Test
    void testAiExceptionMessageOnlyConstructor() {
        AiFacade.AiException ex = new AiFacade.AiException("boom");
        assertEquals("boom", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testAiExceptionMessageAndCauseConstructor() {
        Throwable cause = new IllegalStateException("root");
        AiFacade.AiException ex = new AiFacade.AiException("boom", cause);
        assertEquals("boom", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}

