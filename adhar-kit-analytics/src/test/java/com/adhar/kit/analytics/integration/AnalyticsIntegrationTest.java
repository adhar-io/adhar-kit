package com.adhar.kit.analytics.integration;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests demonstrating complete analytics usage.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("Analytics Integration Tests")
class AnalyticsIntegrationTest {

    private AnalyticsFacade analytics;
    private TestService testService;

    @BeforeEach
    void setUp() {
        analytics = AnalyticsFacade.getInstance();
        testService = new TestService();
    }

    @Test
    @DisplayName("Complete user journey tracking")
    void testCompleteUserJourney() {
        String userId = "test-user-123";
        String sessionId = "session-456";
        String companyId = "company-789";

        // 1. User arrives (anonymous)
        assertDoesNotThrow(() -> {
            analytics.trackPageView(sessionId, "/landing", Map.of(
                "referrer", "https://google.com",
                "utm_source", "google",
                "utm_campaign", "spring-2024"
            ));
        });

        // 2. User signs up (convert anonymous to identified)
        assertDoesNotThrow(() -> {
            analytics.alias(sessionId, userId);
        });

        // 3. Identify user with properties
        assertDoesNotThrow(() -> {
            analytics.identify(userId, Map.of(
                "$email", "user@example.com",
                "$name", "John Doe",
                "plan", "trial",
                "source", "google"
            ));
        });

        // 4. Track signup event
        assertDoesNotThrow(() -> {
            analytics.track(userId, "User Signed Up", Map.of(
                "plan", "trial",
                "trial_days", 14,
                "referrer", "google"
            ));
        });

        // 5. Add to company group
        assertDoesNotThrow(() -> {
            analytics.group(userId, companyId, Map.of(
                "$group_type", "company",
                "name", "Acme Corp",
                "plan", "enterprise",
                "employees", 50
            ));
        });

        // 6. Check feature flag
        boolean newOnboarding = analytics.isFeatureEnabled(userId, "new-onboarding");
        assertNotNull(newOnboarding);

        // 7. Track feature usage
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Feature Used", Map.of(
                "feature", "dashboard",
                "variant", newOnboarding ? "new" : "old"
            ));
        });

        // 8. Flush events
        assertDoesNotThrow(() -> {
            analytics.flush();
        });

        // Verify health
        Map<String, Object> health = analytics.health();
        assertNotNull(health);
        assertEquals("posthog", health.get("provider"));
    }

    @Test
    @DisplayName("E-commerce purchase flow")
    void testEcommercePurchaseFlow() {
        String userId = "buyer-123";

        // Product view
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Product Viewed", Map.of(
                "product_id", "prod-456",
                "product_name", "Premium Widget",
                "category", "Widgets",
                "price", 99.99,
                "currency", "USD"
            ));
        });

        // Add to cart
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Product Added to Cart", Map.of(
                "product_id", "prod-456",
                "quantity", 2,
                "total_price", 199.98
            ));
        });

        // Checkout started
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Checkout Started", Map.of(
                "cart_value", 199.98,
                "item_count", 2
            ));
        });

        // Purchase completed
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Purchase Completed", Map.of(
                "order_id", "order-789",
                "total", 199.98,
                "currency", "USD",
                "payment_method", "credit_card"
            ));
        });

        // Update user properties
        assertDoesNotThrow(() -> {
            analytics.identify(userId, Map.of(
                "total_purchases", 1,
                "total_revenue", 199.98,
                "last_purchase_date", "2024-01-15"
            ));
        });
    }

    @Test
    @DisplayName("SaaS conversion funnel")
    void testSaaSConversionFunnel() {
        String userId = "saas-user-456";

        // Trial started
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Trial Started", Map.of(
                "plan", "professional",
                "trial_days", 14,
                "source", "website"
            ));
        });

        // Feature exploration
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Feature Used", Map.of(
                "feature", "api_integration",
                "success", true
            ));
        });

        // Billing info added
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Billing Info Added", Map.of(
                "payment_method", "credit_card"
            ));
        });

        // Converted to paid
        assertDoesNotThrow(() -> {
            analytics.track(userId, "Converted to Paid", Map.of(
                "plan", "professional",
                "billing_cycle", "monthly",
                "mrr", 99.00,
                "trial_duration", 7
            ));
        });

        // Update to paying customer
        assertDoesNotThrow(() -> {
            analytics.identify(userId, Map.of(
                "plan", "professional",
                "status", "paying",
                "mrr", 99.00,
                "conversion_date", "2024-01-22"
            ));
        });
    }

    @Test
    @DisplayName("A/B test tracking")
    void testABTesting() {
        String userId = "ab-test-user";

        // Check feature variant
        String buttonColor = analytics.getFeatureFlag(userId, "cta-button-color");

        // Track exposure (user saw this variant)
        assertDoesNotThrow(() -> {
            analytics.track(userId, "$feature_flag_called", Map.of(
                "feature_flag", "cta-button-color",
                "variant", buttonColor != null ? buttonColor : "control"
            ));
        });

        // Track conversion
        assertDoesNotThrow(() -> {
            analytics.track(userId, "CTA Clicked", Map.of(
                "variant", buttonColor != null ? buttonColor : "control",
                "location", "homepage"
            ));
        });
    }

    /**
     * Test service demonstrating annotation usage.
     */
    @Service
    static class TestService {

        @TrackEvent(
            event = "Test Method Called",
            userIdParam = "userId",
            properties = {"param1", "param2"}
        )
        public String testMethod(String userId, String param1, int param2) {
            return "success";
        }

        @IdentifyUser(
            userIdParam = "userId",
            properties = {"email", "name"}
        )
        public void updateUser(String userId, String email, String name) {
            // Update logic
        }

        @TrackGroup(
            userIdParam = "userId",
            groupIdParam = "companyId",
            groupType = "company"
        )
        public void joinCompany(String userId, String companyId) {
            // Join logic
        }

        @FeatureFlag(
            flag = "test-feature",
            userIdParam = "userId",
            trackExposure = true
        )
        public String getFeature(String userId) {
            return "feature-enabled";
        }
    }
}

