package com.adhar.kit.grpc.integration;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    void testFrameworkDetection_QuarkusAndMicronautNotOnClasspath() {
        // Neither Quarkus nor Micronaut are dependencies of this module.
        assertFalse(QuarkusGrpcIntegration.isQuarkusAvailable());
        assertFalse(MicronautGrpcIntegration.isMicronautAvailable());
    }

    @Test
    void testSpringBootDetection_SpringOnClasspath() {
        // spring-boot-starter is a (optional) dependency, so SpringApplication is present.
        assertTrue(SpringBootGrpcIntegration.isSpringBootAvailable());
    }

    @Test
    void testSpringBootIntegration_StartServer_Success() throws IOException {
        AdharGrpcServer server = mock(AdharGrpcServer.class);
        doNothing().when(server).start();

        SpringBootGrpcIntegration.startServer(server);

        verify(server).start();
    }

    @Test
    void testSpringBootIntegration_StartServer_Failure_WrapsException() throws IOException {
        AdharGrpcServer server = mock(AdharGrpcServer.class);
        doThrow(new IOException("bind failed")).when(server).start();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SpringBootGrpcIntegration.startServer(server));
        assertEquals("Failed to start gRPC server", ex.getMessage());
    }

    @Test
    void testQuarkusIntegration_StartServer_Success() throws IOException {
        AdharGrpcServer server = mock(AdharGrpcServer.class);
        doNothing().when(server).start();

        QuarkusGrpcIntegration.startServer(server);

        verify(server).start();
    }

    @Test
    void testQuarkusIntegration_StartServer_Failure_WrapsException() throws IOException {
        AdharGrpcServer server = mock(AdharGrpcServer.class);
        doThrow(new IOException("bind failed")).when(server).start();

        assertThrows(RuntimeException.class, () -> QuarkusGrpcIntegration.startServer(server));
    }

    @Test
    void testMicronautIntegration_StartServer_Success() throws IOException {
        AdharGrpcServer server = mock(AdharGrpcServer.class);
        doNothing().when(server).start();

        MicronautGrpcIntegration.startServer(server);

        verify(server).start();
    }

    @Test
    void testMicronautIntegration_StartServer_Failure_WrapsException() throws IOException {
        AdharGrpcServer server = mock(AdharGrpcServer.class);
        doThrow(new IOException("bind failed")).when(server).start();

        assertThrows(RuntimeException.class, () -> MicronautGrpcIntegration.startServer(server));
    }

    @Test
    void testSpringBootAutoConfigurationHint_Instantiable() {
        assertNotNull(new SpringBootGrpcIntegration.AutoConfiguration());
    }
}

