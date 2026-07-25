package com.adhar.kit.test.base;

import com.adhar.kit.test.container.KafkaTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that need a Kafka container.
 * Mirrors {@link BaseIntegrationTest}'s {@code @DynamicPropertySource} approach, but wires
 * Kafka instead of PostgreSQL.
 *
 * <p>The container is started through the shared {@link TestContainerRegistry} so it
 * participates in the same lifecycle bookkeeping as every other container in this module.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@SpringBootTest
public abstract class KafkaIntegrationTest {

    @BeforeAll
    static void initializeContainers() {
        TestContainerRegistry.getInstance().registerAndStart("kafka", KafkaTestContainer.getInstance());
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainer::getBootstrapServers);
    }
}
