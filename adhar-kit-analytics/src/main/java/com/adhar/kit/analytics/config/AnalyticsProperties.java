package com.adhar.kit.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration properties for Analytics module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "adhar.analytics")
public class AnalyticsProperties {

    /**
     * Enable analytics module.
     */
    private boolean enabled = true;

    /**
     * PostHog configuration.
     */
    private PostHog postHog = new PostHog();

    /**
     * Annotation support.
     */
    private Annotations annotations = new Annotations();

    /**
     * Event tracking configuration.
     */
    private EventTracking eventTracking = new EventTracking();

    /**
     * Consent / opt-out governance configuration.
     */
    private Consent consent = new Consent();

    /**
     * PII scrubbing configuration.
     */
    private Pii pii = new Pii();

    @Data
    public static class PostHog {
        /**
         * PostHog API key.
         */
        private String apiKey;

        /**
         * PostHog host URL.
         */
        private String host = "https://app.posthog.com";

        /**
         * Enable personal API key for feature flags.
         */
        private String personalApiKey;

        /**
         * Batch size for events. Also used as the chunk size when draining the
         * queue so a single HTTP call never carries more than this many events.
         */
        private int batchSize = 100;

        /**
         * Flush interval in seconds for the background batch sender.
         */
        private int flushInterval = 10;

        /**
         * Enable real async batching via {@link com.adhar.kit.analytics.batching.BatchingEventSender}.
         * When {@code false}, events are sent synchronously one-by-one (legacy fallback path).
         */
        private boolean batchingEnabled = true;

        /**
         * Maximum number of events buffered in memory awaiting flush.
         */
        private int queueCapacity = 10_000;

        /**
         * Behavior when the in-memory queue is full.
         */
        private OverflowPolicy overflowPolicy = OverflowPolicy.DROP_OLDEST;

        /**
         * Time-to-live, in seconds, for cached feature flag decisions before
         * they are re-fetched from the PostHog {@code /decide} endpoint.
         */
        private long featureFlagCacheTtlSeconds = 60;

        /**
         * Enable local flag evaluation via
         * {@link com.adhar.kit.analytics.flag.LocalFlagEvaluator}. When on, flags
         * with cached local definitions are evaluated in-process (rollout % +
         * property conditions), falling back to {@code /decide} when a flag
         * cannot be decided locally. Off by default.
         */
        private boolean localEvaluationEnabled = false;

        /**
         * Enable the failed-batch retry buffer in
         * {@link com.adhar.kit.analytics.batching.BatchingEventSender}. When off,
         * batches that fail to send are logged and dropped (legacy behaviour).
         */
        private boolean retryEnabled = true;

        /**
         * Maximum delivery attempts for a failed batch before it is spilled or dropped.
         */
        private int retryMaxAttempts = 3;

        /**
         * Backoff before the first retry, in milliseconds.
         */
        private long retryInitialBackoffMillis = 500;

        /**
         * Exponential backoff multiplier applied per retry attempt.
         */
        private double retryBackoffMultiplier = 2.0;

        /**
         * Maximum backoff between retries, in milliseconds.
         */
        private long retryMaxBackoffMillis = 30_000;

        /**
         * Maximum number of failed batches held in the in-memory retry queue.
         */
        private int retryMaxBatches = 1_000;

        /**
         * Enable the optional file-based offline spill: batches that exhaust
         * retries (or overflow the retry queue) are persisted as JSONL and
         * reloaded on startup. Off by default.
         */
        private boolean spillEnabled = false;

        /**
         * Directory for the offline spill file. Required when
         * {@code spillEnabled} is true.
         */
        private String spillDirectory;
    }

    /**
     * Overflow behavior for the bounded event batch queue.
     */
    public enum OverflowPolicy {
        /** Drop the oldest queued event to make room for the newest one. */
        DROP_OLDEST,
        /** Block the calling thread until space is available. */
        BLOCK
    }

    @Data
    public static class Consent {
        /**
         * Distinct IDs that have opted out of analytics tracking. Events for
         * these IDs are suppressed before ever reaching PostHog.
         */
        private List<String> optedOutIds = List.of();
    }

    @Data
    public static class Pii {
        /**
         * Property keys (case-insensitive) whose values are always redacted
         * before being sent to PostHog.
         */
        private Set<String> redactedKeys = new LinkedHashSet<>(List.of(
                "password", "passwd", "secret", "token", "apikey", "api_key",
                "ssn", "creditcard", "credit_card", "cardnumber", "card_number"
        ));

        /**
         * Also redact string values that look like obvious PII (emails, SSNs,
         * credit card numbers, phone numbers) regardless of their property key.
         */
        private boolean patternDetectionEnabled = true;
    }

    @Data
    public static class Annotations {
        /**
         * Enable annotation processing.
         */
        private boolean enabled = true;

        /**
         * Enable @TrackEvent annotation.
         */
        private boolean trackEventEnabled = true;

        /**
         * Enable @TrackPageView annotation.
         */
        private boolean trackPageViewEnabled = true;

        /**
         * Enable @IdentifyUser annotation.
         */
        private boolean identifyUserEnabled = true;

        /**
         * Enable @FeatureFlag annotation.
         */
        private boolean featureFlagEnabled = true;

        /**
         * Enable @TrackGroup annotation.
         */
        private boolean trackGroupEnabled = true;

        /**
         * Enable @AliasUser annotation.
         */
        private boolean aliasUserEnabled = true;

        /**
         * Enable @EnableAnalytics annotation.
         */
        private boolean enableAnalyticsEnabled = true;

        /**
         * Enable @TrackSession annotation.
         */
        private boolean trackSessionEnabled = true;
    }

    @Data
    public static class EventTracking {
        /**
         * Enable automatic event tracking.
         */
        private boolean enabled = true;

        /**
         * Track HTTP requests automatically.
         */
        private boolean trackHttpRequests = false;

        /**
         * Track exceptions automatically.
         */
        private boolean trackExceptions = true;

        /**
         * Async event tracking.
         */
        private boolean async = true;
    }
}

