package com.adhar.kit.test.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for RedisTestContainer.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("RedisTestContainer Tests")
class RedisTestContainerTest {

    @Test
    @DisplayName("Should create singleton instance")
    void testGetInstance() {
        // When
        GenericContainer<?> instance1 = RedisTestContainer.getInstance();
        GenericContainer<?> instance2 = RedisTestContainer.getInstance();

        // Then
        assertNotNull(instance1, "First instance should not be null");
        assertNotNull(instance2, "Second instance should not be null");
        assertSame(instance1, instance2, "Should return same instance (singleton pattern)");
    }

    @Test
    @DisplayName("Should have correct Docker image")
    void testDockerImage() {
        // When
        GenericContainer<?> container = RedisTestContainer.getInstance();

        // Then
        assertNotNull(container, "Container should not be null");
        String image = container.getDockerImageName();
        assertNotNull(image, "Docker image name should not be null");
        assertTrue(image.contains("redis"), "Should use redis image");
        assertTrue(image.contains("7"), "Should use redis version 7");
    }

    @Test
    @DisplayName("Should return consistent instance across multiple calls")
    void testConsistentSingleton() {
        // When
        GenericContainer<?> instance1 = RedisTestContainer.getInstance();
        GenericContainer<?> instance2 = RedisTestContainer.getInstance();
        GenericContainer<?> instance3 = RedisTestContainer.getInstance();

        // Then
        assertSame(instance1, instance2, "First and second instances should be same");
        assertSame(instance2, instance3, "Second and third instances should be same");
        assertSame(instance1, instance3, "First and third instances should be same");
    }

    @Test
    @DisplayName("Should not fail when stopping non-running container")
    void testStopNonRunningContainer() {
        // When/Then
        assertDoesNotThrow(() -> RedisTestContainer.stop(),
                "Stopping non-running container should not throw exception");
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should start and provide valid connection details")
    void testStartAndGetConnectionDetails() {
        try {
            // When
            RedisTestContainer.start();
            GenericContainer<?> container = RedisTestContainer.getInstance();
            String host = RedisTestContainer.getHost();
            Integer port = RedisTestContainer.getPort();
            String connectionUrl = RedisTestContainer.getConnectionUrl();

            // Then
            assertTrue(container.isRunning(), "Container should be running after start");
            assertNotNull(host, "Host should not be null");
            assertNotNull(port, "Port should not be null");
            assertNotNull(connectionUrl, "Connection URL should not be null");
            assertTrue(port > 0, "Port should be positive");
            assertTrue(connectionUrl.startsWith("redis://"), "Connection URL should start with redis://");
            assertTrue(connectionUrl.contains(host), "Connection URL should contain host");
            assertTrue(connectionUrl.contains(port.toString()), "Connection URL should contain port");
        } finally {
            RedisTestContainer.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should stop running container gracefully")
    void testStopRunningContainer() {
        // Given
        RedisTestContainer.start();
        assertTrue(RedisTestContainer.getInstance().isRunning(), "Container should be running");

        // When
        RedisTestContainer.stop();

        // Then
        assertFalse(RedisTestContainer.getInstance().isRunning(), "Container should be stopped");
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should provide valid host")
    void testGetHost() {
        try {
            // Given
            RedisTestContainer.start();

            // When
            String host = RedisTestContainer.getHost();

            // Then
            assertNotNull(host, "Host should not be null");
            assertFalse(host.isEmpty(), "Host should not be empty");
        } finally {
            RedisTestContainer.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should provide valid port")
    void testGetPort() {
        try {
            // Given
            RedisTestContainer.start();

            // When
            Integer port = RedisTestContainer.getPort();

            // Then
            assertNotNull(port, "Port should not be null");
            assertTrue(port > 0, "Port should be positive");
            assertTrue(port < 65536, "Port should be valid port number");
        } finally {
            RedisTestContainer.stop();
        }
    }
}

