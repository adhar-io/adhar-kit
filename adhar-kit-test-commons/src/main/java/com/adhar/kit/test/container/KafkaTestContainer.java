package com.adhar.kit.test.container;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainer configuration for Apache Kafka.
 * Provides a reusable Kafka container for integration tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class KafkaTestContainer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTestContainer.class);
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.5.0";

    private static ConfluentKafkaContainer container;

    /**
     * Get a singleton Kafka container instance.
     */
    public static ConfluentKafkaContainer getInstance() {
        if (container == null) {
            container = new ConfluentKafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
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

