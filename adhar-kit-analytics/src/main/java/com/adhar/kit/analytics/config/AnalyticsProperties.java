package com.adhar.kit.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Adhar Analytics module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.analytics")
public class AnalyticsProperties {

    /**
     * Enable/disable analytics features.
     */
    private boolean enabled = true;

    /**
     * Event tracking settings.
     */
    private EventTracking eventTracking = new EventTracking();

    /**
     * Report generation settings.
     */
    private Reporting reporting = new Reporting();

    /**
     * Aggregation settings.
     */
    private Aggregation aggregation = new Aggregation();

    @Data
    public static class EventTracking {
        private boolean enabled = true;
        private String kafkaTopic = "analytics-events";
        private boolean trackPageViews = true;
        private boolean trackUserActions = true;
        private boolean trackBusinessEvents = true;
        private int batchSize = 100;
        private long flushInterval = 5000; // 5 seconds
    }

    @Data
    public static class Reporting {
        private boolean enabled = true;
        private String outputDirectory = "/tmp/reports";
        private boolean enableCsv = true;
        private boolean enableExcel = true;
        private boolean enablePdf = false;
        private boolean enableScheduledReports = false;
        private String scheduleExpression = "0 0 0 * * ?"; // Daily at midnight
    }

    @Data
    public static class Aggregation {
        private boolean enabled = true;
        private boolean realTimeAggregation = true;
        private int aggregationWindowMinutes = 5;
        private boolean persistAggregates = true;
    }
}

