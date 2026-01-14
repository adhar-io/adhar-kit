package com.adhar.kit.grpc.integration;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus integration for Adhar gRPC.
 *
 * <p>Provides automatic gRPC server configuration for Quarkus applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class GrpcConfig {
 *
 *     @Produces
 *     @Singleton
 *     public AdharGrpcServer grpcServer(@ConfigProperty GrpcProperties properties,
 *                                        Instance<BindableService> services) {
 *         AdharGrpcServer server = QuarkusGrpcIntegration.createServer(properties);
 *         services.forEach(server::addService);
 *         return server;
 *     }
 *
 *     void onStart(@Observes StartupEvent event, AdharGrpcServer server) {
 *         QuarkusGrpcIntegration.startServer(server);
 *     }
 *
 *     void onStop(@Observes ShutdownEvent event, AdharGrpcServer server) {
 *         server.shutdown();
 *     }
 * }
 *
 * @GrpcService
 * @ApplicationScoped
 * public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
 *     // Implementation
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class QuarkusGrpcIntegration {

    /**
     * Creates gRPC server for Quarkus.
     *
     * @param properties gRPC properties
     * @return configured server
     */
    public static AdharGrpcServer createServer(GrpcProperties properties) {
        log.info("Creating gRPC server for Quarkus");
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
     * Checks if Quarkus is available.
     *
     * @return true if Quarkus is on classpath
     */
    public static boolean isQuarkusAvailable() {
        try {
            Class.forName("io.quarkus.runtime.Quarkus");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

