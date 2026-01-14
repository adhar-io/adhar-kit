package com.adhar.kit.grpc.integration;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Boot integration for Adhar gRPC.
 *
 * <p>Provides automatic gRPC server configuration for Spring Boot applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Configuration
 * public class GrpcConfig {
 *
 *     @Bean
 *     public AdharGrpcServer grpcServer(GrpcProperties properties,
 *                                        List<BindableService> services) {
 *         AdharGrpcServer server = SpringBootGrpcIntegration.createServer(properties);
 *         services.forEach(server::addService);
 *         return server;
 *     }
 *
 *     @Bean
 *     public CommandLineRunner startGrpcServer(AdharGrpcServer server) {
 *         return args -> SpringBootGrpcIntegration.startServer(server);
 *     }
 * }
 *
 * @GrpcService
 * @Component
 * public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
 *     // Implementation
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SpringBootGrpcIntegration {

    /**
     * Creates gRPC server for Spring Boot.
     *
     * @param properties gRPC properties
     * @return configured server
     */
    public static AdharGrpcServer createServer(GrpcProperties properties) {
        log.info("Creating gRPC server for Spring Boot");
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
     * Checks if Spring Boot is available.
     *
     * @return true if Spring Boot is on classpath
     */
    public static boolean isSpringBootAvailable() {
        try {
            Class.forName("org.springframework.boot.SpringApplication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Auto-configuration hint.
     */
    public static class AutoConfiguration {
        // This class can be used as a hint for Spring Boot auto-configuration
    }
}

