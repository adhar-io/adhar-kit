package com.adhar.kit.test.container;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainer configuration for MongoDB.
 * Provides a reusable MongoDB container for integration tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class MongoTestContainer {

    private static final String MONGO_IMAGE = "mongo:7";

    private static MongoDBContainer container;

    /**
     * Get a singleton MongoDB container instance.
     */
    public static MongoDBContainer getInstance() {
        if (container == null) {
            container = new MongoDBContainer(DockerImageName.parse(MONGO_IMAGE))
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Start the MongoDB container.
     */
    public static void start() {
        getInstance().start();
        log.info("MongoDB container started: {}", container.getReplicaSetUrl());
    }

    /**
     * Stop the MongoDB container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("MongoDB container stopped");
        }
    }

    /**
     * Get MongoDB connection string.
     */
    public static String getConnectionString() {
        return getInstance().getReplicaSetUrl();
    }
}

