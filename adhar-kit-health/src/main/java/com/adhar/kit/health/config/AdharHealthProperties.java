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
     * Health result cache configuration.
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * Heap memory health check configuration.
     */
    private MemoryConfig memory = new MemoryConfig();

    /**
     * Thread pool health check configuration.
     */
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    /**
     * Certificate expiry health check configuration.
     */
    private CertificateConfig certificate = new CertificateConfig();

    /**
     * Readiness gate (graceful shutdown) configuration.
     */
    private ReadinessGateConfig readinessGate = new ReadinessGateConfig();

    /**
     * Health transition history / flapping detection configuration.
     */
    private HistoryConfig history = new HistoryConfig();

    /**
     * Weighted aggregation configuration.
     */
    private WeightedConfig weighted = new WeightedConfig();

    /**
     * Circuit-breaker health check configuration.
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * Health transition event stream configuration.
     */
    private EventsConfig events = new EventsConfig();

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
     * Health result cache configuration.
     */
    @Data
    public static class CacheConfig {
        /** Enable TTL caching of health results. */
        private boolean enabled = true;
        /** Cache TTL in milliseconds (0 = disabled). */
        private long ttl = 10_000;
    }

    /**
     * Heap memory health configuration.
     */
    @Data
    public static class MemoryConfig {
        private boolean enabled = true;
        /** Heap usage ratio [0..1] at which memory is DOWN. */
        private double threshold = 0.90;
    }

    /**
     * Thread pool health configuration.
     */
    @Data
    public static class ThreadPoolConfig {
        private boolean enabled = true;
        /** Queue usage ratio [0..1] at which the pool is DOWN. */
        private double queueUsageThreshold = 0.90;
    }

    /**
     * Certificate expiry health configuration.
     */
    @Data
    public static class CertificateConfig {
        private boolean enabled = false;
        /** Keystore path (keystore mode when set). */
        private String keystorePath;
        private String keystorePassword;
        private String keystoreType = "PKCS12";
        /** TLS endpoint host (endpoint mode when keystore path is unset). */
        private String host;
        private int port = 443;
        /** Report DOWN when expiry is within this many days. */
        private int warningDays = 30;
    }

    /**
     * Readiness gate configuration.
     */
    @Data
    public static class ReadinessGateConfig {
        private boolean enabled = true;
        /** Whether the gate starts open (ready) before lifecycle events fire. */
        private boolean initiallyReady = false;
        /** Register a JVM shutdown hook that closes the gate. */
        private boolean shutdownHook = true;
    }

    /**
     * Health transition history and flapping detection configuration.
     */
    @Data
    public static class HistoryConfig {
        /** Maximum retained health transitions. */
        private int capacity = 100;
        /** Number of transitions within the window considered flapping. */
        private int flappingThreshold = 5;
        /** Flapping detection window in milliseconds. */
        private long flappingWindow = 60_000;
    }

    /**
     * Weighted aggregation configuration.
     *
     * <p>When {@code weights} assigns a positive weight to any indicator, a group's
     * status is derived from a weighted health score rather than the classic
     * critical/non-critical rule. Score {@code s} in {@code [0..1]}:
     * {@code s >= upThreshold} &rarr; UP, {@code s <= downThreshold} &rarr; DOWN,
     * otherwise the degraded band (OUT_OF_SERVICE).</p>
     */
    @Data
    public static class WeightedConfig {
        /** Score at or above which a group is UP. */
        private double upThreshold = 1.0;
        /** Score at or below which a group is DOWN. */
        private double downThreshold = 0.0;
        /** Per-indicator weights, keyed by indicator name (0 or absent = unweighted). */
        private Map<String, Double> weights = new HashMap<>();
    }

    /**
     * Circuit-breaker health configuration.
     */
    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        /** Treat HALF_OPEN breakers as degraded (OUT_OF_SERVICE) rather than UP. */
        private boolean treatHalfOpenAsDegraded = true;
    }

    /**
     * Health transition event stream configuration.
     */
    @Data
    public static class EventsConfig {
        /** Publish a Spring ApplicationEvent on each transition. */
        private boolean enabled = true;
        /** Expose the Server-Sent Events stream (requires Spring Web MVC). */
        private boolean sseEnabled = true;
        /** SSE endpoint path. */
        private String ssePath = "/health/events";
        /** SSE connection timeout in milliseconds (0 = container default / no timeout). */
        private long sseTimeout = 0;
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

