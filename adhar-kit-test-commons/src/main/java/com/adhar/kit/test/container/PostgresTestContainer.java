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

    /** The PostgreSQL the platform runs (CNPG), for tests that must behave as on a cluster. */
    public static final String PLATFORM_IMAGE = "postgres:18.4";

    private static PostgreSQLContainer<?> container;
    private static PostgreSQLContainer<?> platform;

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
     * A singleton container of {@link #PLATFORM_IMAGE}, started on first use and never reused
     * across JVMs (app-ci runs it under kubedock). Used by {@code @AdharBatchTest}.
     *
     * @return the started container
     */
    public static synchronized PostgreSQLContainer<?> platformInstance() {
        if (platform == null) {
            PostgreSQLContainer<?> started = new PostgreSQLContainer<>(DockerImageName.parse(PLATFORM_IMAGE)
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName(DEFAULT_DATABASE)
                    .withUsername(DEFAULT_USERNAME)
                    .withPassword(DEFAULT_PASSWORD);
            started.start();
            log.info("PostgreSQL {} container started: {}", PLATFORM_IMAGE, started.getJdbcUrl());
            platform = started;
        }
        return platform;
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

