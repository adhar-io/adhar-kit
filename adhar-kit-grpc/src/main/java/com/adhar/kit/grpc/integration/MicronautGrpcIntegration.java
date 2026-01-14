package com.adhar.kit.grpc.integration;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Micronaut integration for Adhar gRPC.
 *
 * <p>Provides automatic gRPC server configuration for Micronaut applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Factory
 * public class GrpcConfig {
 *
 *     @Bean
 *     @Singleton
 *     public AdharGrpcServer grpcServer(GrpcProperties properties,
 *                                        Collection<BindableService> services) {
 *         AdharGrpcServer server = MicronautGrpcIntegration.createServer(properties);
 *         services.forEach(server::addService);
 *         return server;
 *     }
 *
 *     @EventListener
 *     void onStartup(StartupEvent event, AdharGrpcServer server) {
 *         MicronautGrpcIntegration.startServer(server);
 *     }
 *
 *     @EventListener
 *     void onShutdown(ShutdownEvent event, AdharGrpcServer server) {
 *         server.shutdown();
 *     }
 * }
 *
 * @GrpcService
 * @Singleton
 * public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
 *     // Implementation
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class MicronautGrpcIntegration {

    /**
     * Creates gRPC server for Micronaut.
     *
     * @param properties gRPC properties
     * @return configured server
     */
    public static AdharGrpcServer createServer(GrpcProperties properties) {
        log.info("Creating gRPC server for Micronaut");
        return new AdharGrpcServer(properties);
    }

    /**
     * Starts gRPC server.
     *
     * @param server server to start
     */
    public static void startServer(AdharGrpcServer server) {
        try {
            server.start();
            log.info("gRPC server started successfully");
        } catch (Exception e) {
            log.error("Failed to start gRPC server", e);
            throw new RuntimeException("Failed to start gRPC server", e);
        }
    }

    /**
     * Checks if Micronaut is available.
     *
     * @return true if Micronaut is on classpath
     */
    public static boolean isMicronautAvailable() {
        try {
            Class.forName("io.micronaut.runtime.Micronaut");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

