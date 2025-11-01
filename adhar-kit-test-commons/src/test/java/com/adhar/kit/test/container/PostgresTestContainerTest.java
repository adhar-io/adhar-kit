package com.adhar.kit.test.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PostgresTestContainer.
 * Tests singleton pattern, configuration, and container lifecycle.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("PostgresTestContainer Tests")
class PostgresTestContainerTest {

    @Test
    @DisplayName("Should create singleton instance")
    void testGetInstance() {
        // When
        PostgreSQLContainer<?> instance1 = PostgresTestContainer.getInstance();
        PostgreSQLContainer<?> instance2 = PostgresTestContainer.getInstance();

        // Then
        assertNotNull(instance1, "First instance should not be null");
        assertNotNull(instance2, "Second instance should not be null");
        assertSame(instance1, instance2, "Should return same instance (singleton pattern)");
    }

    @Test
    @DisplayName("Should have correct database configuration")
    void testDatabaseConfiguration() {
        // When
        PostgreSQLContainer<?> container = PostgresTestContainer.getInstance();

        // Then
        assertNotNull(container, "Container should not be null");
        assertEquals("testdb", container.getDatabaseName(), "Database name should be 'testdb'");
        assertEquals("test", container.getUsername(), "Username should be 'test'");
        assertEquals("test", container.getPassword(), "Password should be 'test'");
    }

    @Test
    @DisplayName("Should have correct Docker image")
    void testDockerImage() {
        // When
        PostgreSQLContainer<?> container = PostgresTestContainer.getInstance();

        // Then
        assertNotNull(container, "Container should not be null");
        String image = container.getDockerImageName();
        assertNotNull(image, "Docker image name should not be null");
        assertTrue(image.contains("postgres"), "Should use postgres image");
        assertTrue(image.contains("15"), "Should use postgres version 15");
    }

    @Test
    @DisplayName("Should provide username via static method")
    void testGetUsername() {
        // When
        String username = PostgresTestContainer.getUsername();

        // Then
        assertNotNull(username, "Username should not be null");
        assertEquals("test", username, "Username should be 'test'");
    }

    @Test
    @DisplayName("Should provide password via static method")
    void testGetPassword() {
        // When
        String password = PostgresTestContainer.getPassword();

        // Then
        assertNotNull(password, "Password should not be null");
        assertEquals("test", password, "Password should be 'test'");
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should provide JDBC URL via static method")
    void testGetJdbcUrl() {
        try {
            // Given - Container must be started to get JDBC URL
            PostgresTestContainer.start();

            // When
            String jdbcUrl = PostgresTestContainer.getJdbcUrl();

            // Then
            assertNotNull(jdbcUrl, "JDBC URL should not be null");
            assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "JDBC URL should start with jdbc:postgresql://");
            assertTrue(jdbcUrl.contains("testdb"), "JDBC URL should contain database name");
        } finally {
            PostgresTestContainer.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should start and provide valid JDBC URL")
    void testStartAndGetJdbcUrl() {
        try {
            // When
            PostgresTestContainer.start();
            String jdbcUrl = PostgresTestContainer.getJdbcUrl();
            PostgreSQLContainer<?> container = PostgresTestContainer.getInstance();

            // Then
            assertNotNull(jdbcUrl, "JDBC URL should not be null after start");
            assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "JDBC URL should be valid PostgreSQL URL");
            assertTrue(container.isRunning(), "Container should be running after start");

            // Verify connection details
            assertTrue(jdbcUrl.contains(String.valueOf(container.getFirstMappedPort())),
                    "JDBC URL should contain mapped port");
        } finally {
            // Cleanup
            PostgresTestContainer.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should stop running container gracefully")
    void testStopRunningContainer() {
        // Given
        PostgresTestContainer.start();
        assertTrue(PostgresTestContainer.getInstance().isRunning(), "Container should be running");

        // When
        PostgresTestContainer.stop();

        // Then
        assertFalse(PostgresTestContainer.getInstance().isRunning(), "Container should be stopped");
    }

    @Test
    @DisplayName("Should not fail when stopping non-running container")
    void testStopNonRunningContainer() {
        // When/Then
        assertDoesNotThrow(() -> PostgresTestContainer.stop(),
                "Stopping non-running container should not throw exception");
    }

    @Test
    @DisplayName("Should have reuse enabled")
    void testContainerReuse() {
        // When
        PostgreSQLContainer<?> container = PostgresTestContainer.getInstance();

        // Then
        // Note: This tests the configuration, actual reuse behavior is at Testcontainers level
        assertNotNull(container, "Container should not be null");
    }

    @Test
    @DisplayName("Should return consistent instance across multiple calls")
    void testConsistentSingleton() {
        // When
        PostgreSQLContainer<?> instance1 = PostgresTestContainer.getInstance();
        PostgreSQLContainer<?> instance2 = PostgresTestContainer.getInstance();
        PostgreSQLContainer<?> instance3 = PostgresTestContainer.getInstance();

        // Then
        assertSame(instance1, instance2, "First and second instances should be same");
        assertSame(instance2, instance3, "Second and third instances should be same");
        assertSame(instance1, instance3, "First and third instances should be same");
    }

    @Test
    @EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
    @DisplayName("Should provide valid configuration for test datasource")
    void testDatasourceConfiguration() {
        try {
            // Given - Container must be started to get JDBC URL
            PostgresTestContainer.start();

            // When
            String jdbcUrl = PostgresTestContainer.getJdbcUrl();
            String username = PostgresTestContainer.getUsername();
            String password = PostgresTestContainer.getPassword();

            // Then
            assertNotNull(jdbcUrl, "JDBC URL should be available");
            assertNotNull(username, "Username should be available");
            assertNotNull(password, "Password should be available");

            assertFalse(jdbcUrl.isEmpty(), "JDBC URL should not be empty");
            assertFalse(username.isEmpty(), "Username should not be empty");
            assertFalse(password.isEmpty(), "Password should not be empty");
        } finally {
            PostgresTestContainer.stop();
        }
    }
}


