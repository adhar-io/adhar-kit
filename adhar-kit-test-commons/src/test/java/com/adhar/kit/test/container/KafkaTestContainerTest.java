package com.adhar.kit.test.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.KafkaContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for KafkaTestContainer.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("KafkaTestContainer Tests")
class KafkaTestContainerTest {

    @Test
    @DisplayName("Should create singleton instance")
    void testGetInstance() {
        // When
        KafkaContainer instance1 = KafkaTestContainer.getInstance();
        KafkaContainer instance2 = KafkaTestContainer.getInstance();

        // Then
        assertNotNull(instance1, "First instance should not be null");
        assertNotNull(instance2, "Second instance should not be null");
        assertSame(instance1, instance2, "Should return same instance (singleton pattern)");
    }

    @Test
    @DisplayName("Should have correct Docker image")
    void testDockerImage() {
        // When
        KafkaContainer container = KafkaTestContainer.getInstance();

        // Then
        assertNotNull(container, "Container should not be null");
        String image = container.getDockerImageName();
        assertNotNull(image, "Docker image name should not be null");
        assertTrue(image.contains("kafka"), "Should use kafka image");
    }

    @Test
    @DisplayName("Should return consistent instance across multiple calls")
    void testConsistentSingleton() {
        // When
        KafkaContainer instance1 = KafkaTestContainer.getInstance();
        KafkaContainer instance2 = KafkaTestContainer.getInstance();
        KafkaContainer instance3 = KafkaTestContainer.getInstance();

        // Then
        assertSame(instance1, instance2, "First and second instances should be same");
        assertSame(instance2, instance3, "Second and third instances should be same");
        assertSame(instance1, instance3, "First and third instances should be same");
    }

    @Test
    @DisplayName("Should not fail when stopping non-running container")
    void testStopNonRunningContainer() {
        // When/Then
        assertDoesNotThrow(() -> KafkaTestContainer.stop(),
                "Stopping non-running container should not throw exception");
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should start and provide bootstrap servers")
    void testStartAndGetBootstrapServers() {
        try {
            // When
            KafkaTestContainer.start();
            KafkaContainer container = KafkaTestContainer.getInstance();
            String bootstrapServers = KafkaTestContainer.getBootstrapServers();

            // Then
            assertTrue(container.isRunning(), "Container should be running after start");
            assertNotNull(bootstrapServers, "Bootstrap servers should not be null");
            assertFalse(bootstrapServers.isEmpty(), "Bootstrap servers should not be empty");
            assertTrue(bootstrapServers.contains(":"), "Bootstrap servers should contain port separator");
        } finally {
            KafkaTestContainer.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should stop running container gracefully")
    void testStopRunningContainer() {
        // Given
        KafkaTestContainer.start();
        assertTrue(KafkaTestContainer.getInstance().isRunning(), "Container should be running");

        // When
        KafkaTestContainer.stop();

        // Then
        assertFalse(KafkaTestContainer.getInstance().isRunning(), "Container should be stopped");
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should provide valid bootstrap servers configuration")
    void testGetBootstrapServers() {
        try {
            // Given
            KafkaTestContainer.start();

            // When
            String bootstrapServers = KafkaTestContainer.getBootstrapServers();

            // Then
            assertNotNull(bootstrapServers, "Bootstrap servers should not be null");
            assertTrue(bootstrapServers.startsWith("PLAINTEXT://") ||
                       bootstrapServers.matches(".*:\\d+.*"),
                       "Bootstrap servers should have valid format");
        } finally {
            KafkaTestContainer.stop();
        }
    }
}

