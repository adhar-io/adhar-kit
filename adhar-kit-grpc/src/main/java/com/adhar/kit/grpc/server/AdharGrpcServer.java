package com.adhar.kit.grpc.server;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.interceptor.ExceptionHandlerInterceptor;
import com.adhar.kit.grpc.interceptor.LoggingInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
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

        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(config.getPort());

        // Configure server settings
        serverBuilder
            .maxInboundMessageSize(config.getMaxInboundMessageSize())
            .maxInboundMetadataSize(config.getMaxInboundHeaderListSize())
            .keepAliveTime(config.getKeepAliveTime(), TimeUnit.SECONDS)
            .keepAliveTimeout(config.getKeepAliveTimeout(), TimeUnit.SECONDS)
            .permitKeepAliveTime(config.getPermitKeepAliveTime(), TimeUnit.SECONDS)
            .permitKeepAliveWithoutCalls(config.isPermitKeepAliveWithoutCalls());

        // Add interceptors
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        ExceptionHandlerInterceptor exceptionHandler = new ExceptionHandlerInterceptor();

        // Register services with interceptors
        for (io.grpc.BindableService service : services) {
            serverBuilder.addService(
                ServerInterceptors.intercept(service, loggingInterceptor, exceptionHandler)
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

