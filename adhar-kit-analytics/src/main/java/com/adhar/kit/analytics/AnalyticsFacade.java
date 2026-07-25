package com.adhar.kit.analytics;

import com.adhar.kit.analytics.batching.BatchingEventSender;
import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.DecideResult;
import com.adhar.kit.analytics.client.OkHttpPostHogClient;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import com.adhar.kit.analytics.consent.ConsentGateway;
import com.adhar.kit.analytics.consent.InMemoryConsentStore;
import com.adhar.kit.analytics.flag.FeatureFlagCache;
import com.adhar.kit.analytics.pii.PiiScrubber;
import com.adhar.kit.commons.event.AdharCloudEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * <p><b>Batching</b>: events are buffered in a bounded, in-memory queue and
 * flushed to PostHog's {@code /batch/} endpoint either when
 * {@code adhar.analytics.post-hog.batch-size} is reached or every
 * {@code adhar.analytics.post-hog.flush-interval} seconds, whichever comes
 * first. Set {@code adhar.analytics.post-hog.batching-enabled=false} to fall
 * back to sending every event synchronously via {@code /capture/}.</p>
 *
 * <p><b>Consent &amp; PII</b>: every send path is gated by a {@link ConsentGateway}
 * (per-distinct-id opt-out) and scrubbed by a {@link PiiScrubber} (configured
 * redaction keys plus obvious PII patterns) before anything reaches PostHog.</p>
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
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AnalyticsFacade {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 10;
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final long DEFAULT_FLAG_TTL_SECONDS = 60;

    /**
     * Singleton instance (thread-safe lazy initialization).
     */
    private static volatile AnalyticsFacade instance;

    /**
     * PostHog API configuration.
     */
    private String apiKey;
    private String apiUrl;

    /**
     * PostHog HTTP abstraction (correct, typed /capture, /batch, /decide routing).
     */
    private volatile PostHogClient postHogClient;

    /**
     * Async batching sender; {@code null} when the synchronous fallback is in effect.
     */
    private volatile BatchingEventSender batchingSender;

    private volatile boolean batchingEnabled;

    /**
     * Per-distinct-id opt-out gate, consulted before every send.
     */
    private volatile ConsentGateway consentGateway;

    /**
     * Redacts configured/obvious PII from event properties before send.
     */
    private volatile PiiScrubber piiScrubber;

    /**
     * TTL-based feature flag decision cache.
     */
    private volatile FeatureFlagCache featureFlagCache;

    /**
     * Indicates if analytics is available and configured.
     */
    private volatile boolean available = false;

    /**
     * Private constructor for singleton pattern.
     */
    private AnalyticsFacade() {
        log.info("Initializing Analytics Facade v1.0.0 (PostHog)");
        initializePostHog();
    }

    /**
     * Package-private constructor for tests/DI: builds a fully-configured,
     * standalone instance (not the shared singleton) around an injected
     * {@link PostHogClient}, so batching/consent/PII/TTL behavior can be unit
     * tested without a real network call or singleton state bleed across tests.
     */
    AnalyticsFacade(PostHogClient postHogClient,
                     boolean batchingEnabled,
                     int batchSize,
                     Duration flushInterval,
                     int queueCapacity,
                     AnalyticsProperties.OverflowPolicy overflowPolicy,
                     Duration featureFlagTtl,
                     Clock clock,
                     ConsentGateway consentGateway,
                     PiiScrubber piiScrubber) {
        this.postHogClient = postHogClient;
        this.consentGateway = consentGateway != null ? consentGateway : new ConsentGateway(new InMemoryConsentStore());
        this.piiScrubber = piiScrubber != null ? piiScrubber : new PiiScrubber(Set.of(), true);
        this.featureFlagCache = new FeatureFlagCache(featureFlagTtl, clock);
        this.batchingEnabled = batchingEnabled;
        this.batchingSender = batchingEnabled
                ? new BatchingEventSender(postHogClient, batchSize, flushInterval, queueCapacity, overflowPolicy)
                : null;
        this.available = true;
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

    /**
     * Applies Spring Boot configuration ({@link AnalyticsProperties}) to the
     * singleton, overriding the environment-variable-based defaults picked up
     * at construction time. Invoked once by {@code AnalyticsAutoConfiguration}.
     * Any previously buffered events are flushed before the batching sender is
     * rebuilt with the new settings.
     *
     * @param properties the bound Spring configuration properties
     */
    public synchronized void configure(AnalyticsProperties properties) {
        if (properties == null) {
            return;
        }
        AnalyticsProperties.PostHog postHogProps = properties.getPostHog();

        String configuredKey = postHogProps.getApiKey();
        if (configuredKey != null && !configuredKey.isBlank()) {
            this.apiKey = configuredKey;
            String configuredHost = postHogProps.getHost();
            if (configuredHost != null && !configuredHost.isBlank()) {
                this.apiUrl = configuredHost;
            }
            PostHogClient newClient = new OkHttpPostHogClient(
                    new OkHttpClient.Builder().build(), new ObjectMapper(), apiKey, apiUrl);
            if (this.postHogClient != null) {
                this.postHogClient.close();
            }
            this.postHogClient = newClient;
            this.available = true;
        }

        if (this.batchingSender != null) {
            this.batchingSender.shutdown();
        }
        this.batchingEnabled = postHogProps.isBatchingEnabled();
        this.batchingSender = (this.batchingEnabled && this.postHogClient != null)
                ? new BatchingEventSender(
                        this.postHogClient,
                        postHogProps.getBatchSize(),
                        Duration.ofSeconds(postHogProps.getFlushInterval()),
                        postHogProps.getQueueCapacity(),
                        postHogProps.getOverflowPolicy())
                : null;

        this.featureFlagCache = new FeatureFlagCache(
                Duration.ofSeconds(postHogProps.getFeatureFlagCacheTtlSeconds()), Clock.systemUTC());
        this.consentGateway = new ConsentGateway(new InMemoryConsentStore(properties.getConsent().getOptedOutIds()));
        this.piiScrubber = new PiiScrubber(properties.getPii().getRedactedKeys(), properties.getPii().isPatternDetectionEnabled());
    }

    // ==================== Event Tracking ====================

    /**
     * Tracks an event for a user.
     *
     * <p>Events are the foundation of analytics. Use them to track any action
     * a user takes in your application.</p>
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
        if (userId == null || event == null) {
            log.debug("Skipping track(): distinct id or event name is null");
            return;
        }
        if (!consentGateway.isAllowed(userId)) {
            return;
        }

        try {
            Map<String, Object> scrubbed = piiScrubber.scrub(properties);
            send(CaptureEvent.of(event, userId, scrubbed));
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

    // ==================== CloudEvent Tracking ====================

    /**
     * Tracks an analytics event and returns it wrapped in a CloudEvent envelope.
     *
     * @param userId     user identifier
     * @param event      event name (e.g., "Button Clicked", "Purchase")
     * @param properties event properties (metadata)
     * @return an {@link AdharCloudEvent} wrapping the analytics event data, or {@code null} if analytics is unavailable
     */
    public AdharCloudEvent<Map<String, Object>> trackAsCloudEvent(String userId, String event, Map<String, Object> properties) {
        // Delegate to standard tracking
        track(userId, event, properties);

        // Build CloudEvent envelope
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("userId", userId);
        eventData.put("event", event);
        eventData.put("properties", properties != null ? properties : Map.of());

        AdharCloudEvent<Map<String, Object>> cloudEvent = AdharCloudEvent.of(
                "adhar-kit/analytics",
                "com.adhar.analytics.tracked",
                userId,
                eventData
        );

        log.debug("Created CloudEvent [type={}, subject={}] for analytics event '{}'",
                cloudEvent.type(), cloudEvent.subject(), event);

        return cloudEvent;
    }

    // ==================== User Identification ====================

    /**
     * Identifies a user with properties.
     *
     * <p>Builds a PostHog {@code $identify} event (the real PostHog protocol
     * for identify - there is no separate {@code /identify} REST endpoint)
     * with the given properties under {@code $set}, and routes it through the
     * same capture/batch pipeline as regular events.</p>
     *
     * @param userId user identifier
     * @param properties user properties (use $-prefixed keys for special properties)
     */
    public void identify(String userId, Map<String, Object> properties) {
        if (!available) {
            log.warn("Analytics not available, skipping identify");
            return;
        }
        if (userId == null) {
            return;
        }
        if (!consentGateway.isAllowed(userId)) {
            return;
        }

        try {
            log.debug("Identifying user: {}", userId);
            Map<String, Object> scrubbed = piiScrubber.scrub(properties);
            Map<String, Object> payload = new HashMap<>();
            payload.put("$set", scrubbed);
            send(CaptureEvent.of("$identify", userId, payload));
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
     * <p>Builds a PostHog {@code $create_alias} event - the real PostHog
     * protocol for aliasing - and routes it through capture/batch.</p>
     *
     * @param distinctId the current user ID
     * @param alias the ID to alias to
     */
    public void alias(String distinctId, String alias) {
        if (!available) {
            log.warn("Analytics not available, skipping alias");
            return;
        }
        if (distinctId == null || alias == null) {
            return;
        }
        if (!consentGateway.isAllowed(distinctId)) {
            return;
        }

        try {
            log.debug("Aliasing {} to {}", distinctId, alias);
            Map<String, Object> payload = new HashMap<>();
            payload.put("alias", alias);
            send(CaptureEvent.of("$create_alias", distinctId, payload));
        } catch (Exception e) {
            log.error("Failed to alias user", e);
        }
    }

    // ==================== Group Analytics ====================

    /**
     * Associates a user with a group (company, organization, etc.).
     *
     * <p>Builds a PostHog {@code $groupidentify} event - the real PostHog
     * protocol for group analytics - and routes it through capture/batch.</p>
     *
     * @param userId user identifier
     * @param groupId group identifier
     * @param properties group properties (an explicit {@code $group_type} entry
     *                    is honored; otherwise defaults to {@code "company"})
     */
    public void group(String userId, String groupId, Map<String, Object> properties) {
        if (!available) {
            log.warn("Analytics not available, skipping group");
            return;
        }
        if (userId == null || groupId == null) {
            return;
        }
        if (!consentGateway.isAllowed(userId)) {
            return;
        }

        try {
            log.debug("Adding user {} to group {}", userId, groupId);
            Map<String, Object> scrubbed = new HashMap<>(piiScrubber.scrub(properties));
            Object groupType = scrubbed.remove("$group_type");
            Map<String, Object> payload = new HashMap<>(scrubbed);
            payload.put("$group_type", groupType != null ? groupType : "company");
            payload.put("$group_key", groupId);
            send(CaptureEvent.of("$groupidentify", userId, payload));
        } catch (Exception e) {
            log.error("Failed to add user to group", e);
        }
    }

    // ==================== Feature Flags ====================

    /**
     * Checks if a feature flag is enabled for a user.
     *
     * <p>Results are cached per user/flag with a configurable TTL
     * ({@code adhar.analytics.post-hog.feature-flag-cache-ttl-seconds},
     * default 60s) so repeated checks don't call PostHog's {@code /decide}
     * endpoint on every request, while still refreshing periodically instead
     * of serving a decision forever.</p>
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
            Optional<FeatureFlagCache.CachedFlag> cached = featureFlagCache.get(userId, featureKey);
            if (cached.isPresent()) {
                return cached.get().enabled();
            }

            DecideResult result = fetchAndCacheDecision(userId);
            if (!result.featureFlags().containsKey(featureKey)) {
                // Explicitly cache "disabled" so we don't re-call /decide for this
                // key on every request until the TTL expires.
                featureFlagCache.put(userId, featureKey, null, false);
                return false;
            }

            boolean enabled = featureFlagCache.get(userId, featureKey).map(FeatureFlagCache.CachedFlag::enabled).orElse(false);
            log.debug("Feature '{}' is {} for user {}", featureKey, enabled ? "enabled" : "disabled", userId);
            return enabled;
        } catch (Exception e) {
            log.error("Failed to check feature flag: " + featureKey, e);
            return false;
        }
    }

    /**
     * Gets the feature flag value (variant) for a user.
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
            Optional<FeatureFlagCache.CachedFlag> cached = featureFlagCache.get(userId, featureKey);
            if (cached.isPresent()) {
                Object value = cached.get().value();
                return value != null ? value.toString() : null;
            }

            // Populates the cache as a side effect.
            isFeatureEnabled(userId, featureKey);

            return featureFlagCache.get(userId, featureKey)
                    .map(FeatureFlagCache.CachedFlag::value)
                    .map(Object::toString)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Failed to get feature flag value: " + featureKey, e);
            return null;
        }
    }

    /**
     * Reloads all feature flags from PostHog by clearing the TTL cache; the
     * next access for any user/flag will re-fetch from {@code /decide}.
     */
    public void reloadFeatureFlags() {
        if (!available) {
            return;
        }
        try {
            log.info("Reloading feature flags from PostHog");
            featureFlagCache.clear();
            log.info("Feature flags cache cleared - flags will be fetched on next access");
        } catch (Exception e) {
            log.error("Failed to reload feature flags", e);
        }
    }

    /**
     * Preloads feature flags for a specific user.
     *
     * @param userId user identifier
     */
    public void preloadFeatureFlags(String userId) {
        if (!available) {
            return;
        }
        try {
            DecideResult result = fetchAndCacheDecision(userId);
            log.debug("Preloaded {} feature flags for user {}", result.featureFlags().size(), userId);
        } catch (Exception e) {
            log.error("Failed to preload feature flags for user: " + userId, e);
        }
    }

    private DecideResult fetchAndCacheDecision(String userId) {
        DecideResult result = postHogClient.decide(userId);
        for (Map.Entry<String, Object> entry : result.featureFlags().entrySet()) {
            Object value = entry.getValue();
            featureFlagCache.put(userId, entry.getKey(), value, interpretEnabled(value));
        }
        return result;
    }

    private static boolean interpretEnabled(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        } else if (value instanceof String str) {
            return !str.isEmpty() && !"false".equalsIgnoreCase(str);
        }
        return value != null;
    }

    // ==================== Utility Methods ====================

    /**
     * Flushes all pending batched events to PostHog immediately.
     */
    public void flush() {
        if (!available) {
            return;
        }
        try {
            log.debug("Flushing analytics events");
            if (batchingEnabled && batchingSender != null) {
                batchingSender.flush();
            }
        } catch (Exception e) {
            log.error("Failed to flush events", e);
        }
    }

    /**
     * Shuts down the analytics client.
     *
     * <p>Stops the scheduled batch flush and performs a final, synchronous
     * flush so buffered events are not lost.</p>
     */
    public void shutdown() {
        if (!available) {
            return;
        }
        try {
            log.info("Shutting down analytics");
            if (batchingSender != null) {
                batchingSender.shutdown();
            }
            if (postHogClient != null) {
                postHogClient.close();
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
        health.put("featureFlagsCacheSize", featureFlagCache != null ? featureFlagCache.userCount() : 0);
        health.put("pendingEvents", batchingSender != null ? batchingSender.pendingCount() : 0);
        health.put("droppedEvents", batchingSender != null ? batchingSender.droppedCount() : 0);
        return health;
    }

    /**
     * Gets the PII scrubber used by this facade, so callers (e.g. aspects
     * building event properties from method parameters) can redact sensitive
     * data before it is even assembled into a properties map.
     */
    public PiiScrubber getPiiScrubber() {
        return piiScrubber;
    }

    /**
     * Gets the consent gateway used by this facade.
     */
    public ConsentGateway getConsentGateway() {
        return consentGateway;
    }

    // ==================== Private Methods ====================

    /**
     * Sends a single event either through the async batch queue or, if
     * batching is disabled, synchronously via {@code /capture/}.
     */
    private void send(CaptureEvent event) {
        if (batchingEnabled && batchingSender != null) {
            batchingSender.enqueue(event);
        } else {
            postHogClient.capture(event);
        }
    }

    /**
     * Initializes PostHog client.
     *
     * <p>Configuration is loaded from environment variables or system properties:</p>
     * <ul>
     *   <li>POSTHOG_API_KEY or posthog.api.key</li>
     *   <li>POSTHOG_HOST or posthog.host (default: https://app.posthog.com)</li>
     * </ul>
     *
     * <p>This env-var-based configuration is used when the facade is
     * constructed outside of a Spring context. When Spring is present,
     * {@code AnalyticsAutoConfiguration} calls {@link #configure(AnalyticsProperties)}
     * to apply {@code adhar.analytics.*} properties, which take precedence.</p>
     */
    private void initializePostHog() {
        this.consentGateway = new ConsentGateway(new InMemoryConsentStore());
        this.piiScrubber = new PiiScrubber(Set.of(
                "password", "passwd", "secret", "token", "apikey", "api_key",
                "ssn", "creditcard", "credit_card"), true);
        this.featureFlagCache = new FeatureFlagCache(Duration.ofSeconds(DEFAULT_FLAG_TTL_SECONDS), Clock.systemUTC());
        this.batchingEnabled = true;

        try {
            this.apiKey = getConfig("POSTHOG_API_KEY", "posthog.api.key");
            this.apiUrl = getConfig("POSTHOG_HOST", "posthog.host", "https://app.posthog.com");

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("PostHog API key not configured. Analytics will be disabled.");
                log.info("Set POSTHOG_API_KEY environment variable or posthog.api.key property");
                return;
            }

            this.postHogClient = new OkHttpPostHogClient(
                    new OkHttpClient.Builder().build(), new ObjectMapper(), apiKey, apiUrl);
            this.batchingSender = new BatchingEventSender(
                    postHogClient, DEFAULT_BATCH_SIZE, Duration.ofSeconds(DEFAULT_FLUSH_INTERVAL_SECONDS),
                    DEFAULT_QUEUE_CAPACITY, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

            available = true;
            log.info("PostHog initialized successfully. Host: {}", apiUrl);

        } catch (Exception e) {
            log.error("Failed to initialize PostHog", e);
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
