package com.adhar.kit.test.base;

import com.adhar.kit.test.container.KafkaTestContainer;
import com.adhar.kit.test.container.MongoTestContainer;
import com.adhar.kit.test.container.PostgresTestContainer;
import com.adhar.kit.test.container.RedisTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CompositeIntegrationTest}.
 *
 * <p>All four container types are intercepted with {@link MockedConstruction} so no Docker
 * container is ever started, and the registration order the registry ends up with is
 * verified directly.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("CompositeIntegrationTest Tests")
class CompositeIntegrationTestTest {

    private static class CapturingRegistry implements DynamicPropertyRegistry {
        final Map<String, Supplier<Object>> props = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            props.put(name, valueSupplier);
        }
    }

    private void resetStatic(Class<?> clazz) throws Exception {
        Field f = clazz.getDeclaredField("container");
        f.setAccessible(true);
        f.set(null, null);
    }

    private void resetStatics() throws Exception {
        resetStatic(PostgresTestContainer.class);
        resetStatic(MongoTestContainer.class);
        resetStatic(RedisTestContainer.class);
        resetStatic(KafkaTestContainer.class);

        Field registryField = TestContainerRegistry.class.getDeclaredField("instance");
        registryField.setAccessible(true);
        registryField.set(null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        resetStatics();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetStatics();
    }

    @Test
    @DisplayName("Should be instantiable as a concrete subclass")
    void testInstantiable() {
        CompositeIntegrationTest subject = new CompositeIntegrationTest() {
        };
        assertNotNull(subject);
    }

    @Test
    @DisplayName("initializeContainers should start all four containers in order via the shared registry")
    void testInitializeContainers() throws Exception {
        try (MockedConstruction<PostgreSQLContainer> pg = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
                    when(mock.isRunning()).thenReturn(false);
                });
             MockedConstruction<MongoDBContainer> mongo = mockConstruction(MongoDBContainer.class,
                     (mock, ctx) -> {
                         when(mock.withReuse(anyBoolean())).thenReturn(mock);
                         when(mock.getReplicaSetUrl()).thenReturn("mongodb://localhost:27017/test");
                         when(mock.isRunning()).thenReturn(false);
                     });
             MockedConstruction<GenericContainer> redis = mockConstruction(GenericContainer.class,
                     (mock, ctx) -> {
                         when(mock.withExposedPorts(anyInt())).thenReturn(mock);
                         when(mock.withReuse(anyBoolean())).thenReturn(mock);
                         when(mock.getHost()).thenReturn("localhost");
                         when(mock.getMappedPort(6379)).thenReturn(6379);
                         when(mock.isRunning()).thenReturn(false);
                     });
             MockedConstruction<ConfluentKafkaContainer> kafka = mockConstruction(ConfluentKafkaContainer.class,
                     (mock, ctx) -> {
                         when(mock.withReuse(anyBoolean())).thenReturn(mock);
                         when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://localhost:9092");
                         when(mock.isRunning()).thenReturn(false);
                     })) {

            CompositeIntegrationTest.initializeContainers();

            verify(pg.constructed().get(0)).start();
            verify(mongo.constructed().get(0)).start();
            verify(redis.constructed().get(0)).start();
            verify(kafka.constructed().get(0)).start();

            TestContainerRegistry registry = TestContainerRegistry.getInstance();
            assertEquals(List.of("postgres", "mongo", "redis", "kafka"), registry.registrationOrder());
        }
    }

    @Test
    @DisplayName("configureProperties should register properties for all four services")
    void testConfigureProperties() throws Exception {
        try (MockedConstruction<PostgreSQLContainer> pg = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
                    when(mock.getUsername()).thenReturn("test");
                    when(mock.getPassword()).thenReturn("test");
                });
             MockedConstruction<MongoDBContainer> mongo = mockConstruction(MongoDBContainer.class,
                     (mock, ctx) -> {
                         when(mock.withReuse(anyBoolean())).thenReturn(mock);
                         when(mock.getReplicaSetUrl()).thenReturn("mongodb://localhost:27017/test");
                     });
             MockedConstruction<GenericContainer> redis = mockConstruction(GenericContainer.class,
                     (mock, ctx) -> {
                         when(mock.withExposedPorts(anyInt())).thenReturn(mock);
                         when(mock.withReuse(anyBoolean())).thenReturn(mock);
                         when(mock.getHost()).thenReturn("localhost");
                         when(mock.getMappedPort(6379)).thenReturn(6379);
                     });
             MockedConstruction<ConfluentKafkaContainer> kafka = mockConstruction(ConfluentKafkaContainer.class,
                     (mock, ctx) -> {
                         when(mock.withReuse(anyBoolean())).thenReturn(mock);
                         when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://localhost:9092");
                     })) {

            CapturingRegistry registry = new CapturingRegistry();
            CompositeIntegrationTest.configureProperties(registry);

            assertEquals("jdbc:postgresql://localhost:5432/testdb", registry.props.get("spring.datasource.url").get());
            assertEquals("test", registry.props.get("spring.datasource.username").get());
            assertEquals("mongodb://localhost:27017/test", registry.props.get("spring.data.mongodb.uri").get());
            assertEquals("localhost", registry.props.get("spring.data.redis.host").get());
            assertEquals(6379, registry.props.get("spring.data.redis.port").get());
            assertEquals("PLAINTEXT://localhost:9092", registry.props.get("spring.kafka.bootstrap-servers").get());
            assertEquals("create-drop", registry.props.get("spring.jpa.hibernate.ddl-auto").get());
        }
    }
}
