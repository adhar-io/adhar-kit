package com.adhar.kit.test.container;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainer configuration for PostgreSQL database.
 * Provides a reusable PostgreSQL container for integration tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class PostgresTestContainer {

    private static final Logger log = LoggerFactory.getLogger(PostgresTestContainer.class);
    private static final String POSTGRES_IMAGE = "postgres:15-alpine";
    private static final String DEFAULT_DATABASE = "testdb";
    private static final String DEFAULT_USERNAME = "test";
    private static final String DEFAULT_PASSWORD = "test";

    private static PostgreSQLContainer<?> container;

    /**
     * Get a singleton PostgreSQL container instance.
     */
    public static PostgreSQLContainer<?> getInstance() {
        if (container == null) {
            container = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName(DEFAULT_DATABASE)
                    .withUsername(DEFAULT_USERNAME)
                    .withPassword(DEFAULT_PASSWORD)
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Start the PostgreSQL container.
     */
    public static void start() {
        getInstance().start();
        log.info("PostgreSQL container started: {}", container.getJdbcUrl());
    }

    /**
     * Stop the PostgreSQL container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("PostgreSQL container stopped");
        }
    }

    /**
     * Get JDBC URL for the PostgreSQL container.
     */
    public static String getJdbcUrl() {
        return getInstance().getJdbcUrl();
    }

    /**
     * Get username for the PostgreSQL container.
     */
    public static String getUsername() {
        return getInstance().getUsername();
    }

    /**
     * Get password for the PostgreSQL container.
     */
    public static String getPassword() {
        return getInstance().getPassword();
    }
}

