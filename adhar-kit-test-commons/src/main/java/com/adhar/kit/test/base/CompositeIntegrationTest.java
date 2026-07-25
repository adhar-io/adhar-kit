package com.adhar.kit.test.base;

import com.adhar.kit.test.container.KafkaTestContainer;
import com.adhar.kit.test.container.MongoTestContainer;
import com.adhar.kit.test.container.PostgresTestContainer;
import com.adhar.kit.test.container.RedisTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that need several backing services at once
 * (PostgreSQL, MongoDB, Redis, and Kafka).
 *
 * <p>All four containers are started through the shared {@link TestContainerRegistry} in a
 * fixed order (postgres, mongo, redis, kafka), each joining the registry's shared Testcontainers
 * network. Because the registry tracks registration order, tearing everything down with
 * {@link TestContainerRegistry#stopAll()} stops them in the reverse order they were started.</p>
 *
 * <p>Prefer the single-service base classes ({@link BaseIntegrationTest}, {@link MongoIntegrationTest},
 * {@link RedisIntegrationTest}, {@link KafkaIntegrationTest}) when a test only needs one backing
 * service - starting every container for every test class is slower than necessary.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@SpringBootTest
public abstract class CompositeIntegrationTest {

    @BeforeAll
    static void initializeContainers() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        registry.registerAndStart("postgres", PostgresTestContainer.getInstance());
        registry.registerAndStart("mongo", MongoTestContainer.getInstance());
        registry.registerAndStart("redis", RedisTestContainer.getInstance());
        registry.registerAndStart("kafka", KafkaTestContainer.getInstance());
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL / JPA properties
        registry.add("spring.datasource.url", PostgresTestContainer::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainer::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "true");

        // MongoDB properties
        registry.add("spring.data.mongodb.uri", MongoTestContainer::getConnectionString);

        // Redis properties
        registry.add("spring.data.redis.host", RedisTestContainer::getHost);
        registry.add("spring.data.redis.port", RedisTestContainer::getPort);

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainer::getBootstrapServers);
    }
}
