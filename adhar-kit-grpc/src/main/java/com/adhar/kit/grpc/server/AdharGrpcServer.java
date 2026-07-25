package com.adhar.kit.grpc.server;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.exception.GrpcServiceConfigurationException;
import com.adhar.kit.grpc.interceptor.AuthServerInterceptor;
import com.adhar.kit.grpc.interceptor.ExceptionHandlerInterceptor;
import com.adhar.kit.grpc.interceptor.LoggingInterceptor;
import com.adhar.kit.grpc.interceptor.MetricsServerInterceptor;
import com.adhar.kit.grpc.util.GrpcUtils;
import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.TlsServerCredentials;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Adhar gRPC server with enterprise features.
 *
 * <p>Provides comprehensive gRPC server with:</p>
 * <ul>
 *   <li>Automatic service registration</li>
 *   <li>Health check service</li>
 *   <li>Reflection service for debugging</li>
 *   <li>Logging and tracing interceptors</li>
 *   <li>Exception handling</li>
 *   <li>Optional authentication (see {@link GrpcAuthenticator})</li>
 *   <li>Optional Micrometer metrics</li>
 *   <li>Graceful shutdown</li>
 *   <li>TLS/mTLS support</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * GrpcProperties properties = new GrpcProperties();
 * properties.getServer().setPort(9090);
 *
 * AdharGrpcServer server = new AdharGrpcServer(properties);
 * server.addService(new OrderServiceImpl());
 * server.addService(new InventoryServiceImpl());
 * server.start();
 *
 * // Graceful shutdown
 * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
 *     server.shutdown();
 * }));
 *
 * server.awaitTermination();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharGrpcServer {

    private final GrpcProperties properties;
    private final List<io.grpc.BindableService> services = new ArrayList<>();
    private final HealthStatusManager healthStatusManager = new HealthStatusManager();
    private volatile MeterRegistry meterRegistry;
    private volatile GrpcAuthenticator authenticator;
    private Server server;

    /**
     * Creates gRPC server with properties.
     *
     * @param properties gRPC properties
     */
    public AdharGrpcServer(GrpcProperties properties) {
        this.properties = properties;
    }

    /**
     * Adds a service to the server.
     *
     * @param service service implementation
     * @return this server for chaining
     */
    public AdharGrpcServer addService(io.grpc.BindableService service) {
        services.add(service);
        return this;
    }

    /**
     * Supplies a Micrometer registry so served calls are instrumented with
     * {@link MetricsServerInterceptor}. Optional; if never called, no
     * server-side metrics are recorded.
     *
     * @param meterRegistry Micrometer registry
     * @return this server for chaining
     */
    public AdharGrpcServer withMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        return this;
    }

    /**
     * Supplies an authenticator applied via {@link AuthServerInterceptor} when
     * {@code adhar.grpc.auth.enabled} is {@code true}.
     *
     * @param authenticator authenticator to use
     * @return this server for chaining
     */
    public AdharGrpcServer withAuthenticator(GrpcAuthenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    /**
     * Number of services registered so far, exposed for testing and for
     * {@code GrpcServiceRegistrar} to report what it wired up.
     *
     * @return number of registered services
     */
    public int getServiceCount() {
        return services.size();
    }

    /**
     * Starts the gRPC server.
     *
     * @throws IOException if server fails to start
     */
    public void start() throws IOException {
        GrpcProperties.ServerConfig config = properties.getServer();

        if (!config.isEnabled()) {
            log.info("gRPC server is disabled");
            return;
        }

        ServerBuilder<?> serverBuilder = buildServerBuilder(config);

        // Configure server settings
        serverBuilder
            .maxInboundMessageSize(config.getMaxInboundMessageSize())
            .maxInboundMetadataSize(config.getMaxInboundHeaderListSize())
            .keepAliveTime(config.getKeepAliveTime(), TimeUnit.SECONDS)
            .keepAliveTimeout(config.getKeepAliveTimeout(), TimeUnit.SECONDS)
            .permitKeepAliveTime(config.getPermitKeepAliveTime(), TimeUnit.SECONDS)
            .permitKeepAliveWithoutCalls(config.isPermitKeepAliveWithoutCalls());

        // Build the interceptor chain. Per ServerInterceptors.intercept(List)'s contract,
        // "the last interceptor will have its interceptCall called first" - i.e. the LAST
        // entry in this list is outermost, and its ServerCall wrapping is what every interceptor
        // added before it (and the service itself) will observe. MetricsServerInterceptor is
        // therefore added last so it records every call's final status regardless of where the
        // call is closed - an auth denial (closed directly by AuthServerInterceptor), an
        // uncaught exception (closed by ExceptionHandlerInterceptor), or normal completion deep
        // inside the service.
        List<ServerInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new ExceptionHandlerInterceptor());
        interceptors.add(new LoggingInterceptor());
        if (properties.getAuth().isEnabled()) {
            GrpcAuthenticator effectiveAuthenticator =
                    authenticator != null ? authenticator : new PermitAllGrpcAuthenticator();
            interceptors.add(new AuthServerInterceptor(effectiveAuthenticator));
            log.info("gRPC authentication enabled");
        }
        MeterRegistry registry = this.meterRegistry;
        if (registry != null && properties.getObservability().isEnableMetrics()) {
            interceptors.add(new MetricsServerInterceptor(registry));
            log.debug("gRPC server metrics enabled");
        }

        // Register services with interceptors
        for (io.grpc.BindableService service : services) {
            serverBuilder.addService(
                ServerInterceptors.intercept(service, interceptors)
            );
            log.info("Registered gRPC service: {}", service.getClass().getSimpleName());
        }

        // Add health check service
        if (config.isEnableHealthCheck()) {
            serverBuilder.addService(healthStatusManager.getHealthService());
            log.info("Health check service enabled");
        }

        // Add reflection service for debugging
        if (config.isEnableReflection()) {
            serverBuilder.addService(ProtoReflectionService.newInstance());
            log.info("Reflection service enabled");
        }

        // Build and start server
        server = serverBuilder.build().start();

        log.info("gRPC server started on port {}", config.getPort());

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server...");
            shutdown();
            log.info("gRPC server shut down");
        }));
    }

    /**
     * Builds the {@link ServerBuilder}, wiring up TLS/mTLS server credentials
     * from {@link GrpcProperties.SecurityConfig} when security is enabled, or
     * a plaintext builder otherwise.
     *
     * @param config server configuration (used for the port)
     * @return the server builder, already bound to the configured port
     * @throws GrpcServiceConfigurationException if TLS is enabled but required
     *                                            certificate/key properties are missing, or a configured file does not exist
     */
    private ServerBuilder<?> buildServerBuilder(GrpcProperties.ServerConfig config) {
        GrpcProperties.SecurityConfig security = properties.getSecurity();

        if (!security.isEnabled() || !security.isEnableTls()) {
            return ServerBuilder.forPort(config.getPort());
        }

        if (security.getCertChain() == null || security.getCertChain().isBlank()
                || security.getPrivateKey() == null || security.getPrivateKey().isBlank()) {
            throw new GrpcServiceConfigurationException(
                    "TLS is enabled but security.cert-chain / security.private-key are not configured");
        }

        TlsServerCredentials.Builder tlsBuilder;
        try {
            tlsBuilder = TlsServerCredentials.newBuilder()
                    .keyManager(
                            GrpcUtils.resolveCertFile(security.getCertChain()),
                            GrpcUtils.resolveCertFile(security.getPrivateKey()));

            if (security.isEnableMtls()) {
                if (security.getTrustCertCollection() == null || security.getTrustCertCollection().isBlank()) {
                    throw new GrpcServiceConfigurationException(
                            "mTLS is enabled but security.trust-cert-collection is not configured");
                }
                tlsBuilder.trustManager(GrpcUtils.resolveCertFile(security.getTrustCertCollection()));
                tlsBuilder.clientAuth(parseClientAuth(security.getClientAuth()));
            }
        } catch (IOException e) {
            throw new GrpcServiceConfigurationException("Failed to load TLS credentials for gRPC server", e);
        }

        ServerCredentials credentials = tlsBuilder.build();
        log.info("TLS enabled for gRPC server (mTLS={})", security.isEnableMtls());
        return Grpc.newServerBuilderForPort(config.getPort(), credentials);
    }

    private static TlsServerCredentials.ClientAuth parseClientAuth(String clientAuth) {
        if (clientAuth == null || clientAuth.isBlank()) {
            return TlsServerCredentials.ClientAuth.REQUIRE;
        }
        try {
            return TlsServerCredentials.ClientAuth.valueOf(clientAuth.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new GrpcServiceConfigurationException(
                    "Invalid security.client-auth value '" + clientAuth + "'; expected NONE, OPTIONAL, or REQUIRE", e);
        }
    }

    /**
     * Shuts down the server gracefully.
     */
    public void shutdown() {
        if (server != null) {
            try {
                int gracePeriod = properties.getServer().getShutdownGracePeriod();
                server.shutdown();
                if (!server.awaitTermination(gracePeriod, TimeUnit.SECONDS)) {
                    log.warn("Server did not terminate gracefully, forcing shutdown");
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
    }

    /**
     * Waits for server termination.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void awaitTermination() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Gets the server port.
     *
     * @return server port
     */
    public int getPort() {
        return server != null ? server.getPort() : -1;
    }

    /**
     * Checks if server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    /**
     * Gets health status manager.
     *
     * @return health status manager
     */
    public HealthStatusManager getHealthStatusManager() {
        return healthStatusManager;
    }
}
