package com.adhar.kit.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
         * Batch size for events.
         */
        private int batchSize = 100;

        /**
         * Flush interval in seconds.
         */
        private int flushInterval = 10;
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

