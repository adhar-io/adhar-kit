package com.adhar.kit.metrics.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Adhar Metrics Starter.
 * <p>
 * This class provides configuration options for both Prometheus and OpenTelemetry metrics,
 * allowing for flexible and comprehensive metrics collection in enterprise applications.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "adhar.metrics")
public class AdharMetricsProperties {

    /**
     * Whether metrics collection is enabled.
     */
    private boolean enabled = true;

    /**
     * Common tags to be applied to all metrics.
     */
    private Map<String, String> commonTags = new HashMap<>();

    /**
     * Configuration for Prometheus metrics.
     */
    private PrometheusProperties prometheus = new PrometheusProperties();

    /**
     * Configuration for OpenTelemetry metrics.
     */
    private OpenTelemetryProperties openTelemetry = new OpenTelemetryProperties();

    /**
     * Configuration for controller metrics (HTTP requests, etc.).
     */
    private WebMetricsProperties web = new WebMetricsProperties();

    /**
     * Configuration for JVM metrics.
     */
    private JvmMetricsProperties jvm = new JvmMetricsProperties();

    /**
     * Configuration for system metrics (CPU, memory, etc.).
     */
    private SystemMetricsProperties system = new SystemMetricsProperties();

    /**
     * Configuration for custom application metrics.
     */
    private ApplicationMetricsProperties application = new ApplicationMetricsProperties();

    /**
     * Configuration for Kubernetes-specific metrics.
     */
    private KubernetesProperties kubernetes = new KubernetesProperties();

    /**
     * Configuration properties for Prometheus metrics.
     */
    @Data
    public static class PrometheusProperties {

        /**
         * Whether Prometheus metrics are enabled.
         */
        private boolean enabled = true;

        /**
         * Prometheus metrics endpoint path.
         */
        private String endpoint = "/actuator/prometheus";

        /**
         * Whether to include description in metrics output.
         */
        private boolean descriptions = true;

        /**
         * Step size for Prometheus metrics (duration between pushes).
         */
        private String step = "PT1M";

        /**
         * Custom Prometheus configuration.
         */
        private Map<String, String> config = new HashMap<>();
    }

    /**
     * Configuration properties for OpenTelemetry metrics.
     */
    @Data
    public static class OpenTelemetryProperties {

        /**
         * Whether OpenTelemetry metrics are enabled.
         */
        private boolean enabled = false;

        /**
         * OpenTelemetry endpoint URL.
         */
        private String endpoint = "http://localhost:4318/v1/metrics";

        /**
         * Resource attributes for OpenTelemetry.
         */
        private Map<String, String> resourceAttributes = new HashMap<>();

        /**
         * Export timeout for metrics.
         */
        private String timeout = "PT30S";

        /**
         * Export interval for metrics.
         */
        private String interval = "PT30S";
    }

    /**
     * Configuration properties for controller metrics.
     */
    @Data
    public static class WebMetricsProperties {

        /**
         * Whether controller metrics are enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to record request size metrics.
         */
        private boolean recordRequestSize = true;

        /**
         * Whether to record response size metrics.
         */
        private boolean recordResponseSize = true;

        /**
         * Maximum number of URI tags to create.
         */
        private int maxUriTags = 100;

        /**
         * Patterns to ignore for metrics collection.
         */
        private String[] ignorePatterns = {"/actuator/**", "/health/**", "/info/**"};
    }

    /**
     * Configuration properties for JVM metrics.
     */
    @Data
    public static class JvmMetricsProperties {

        /**
         * Whether JVM metrics are enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to collect memory metrics.
         */
        private boolean memory = true;

        /**
         * Whether to collect GC metrics.
         */
        private boolean gc = true;

        /**
         * Whether to collect thread metrics.
         */
        private boolean threads = true;

        /**
         * Whether to collect class loader metrics.
         */
        private boolean classLoader = true;
    }

    /**
     * Configuration properties for system metrics.
     */
    @Data
    public static class SystemMetricsProperties {

        /**
         * Whether system metrics are enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to collect CPU metrics.
         */
        private boolean processor = true;

        /**
         * Whether to collect file descriptor metrics.
         */
        private boolean fileDescriptor = true;

        /**
         * Whether to collect uptime metrics.
         */
        private boolean uptime = true;

        /**
         * Whether to collect disk space metrics.
         */
        private boolean diskSpace = true;
    }

    /**
     * Configuration properties for application-specific metrics.
     */
    @Data
    public static class ApplicationMetricsProperties {

        /**
         * Whether application metrics are enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to enable method-level timing metrics.
         */
        private boolean methodTiming = true;

        /**
         * Whether to enable exception counting metrics.
         */
        private boolean exceptionCounting = true;

        /**
         * Whether to enable cache metrics.
         */
        private boolean cache = true;

        /**
         * Whether to enable database metrics.
         */
        private boolean database = true;
    }

    /**
     * Configuration properties for Kubernetes-specific metrics.
     */
    @Data
    public static class KubernetesProperties {

        /**
         * Whether Kubernetes metrics are enabled.
         */
        private boolean enabled = false;

        /**
         * Whether to include pod information in metrics.
         */
        private boolean includePodInfo = true;

        /**
         * Whether to include namespace information in metrics.
         */
        private boolean includeNamespace = true;

        /**
         * Whether to include node information in metrics.
         */
        private boolean includeNodeInfo = true;

        /**
         * Custom Kubernetes labels to include as tags.
         */
        private String[] customLabels = {};
    }
}
