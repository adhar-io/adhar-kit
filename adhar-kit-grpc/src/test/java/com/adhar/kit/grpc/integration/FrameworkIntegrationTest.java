package com.adhar.kit.grpc.integration;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for framework integrations.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class FrameworkIntegrationTest {

    @Test
    void testSpringBootIntegration_CreateServer() {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setEnabled(false); // Don't actually start

        AdharGrpcServer server = SpringBootGrpcIntegration.createServer(properties);

        assertNotNull(server);
    }

    @Test
    void testQuarkusIntegration_CreateServer() {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setEnabled(false);

        AdharGrpcServer server = QuarkusGrpcIntegration.createServer(properties);

        assertNotNull(server);
    }

    @Test
    void testMicronautIntegration_CreateServer() {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setEnabled(false);

        AdharGrpcServer server = MicronautGrpcIntegration.createServer(properties);

        assertNotNull(server);
    }

    @Test
    void testFrameworkDetection() {
        // These will return false in test environment unless framework is on classpath
        // Just verify methods don't throw exceptions
        assertDoesNotThrow(() -> SpringBootGrpcIntegration.isSpringBootAvailable());
        assertDoesNotThrow(() -> QuarkusGrpcIntegration.isQuarkusAvailable());
        assertDoesNotThrow(() -> MicronautGrpcIntegration.isMicronautAvailable());
    }
}

