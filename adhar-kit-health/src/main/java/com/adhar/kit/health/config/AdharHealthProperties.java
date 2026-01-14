package com.adhar.kit.health.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Adhar Health Check module.
 *
 * <p>Provides comprehensive health check configuration for enterprise microservices:</p>
 * <ul>
 *   <li>Automated health checks for common components (DB, Redis, Kafka, etc.)</li>
 *   <li>Custom health indicators</li>
 *   <li>Health check endpoints</li>
 *   <li>Readiness and liveness probes</li>
 *   <li>Kubernetes-compatible health checks</li>
 * </ul>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   health:
 *     enabled: true
 *     show-details: always
 *     database:
 *       enabled: true
 *       timeout: 5s
 *     redis:
 *       enabled: true
 *       timeout: 3s
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public class AdharHealthProperties {

    /**
     * Enable health checks.
     */
    private boolean enabled = true;

    /**
     * Show health details (never, when-authorized, always).
     */
    private String showDetails = "always";

    /**
     * Show components in health response.
     */
    private boolean showComponents = true;

    /**
     * Health check endpoint path.
     */
    private String endpoint = "/health";

    /**
     * Readiness probe endpoint path.
     */
    private String readinessEndpoint = "/health/ready";

    /**
     * Liveness probe endpoint path.
     */
    private String livenessEndpoint = "/health/live";

    /**
     * Database health check configuration.
     */
    private DatabaseConfig database = new DatabaseConfig();

    /**
     * Redis health check configuration.
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Kafka health check configuration.
     */
    private KafkaConfig kafka = new KafkaConfig();

    /**
     * MongoDB health check configuration.
     */
    private MongoConfig mongo = new MongoConfig();

    /**
     * Elasticsearch health check configuration.
     */
    private ElasticsearchConfig elasticsearch = new ElasticsearchConfig();

    /**
     * gRPC health check configuration.
     */
    private GrpcConfig grpc = new GrpcConfig();

    /**
     * Custom health indicators.
     */
    private Map<String, IndicatorConfig> custom = new HashMap<>();

    /**
     * Database health configuration.
     */
    @Data
    public static class DatabaseConfig {
        private boolean enabled = true;
        private long timeout = 5000; // 5 seconds
        private String validationQuery = "SELECT 1";
    }

    /**
     * Redis health configuration.
     */
    @Data
    public static class RedisConfig {
        private boolean enabled = true;
        private long timeout = 3000; // 3 seconds
    }

    /**
     * Kafka health configuration.
     */
    @Data
    public static class KafkaConfig {
        private boolean enabled = true;
        private long timeout = 5000;
    }

    /**
     * MongoDB health configuration.
     */
    @Data
    public static class MongoConfig {
        private boolean enabled = true;
        private long timeout = 3000;
    }

    /**
     * Elasticsearch health configuration.
     */
    @Data
    public static class ElasticsearchConfig {
        private boolean enabled = true;
        private long timeout = 3000;
    }

    /**
     * gRPC health configuration.
     */
    @Data
    public static class GrpcConfig {
        private boolean enabled = true;
        private long timeout = 3000;
    }

    /**
     * Custom indicator configuration.
     */
    @Data
    public static class IndicatorConfig {
        private boolean enabled = true;
        private long timeout = 5000;
        private Map<String, String> properties = new HashMap<>();
    }
}

