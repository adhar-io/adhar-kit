package com.adhar.kit.test.container;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainer configuration for Redis.
 * Provides a reusable Redis container for integration tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class RedisTestContainer {

    private static final Logger log = LoggerFactory.getLogger(RedisTestContainer.class);
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;

    private static GenericContainer<?> container;

    /**
     * Get a singleton Redis container instance.
     */
    public static GenericContainer<?> getInstance() {
        if (container == null) {
            container = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Start the Redis container.
     */
    public static void start() {
        getInstance().start();
        log.info("Redis container started on port: {}", container.getMappedPort(REDIS_PORT));
    }

    /**
     * Stop the Redis container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("Redis container stopped");
        }
    }

    /**
     * Get Redis host.
     */
    public static String getHost() {
        return getInstance().getHost();
    }

    /**
     * Get mapped Redis port.
     */
    public static Integer getPort() {
        return getInstance().getMappedPort(REDIS_PORT);
    }

    /**
     * Get Redis connection URL.
     */
    public static String getConnectionUrl() {
        return String.format("redis://%s:%d", getHost(), getPort());
    }
}

