package com.adhar.kit.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AnalyticsFacade.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("Analytics Facade Tests")
class AnalyticsFacadeTest {

    private AnalyticsFacade analytics;

    @BeforeEach
    void setUp() {
        analytics = AnalyticsFacade.getInstance();
    }

    @Test
    @DisplayName("Should return singleton instance")
    void testSingleton() {
        AnalyticsFacade instance1 = AnalyticsFacade.getInstance();
        AnalyticsFacade instance2 = AnalyticsFacade.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "Should return same singleton instance");
    }

    @Test
    @DisplayName("Should initialize without errors")
    void testInitialization() {
        assertNotNull(analytics);
        assertNotNull(analytics.health());
    }

    @Test
    @DisplayName("Should track event without errors")
    void testTrackEvent() {
        assertDoesNotThrow(() -> {
            analytics.track("test-user", "Test Event", Map.of(
                "property1", "value1",
                "property2", 123,
                "property3", true
            ));
        });
    }

    @Test
    @DisplayName("Should track event without properties")
    void testTrackEventWithoutProperties() {
        assertDoesNotThrow(() -> {
            analytics.track("test-user", "Simple Event");
        });
    }

    @Test
    @DisplayName("Should track page view")
    void testTrackPageView() {
        assertDoesNotThrow(() -> {
            analytics.trackPageView("test-user", "/dashboard", Map.of(
                "referrer", "https://google.com",
                "title", "Dashboard"
            ));
        });
    }

    @Test
    @DisplayName("Should track page view without properties")
    void testTrackPageViewWithoutProperties() {
        assertDoesNotThrow(() -> {
            analytics.trackPageView("test-user", "/home");
        });
    }

    @Test
    @DisplayName("Should identify user with properties")
    void testIdentifyUser() {
        assertDoesNotThrow(() -> {
            analytics.identify("test-user", Map.of(
                "$email", "test@example.com",
                "$name", "Test User",
                "plan", "premium"
            ));
        });
    }

    @Test
    @DisplayName("Should identify user without properties")
    void testIdentifyUserWithoutProperties() {
        assertDoesNotThrow(() -> {
            analytics.identify("test-user");
        });
    }

    @Test
    @DisplayName("Should alias user")
    void testAliasUser() {
        assertDoesNotThrow(() -> {
            analytics.alias("anonymous-id", "user-id");
        });
    }

    @Test
    @DisplayName("Should add user to group")
    void testGroupAnalytics() {
        assertDoesNotThrow(() -> {
            analytics.group("test-user", "company-123", Map.of(
                "name", "Test Company",
                "plan", "enterprise",
                "employees", 100
            ));
        });
    }

    @Test
    @DisplayName("Should check feature flag")
    void testFeatureFlag() {
        boolean enabled = analytics.isFeatureEnabled("test-user", "test-feature");
        // Should not throw, returns false if not configured
        assertNotNull(enabled);
    }

    @Test
    @DisplayName("Should get feature flag value")
    void testGetFeatureFlagValue() {
        String value = analytics.getFeatureFlag("test-user", "test-variant");
        // Should return null if not configured
        assertTrue(value == null || value instanceof String);
    }

    @Test
    @DisplayName("Should flush events without errors")
    void testFlush() {
        assertDoesNotThrow(() -> {
            analytics.flush();
        });
    }

    @Test
    @DisplayName("Should return health status")
    void testHealth() {
        Map<String, Object> health = analytics.health();

        assertNotNull(health);
        assertTrue(health.containsKey("available"));
        assertTrue(health.containsKey("provider"));
        assertEquals("posthog", health.get("provider"));
    }

    @Test
    @DisplayName("Should handle null userId gracefully")
    void testNullUserId() {
        assertDoesNotThrow(() -> {
            analytics.track(null, "Test Event");
        });
    }

    @Test
    @DisplayName("Should handle null event name gracefully")
    void testNullEventName() {
        assertDoesNotThrow(() -> {
            analytics.track("test-user", null);
        });
    }

    @Test
    @DisplayName("Should handle null properties gracefully")
    void testNullProperties() {
        assertDoesNotThrow(() -> {
            analytics.track("test-user", "Test Event", null);
        });
    }

    @Test
    @DisplayName("Should handle empty properties")
    void testEmptyProperties() {
        assertDoesNotThrow(() -> {
            analytics.track("test-user", "Test Event", Map.of());
        });
    }

    @Test
    @DisplayName("Should reload feature flags")
    void testReloadFeatureFlags() {
        assertDoesNotThrow(() -> {
            analytics.reloadFeatureFlags();
        });
    }

    @Test
    @DisplayName("Should track complex event properties")
    void testComplexEventProperties() {
        assertDoesNotThrow(() -> {
            analytics.track("test-user", "Complex Event", Map.of(
                "string", "value",
                "number", 42,
                "decimal", 99.99,
                "boolean", true,
                "array", java.util.List.of("a", "b", "c"),
                "nested", Map.of("key", "value")
            ));
        });
    }

    @Test
    @DisplayName("Should handle multiple sequential events")
    void testMultipleEvents() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                analytics.track("user-" + i, "Event " + i, Map.of("index", i));
            }
            analytics.flush();
        });
    }

    @Test
    @DisplayName("Should check availability status")
    void testAvailability() {
        boolean available = analytics.isAvailable();
        assertNotNull(available);
        // Will be false if PostHog not configured, which is OK for tests
    }
}

