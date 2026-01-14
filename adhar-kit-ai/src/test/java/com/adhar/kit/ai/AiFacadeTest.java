package com.adhar.kit.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}

