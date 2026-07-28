package com.adhar.kit.grpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
@ConfigurationProperties(prefix = "adhar.grpc")
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
     * Authentication configuration.
     */
    private AuthConfig auth = new AuthConfig();

    /**
     * Concurrency-limiting configuration.
     */
    private ConcurrencyConfig concurrency = new ConcurrencyConfig();

    /**
     * Enable automatic registration of {@code @GrpcService} beans with the
     * {@code AdharGrpcServer} bean (via {@code GrpcServiceRegistrar}).
     */
    private boolean enableServiceRegistrar = true;

    /**
     * Enable automatic injection of {@code ManagedChannel}s into
     * {@code @GrpcClient} fields (via {@code GrpcClientBeanPostProcessor}).
     */
    private boolean enableClientInjection = true;

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

        /**
         * Initial backoff before the first retry (milliseconds), used to build
         * the gRPC built-in retry policy's {@code initialBackoff}.
         */
        private long initialBackoffMillis = 1000;

        /**
         * Maximum backoff between retries (milliseconds), used to build the
         * gRPC built-in retry policy's {@code maxBackoff}.
         */
        private long maxBackoffMillis = 10000;

        /**
         * Backoff multiplier applied between successive retries.
         */
        private double backoffMultiplier = 2.0;

        /**
         * Status codes that are eligible for automatic retry.
         */
        private List<String> retryableStatusCodes = new ArrayList<>(List.of(
                "UNAVAILABLE", "DEADLINE_EXCEEDED", "RESOURCE_EXHAUSTED", "ABORTED"));
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

    /**
     * Authentication configuration.
     *
     * <p>When {@link #enabled} is {@code true}, the server applies an
     * {@code AuthServerInterceptor} that validates a Bearer token or API key
     * against the configured {@link #sharedSecret} before allowing a call
     * through. Unauthenticated calls fail with {@code UNAUTHENTICATED}.</p>
     */
    @Data
    public static class AuthConfig {
        /**
         * Enable authentication enforcement.
         */
        private boolean enabled = false;

        /**
         * Shared secret used by {@code StaticTokenAuthenticator} to validate
         * Bearer tokens / API keys.
         */
        private String sharedSecret;
    }

    /**
     * Concurrency-limiting configuration.
     *
     * <p>When {@link #enabled} is {@code true}, the server applies a
     * {@code ConcurrencyLimitServerInterceptor} that bounds the number of
     * concurrent in-flight calls. A {@link #globalLimit} caps total concurrent
     * calls across all services, and {@link #serviceLimits} caps concurrent
     * calls per fully-qualified gRPC service name (e.g.
     * {@code my.package.OrderService}). Calls that would exceed a limit are
     * rejected with {@code RESOURCE_EXHAUSTED} rather than queued.</p>
     */
    @Data
    public static class ConcurrencyConfig {
        /**
         * Enable concurrency limiting.
         */
        private boolean enabled = false;

        /**
         * Maximum number of concurrent in-flight calls across all services.
         * Values less than or equal to zero disable the global limit.
         */
        private int globalLimit = 200;

        /**
         * Per-service concurrency limits keyed by fully-qualified gRPC service
         * name (e.g. {@code my.package.OrderService}). Only services with an
         * entry here are individually bounded; a value less than or equal to
         * zero disables the limit for that service.
         */
        private Map<String, Integer> serviceLimits = new HashMap<>();
    }
}

