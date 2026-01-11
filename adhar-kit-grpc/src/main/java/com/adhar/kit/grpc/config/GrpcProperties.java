package com.adhar.kit.grpc.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for gRPC server and client.
 *
 * <p>Provides comprehensive configuration for enterprise gRPC services:</p>
 * <ul>
 *   <li>Server configuration (port, security, interceptors)</li>
 *   <li>Client configuration (targets, load balancing, retries)</li>
 *   <li>Security settings (TLS, authentication)</li>
 *   <li>Observability (metrics, tracing, logging)</li>
 *   <li>Performance tuning (threads, timeouts, limits)</li>
 * </ul>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   grpc:
 *     server:
 *       enabled: true
 *       port: 9090
 *       security:
 *         enabled: true
 *         cert-chain: classpath:certs/server.crt
 *         private-key: classpath:certs/server.key
 *     client:
 *       channels:
 *         order-service:
 *           target: localhost:9090
 *           enable-retry: true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public class GrpcProperties {

    /**
     * Enable gRPC support.
     */
    private boolean enabled = true;

    /**
     * Server configuration.
     */
    private ServerConfig server = new ServerConfig();

    /**
     * Client configuration.
     */
    private ClientConfig client = new ClientConfig();

    /**
     * Security configuration.
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * Observability configuration.
     */
    private ObservabilityConfig observability = new ObservabilityConfig();

    /**
     * Server configuration.
     */
    @Data
    public static class ServerConfig {
        /**
         * Enable gRPC server.
         */
        private boolean enabled = true;

        /**
         * Server port.
         */
        private int port = 9090;

        /**
         * Server address to bind.
         */
        private String address = "0.0.0.0";

        /**
         * Maximum message size (bytes).
         */
        private int maxInboundMessageSize = 4 * 1024 * 1024; // 4MB

        /**
         * Maximum header list size (bytes).
         */
        private int maxInboundHeaderListSize = 8192;

        /**
         * Executor thread pool size.
         */
        private int executorThreadPoolSize = 100;

        /**
         * Keep alive time (seconds).
         */
        private int keepAliveTime = 300;

        /**
         * Keep alive timeout (seconds).
         */
        private int keepAliveTimeout = 20;

        /**
         * Permit keep alive without calls.
         */
        private boolean permitKeepAliveWithoutCalls = false;

        /**
         * Permit keep alive time (seconds).
         */
        private int permitKeepAliveTime = 300;

        /**
         * Enable reflection service.
         */
        private boolean enableReflection = true;

        /**
         * Enable health check service.
         */
        private boolean enableHealthCheck = true;

        /**
         * Shutdown grace period (seconds).
         */
        private int shutdownGracePeriod = 30;
    }

    /**
     * Client configuration.
     */
    @Data
    public static class ClientConfig {
        /**
         * Default target.
         */
        private String defaultTarget = "localhost:9090";

        /**
         * Named channels configuration.
         */
        private Map<String, ChannelConfig> channels = new HashMap<>();

        /**
         * Default channel configuration.
         */
        private ChannelConfig defaults = new ChannelConfig();
    }

    /**
     * Channel configuration.
     */
    @Data
    public static class ChannelConfig {
        /**
         * Target address (host:port).
         */
        private String target;

        /**
         * Enable load balancing.
         */
        private boolean enableLoadBalancing = true;

        /**
         * Load balancing policy (round_robin, pick_first).
         */
        private String loadBalancingPolicy = "round_robin";

        /**
         * Enable retry.
         */
        private boolean enableRetry = true;

        /**
         * Maximum retry attempts.
         */
        private int maxRetryAttempts = 3;

        /**
         * Default timeout (milliseconds).
         */
        private long defaultTimeout = 60000;

        /**
         * Maximum inbound message size (bytes).
         */
        private int maxInboundMessageSize = 4 * 1024 * 1024;

        /**
         * Keep alive time (seconds).
         */
        private int keepAliveTime = 300;

        /**
         * Keep alive timeout (seconds).
         */
        private int keepAliveTimeout = 20;

        /**
         * Keep alive without calls.
         */
        private boolean keepAliveWithoutCalls = false;

        /**
         * Enable TLS.
         */
        private boolean enableTls = false;

        /**
         * User agent.
         */
        private String userAgent = "adhar-grpc-client/1.0.0";
    }

    /**
     * Security configuration.
     */
    @Data
    public static class SecurityConfig {
        /**
         * Enable security.
         */
        private boolean enabled = false;

        /**
         * Enable TLS.
         */
        private boolean enableTls = false;

        /**
         * Enable mutual TLS.
         */
        private boolean enableMtls = false;

        /**
         * Certificate chain file path.
         */
        private String certChain;

        /**
         * Private key file path.
         */
        private String privateKey;

        /**
         * Trust certificate collection file path.
         */
        private String trustCertCollection;

        /**
         * Client auth type (NONE, OPTIONAL, REQUIRE).
         */
        private String clientAuth = "NONE";
    }

    /**
     * Observability configuration.
     */
    @Data
    public static class ObservabilityConfig {
        /**
         * Enable metrics.
         */
        private boolean enableMetrics = true;

        /**
         * Enable tracing.
         */
        private boolean enableTracing = true;

        /**
         * Enable logging interceptor.
         */
        private boolean enableLogging = true;

        /**
         * Log level (NONE, BASIC, HEADERS, FULL).
         */
        private String logLevel = "BASIC";

        /**
         * Enable health check.
         */
        private boolean enableHealthCheck = true;
    }
}

