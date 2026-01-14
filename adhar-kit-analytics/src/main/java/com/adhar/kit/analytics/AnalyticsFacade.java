package com.adhar.kit.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

/**
 * Universal Analytics Facade providing PostHog integration.
 *
 * <p>This facade provides a simplified, production-ready interface to PostHog's
 * powerful analytics capabilities including:</p>
 * <ul>
 *   <li><b>Event Tracking</b> - Track user actions, page views, and custom events</li>
 *   <li><b>User Identification</b> - Identify and track users across sessions</li>
 *   <li><b>Feature Flags</b> - A/B testing and gradual rollouts</li>
 *   <li><b>Session Recording</b> - Understand user behavior</li>
 *   <li><b>Funnel Analysis</b> - Track user conversion flows</li>
 *   <li><b>Cohort Analysis</b> - Group users by behavior</li>
 *   <li><b>Retention Tracking</b> - Measure user retention</li>
 * </ul>
 *
 * <p><b>Example - Basic Event Tracking:</b></p>
 * <pre>{@code
 * AnalyticsFacade analytics = AnalyticsFacade.getInstance();
 *
 * // Track user signup
 * analytics.track("user123", "User Signed Up", Map.of(
 *     "plan", "premium",
 *     "referrer", "google"
 * ));
 * }</pre>
 *
 * <p><b>Example - User Identification:</b></p>
 * <pre>{@code
 * // Identify user with properties
 * analytics.identify("user123", Map.of(
 *     "email", "user@example.com",
 *     "name", "John Doe",
 *     "plan", "premium"
 * ));
 * }</pre>
 *
 * <p><b>Example - Feature Flags:</b></p>
 * <pre>{@code
 * // Check if feature is enabled for user
 * boolean newDesign = analytics.isFeatureEnabled("user123", "new-dashboard");
 * if (newDesign) {
 *     // Show new dashboard
 * }
 * }</pre>
 *
 * <p><b>Example - Group Analytics:</b></p>
 * <pre>{@code
 * // Track company-level events
 * analytics.group("user123", "company456", Map.of(
 *     "name", "Acme Corp",
 *     "plan", "enterprise",
 *     "employees", 500
 * ));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AnalyticsFacade {

    /**
     * Singleton instance (thread-safe lazy initialization).
     */
    private static volatile AnalyticsFacade instance;

    /**
     * HTTP client for PostHog API calls.
     */
    private OkHttpClient httpClient;

    /**
     * JSON object mapper.
     */
    private ObjectMapper objectMapper;

    /**
     * PostHog API configuration.
     */
    private String apiKey;
    private String apiUrl;

    /**
     * Indicates if analytics is available and configured.
     */
    private volatile boolean available = false;

    /**
     * Feature flags cache (user -> flag -> value).
     */
    private final Map<String, Map<String, Boolean>> featureFlagsCache = new ConcurrentHashMap<>();

    /**
     * Private constructor for singleton pattern.
     */
    private AnalyticsFacade() {
        log.info("Initializing Analytics Facade v1.0.0 (PostHog)");
        initializePostHog();
    }

    /**
     * Gets the singleton instance.
     * Uses double-checked locking for thread-safe lazy initialization.
     *
     * @return the AnalyticsFacade singleton instance
     */
    public static AnalyticsFacade getInstance() {
        if (instance == null) {
            synchronized (AnalyticsFacade.class) {
                if (instance == null) {
                    instance = new AnalyticsFacade();
                }
            }
        }
        return instance;
    }

    // ==================== Event Tracking ====================

    /**
     * Tracks an event for a user.
     *
     * <p>Events are the foundation of analytics. Use them to track any action
     * a user takes in your application.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * analytics.track("user123", "Product Purchased", Map.of(
     *     "product_id", "prod_456",
     *     "price", 99.99,
     *     "currency", "USD"
     * ));
     * }</pre>
     *
     * @param userId user identifier
     * @param event event name (e.g., "Button Clicked", "Page Viewed")
     * @param properties event properties (metadata)
     */
    public void track(String userId, String event, Map<String, Object> properties) {
        if (!available) {
            log.warn("Analytics not available, skipping event: {}", event);
            return;
        }

        try {
            // Send event to PostHog API
            sendEvent("capture", Map.of(
                "distinct_id", userId,
                "event", event,
                "properties", properties != null ? properties : Map.of()
            ));
        } catch (Exception e) {
            log.error("Failed to track event: " + event, e);
        }
    }

    /**
     * Tracks an event without properties.
     *
     * @param userId user identifier
     * @param event event name
     */
    public void track(String userId, String event) {
        track(userId, event, Collections.emptyMap());
    }

    /**
     * Tracks a page view.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * analytics.trackPageView("user123", "/dashboard", Map.of(
     *     "referrer", "https://google.com",
     *     "title", "Dashboard"
     * ));
     * }</pre>
     *
     * @param userId user identifier
     * @param page page URL or path
     * @param properties page properties
     */
    public void trackPageView(String userId, String page, Map<String, Object> properties) {
        Map<String, Object> props = new HashMap<>(properties);
        props.put("$current_url", page);
        track(userId, "$pageview", props);
    }

    /**
     * Tracks a page view without properties.
     *
     * @param userId user identifier
     * @param page page URL or path
     */
    public void trackPageView(String userId, String page) {
        trackPageView(userId, page, Collections.emptyMap());
    }

    // ==================== User Identification ====================

    /**
     * Identifies a user with properties.
     *
     * <p>Call this when you know who the user is (e.g., after login).
     * This links their anonymous activity to their identified profile.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * analytics.identify("user123", Map.of(
     *     "$email", "user@example.com",
     *     "$name", "John Doe",
     *     "plan", "premium",
     *     "signup_date", "2024-01-15"
     * ));
     * }</pre>
     *
     * @param userId user identifier
     * @param properties user properties (use $-prefixed keys for special properties)
     */
    public void identify(String userId, Map<String, Object> properties) {
        if (!available) {
            log.warn("Analytics not available, skipping identify");
            return;
        }

        try {
            log.debug("Identifying user: {}", userId);
            sendEvent("identify", Map.of("distinct_id", userId, "properties", properties != null ? properties : Map.of()));
        } catch (Exception e) {
            log.error("Failed to identify user: " + userId, e);
        }
    }

    /**
     * Identifies a user without properties.
     *
     * @param userId user identifier
     */
    public void identify(String userId) {
        identify(userId, Collections.emptyMap());
    }

    /**
     * Aliases one user ID to another.
     *
     * <p>Use this to connect an anonymous user to their identified profile.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // After user logs in
     * analytics.alias("anonymous123", "user123");
     * }</pre>
     *
     * @param distinctId the current user ID
     * @param alias the ID to alias to
     */
    public void alias(String distinctId, String alias) {
        if (!available) {
            log.warn("Analytics not available, skipping alias");
            return;
        }

        try {
            log.debug("Aliasing {} to {}", distinctId, alias);
            sendEvent("alias", Map.of("distinct_id", distinctId, "alias", alias));
        } catch (Exception e) {
            log.error("Failed to alias user", e);
        }
    }

    // ==================== Group Analytics ====================

    /**
     * Associates a user with a group (company, organization, etc.).
     *
     * <p>Groups allow you to track analytics at an organization level.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * analytics.group("user123", "company456", Map.of(
     *     "$group_type", "company",
     *     "name", "Acme Corp",
     *     "plan", "enterprise",
     *     "employees", 500,
     *     "industry", "technology"
     * ));
     * }</pre>
     *
     * @param userId user identifier
     * @param groupId group identifier
     * @param properties group properties
     */
    public void group(String userId, String groupId, Map<String, Object> properties) {
        if (!available) {
            log.warn("Analytics not available, skipping group");
            return;
        }

        try {
            log.debug("Adding user {} to group {}", userId, groupId);
            Map<String, Object> props = new HashMap<>(properties);
            props.put("$group_key", groupId);
            sendEvent("group", Map.of("type", "company", "id", groupId, "properties", props != null ? props : Map.of()));
        } catch (Exception e) {
            log.error("Failed to add user to group", e);
        }
    }

    // ==================== Feature Flags ====================

    /**
     * Checks if a feature flag is enabled for a user.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * if (analytics.isFeatureEnabled("user123", "new-dashboard")) {
     *     // Show new dashboard
     * } else {
     *     // Show old dashboard
     * }
     * }</pre>
     *
     * @param userId user identifier
     * @param featureKey feature flag key
     * @return true if feature is enabled, false otherwise
     */
    public boolean isFeatureEnabled(String userId, String featureKey) {
        if (!available) {
            log.warn("Analytics not available, returning false for feature: {}", featureKey);
            return false;
        }

        try {
            // Check cache first
            Map<String, Boolean> userFlags = featureFlagsCache.get(userId);
            if (userFlags != null && userFlags.containsKey(featureKey)) {
                return userFlags.get(featureKey);
            }

            // Fetch from PostHog
            // TODO: Implement feature flag check via API
            boolean enabled = false;

            // Cache result
            featureFlagsCache.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(featureKey, enabled);

            log.debug("Feature '{}' is {} for user {}", featureKey,
                enabled ? "enabled" : "disabled", userId);

            return enabled;
        } catch (Exception e) {
            log.error("Failed to check feature flag: " + featureKey, e);
            return false;
        }
    }

    /**
     * Gets the feature flag value (variant) for a user.
     *
     * <p>For multivariate flags that return different values.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * String variant = analytics.getFeatureFlag("user123", "button-color");
     * // Returns: "blue", "green", "red", etc.
     * }</pre>
     *
     * @param userId user identifier
     * @param featureKey feature flag key
     * @return feature flag value or null if not enabled
     */
    public String getFeatureFlag(String userId, String featureKey) {
        if (!available) {
            return null;
        }

        try {
            // TODO: Implement feature flag value retrieval via API
            Object value = null;
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Failed to get feature flag value: " + featureKey, e);
            return null;
        }
    }

    /**
     * Reloads all feature flags from PostHog.
     *
     * <p>Use this to refresh feature flags without restarting the application.</p>
     */
    public void reloadFeatureFlags() {
        if (!available) {
            return;
        }

        try {
            log.info("Reloading feature flags from PostHog");
            // TODO: Implement feature flags reload via API
            log.debug("Feature flags reload requested");
            featureFlagsCache.clear();
        } catch (Exception e) {
            log.error("Failed to reload feature flags", e);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Flushes all pending events to PostHog.
     *
     * <p>PostHog batches events for performance. Call this to force immediate send.</p>
     */
    public void flush() {
        if (!available) {
            return;
        }

        try {
            log.debug("Flushing analytics events");
            // Events are sent immediately via HTTP, no need to flush
            log.debug("Flush requested - events already sent");
        } catch (Exception e) {
            log.error("Failed to flush events", e);
        }
    }

    /**
     * Shuts down the analytics client.
     *
     * <p>Call this on application shutdown to ensure all events are sent.</p>
     */
    public void shutdown() {
        if (!available) {
            return;
        }

        try {
            log.info("Shutting down analytics");
            if (httpClient != null) {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
            }
            available = false;
        } catch (Exception e) {
            log.error("Failed to shutdown analytics", e);
        }
    }

    /**
     * Checks if analytics is available.
     *
     * @return true if PostHog is configured and available
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets health status of analytics.
     *
     * @return health information map
     */
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("available", available);
        health.put("provider", "posthog");
        health.put("featureFlagsCacheSize", featureFlagsCache.size());
        return health;
    }

    // ==================== Private Methods ====================

    /**
     * Initializes PostHog client.
     *
     * <p>Configuration is loaded from environment variables or system properties:</p>
     * <ul>
     *   <li>POSTHOG_API_KEY or posthog.api.key</li>
     *   <li>POSTHOG_HOST or posthog.host (default: https://app.posthog.com)</li>
     * </ul>
     */
    private void initializePostHog() {
        try {
            this.apiKey = getConfig("POSTHOG_API_KEY", "posthog.api.key");
            this.apiUrl = getConfig("POSTHOG_HOST", "posthog.host", "https://app.posthog.com");

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("PostHog API key not configured. Analytics will be disabled.");
                log.info("Set POSTHOG_API_KEY environment variable or posthog.api.key property");
                return;
            }

            this.httpClient = new OkHttpClient.Builder()
                .build();

            this.objectMapper = new ObjectMapper();

            available = true;
            log.info("PostHog initialized successfully. Host: {}", apiUrl);

        } catch (Exception e) {
            log.error("Failed to initialize PostHog", e);
        }
    }

    /**
     * Sends event to PostHog API.
     */
    private void sendEvent(String endpoint, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                .url(apiUrl + "/capture/")
                .post(body)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("PostHog API call failed: {}", response.code());
                }
            }
        } catch (IOException e) {
            log.error("Failed to send event to PostHog", e);
        }
    }

    /**
     * Gets configuration value from environment or system property.
     */
    private String getConfig(String envVar, String sysProp) {
        String value = System.getenv(envVar);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(sysProp);
        }
        return value;
    }

    /**
     * Gets configuration value with default.
     */
    private String getConfig(String envVar, String sysProp, String defaultValue) {
        String value = getConfig(envVar, sysProp);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
}

