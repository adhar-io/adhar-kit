package com.adhar.kit.test.container;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainer configuration for Apache Kafka.
 * Provides a reusable Kafka container for integration tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class KafkaTestContainer {

    private static final String KAFKA_IMAGE = "apache/kafka:latest";

    private static KafkaContainer container;

    /**
     * Get a singleton Kafka container instance.
     */
    public static KafkaContainer getInstance() {
        if (container == null) {
            container = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Start the Kafka container.
     */
    public static void start() {
        getInstance().start();
        log.info("Kafka container started: {}", container.getBootstrapServers());
    }

    /**
     * Stop the Kafka container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("Kafka container stopped");
        }
    }

    /**
     * Get Kafka bootstrap servers.
     */
    public static String getBootstrapServers() {
        return getInstance().getBootstrapServers();
    }
}

