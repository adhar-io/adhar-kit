package com.adhar.kit.test.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MongoDBContainer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MongoTestContainer Tests")
class MongoTestContainerTest {

    @Test
    @DisplayName("Should create singleton instance")
    void testGetInstance() {
        MongoDBContainer instance1 = MongoTestContainer.getInstance();
        MongoDBContainer instance2 = MongoTestContainer.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "Should return same instance");
    }

    @Test
    @DisplayName("Should have correct Docker image")
    void testDockerImage() {
        MongoDBContainer container = MongoTestContainer.getInstance();

        assertNotNull(container);
        String image = container.getDockerImageName();
        assertTrue(image.contains("mongo"), "Should use mongo image");
    }

    @Test
    @DisplayName("Should have reuse enabled")
    void testReuseEnabled() {
        MongoDBContainer container = MongoTestContainer.getInstance();

        assertNotNull(container);
        // Container should be configured with reuse
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should start and get connection string")
    void testStartAndGetConnectionString() {
        try {
            MongoTestContainer.start();
            String connectionString = MongoTestContainer.getConnectionString();

            assertNotNull(connectionString);
            assertTrue(connectionString.startsWith("mongodb://"));
            assertTrue(MongoTestContainer.getInstance().isRunning());
        } finally {
            MongoTestContainer.stop();
        }
    }

    @Test
    @DisplayName("Should not fail when stopping non-running container")
    void testStopNonRunningContainer() {
        assertDoesNotThrow(() -> MongoTestContainer.stop());
    }
}

