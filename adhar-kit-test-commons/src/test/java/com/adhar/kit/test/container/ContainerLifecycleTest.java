package com.adhar.kit.test.container;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lifecycle tests for the static container helpers
 * ({@link PostgresTestContainer}, {@link MongoTestContainer},
 * {@link RedisTestContainer}, {@link KafkaTestContainer}).
 *
 * <p>Container construction is intercepted with {@link MockedConstruction} so {@code start()},
 * the connection accessors, and {@code stop()} are exercised without Docker. Each helper's
 * static singleton field is reset to {@code null} before and after every test so neither these
 * nor the sibling test classes are polluted by injected mocks.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("Container Lifecycle Tests")
class ContainerLifecycleTest {

    private void resetStatic(Class<?> clazz) throws Exception {
        Field f = clazz.getDeclaredField("container");
        f.setAccessible(true);
        f.set(null, null);
    }

    @AfterEach
    void cleanup() throws Exception {
        resetStatic(PostgresTestContainer.class);
        resetStatic(MongoTestContainer.class);
        resetStatic(RedisTestContainer.class);
        resetStatic(KafkaTestContainer.class);
    }

    @Test
    @DisplayName("PostgresTestContainer start/accessors/stop")
    void testPostgresLifecycle() throws Exception {
        resetStatic(PostgresTestContainer.class);
        try (MockedConstruction<PostgreSQLContainer> mocked = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
                    when(mock.getUsername()).thenReturn("test");
                    when(mock.getPassword()).thenReturn("test");
                    when(mock.isRunning()).thenReturn(true);
                })) {

            PostgresTestContainer.start();

            assertEquals("jdbc:postgresql://localhost:5432/testdb", PostgresTestContainer.getJdbcUrl());
            assertEquals("test", PostgresTestContainer.getUsername());
            assertEquals("test", PostgresTestContainer.getPassword());

            PostgreSQLContainer<?> created = mocked.constructed().get(0);
            verify(created).start();

            PostgresTestContainer.stop();
            verify(created).stop();
        }
    }

    @Test
    @DisplayName("MongoTestContainer start/accessors/stop")
    void testMongoLifecycle() throws Exception {
        resetStatic(MongoTestContainer.class);
        try (MockedConstruction<MongoDBContainer> mocked = mockConstruction(MongoDBContainer.class,
                (mock, ctx) -> {
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getReplicaSetUrl()).thenReturn("mongodb://localhost:27017/test");
                    when(mock.isRunning()).thenReturn(true);
                })) {

            MongoTestContainer.start();

            assertEquals("mongodb://localhost:27017/test", MongoTestContainer.getConnectionString());

            MongoDBContainer created = mocked.constructed().get(0);
            verify(created).start();

            MongoTestContainer.stop();
            verify(created).stop();
        }
    }

    @Test
    @DisplayName("RedisTestContainer start/accessors/stop")
    void testRedisLifecycle() throws Exception {
        resetStatic(RedisTestContainer.class);
        try (MockedConstruction<GenericContainer> mocked = mockConstruction(GenericContainer.class,
                (mock, ctx) -> {
                    when(mock.withExposedPorts(anyInt())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getHost()).thenReturn("localhost");
                    when(mock.getMappedPort(6379)).thenReturn(6379);
                    when(mock.isRunning()).thenReturn(true);
                })) {

            RedisTestContainer.start();

            assertEquals("localhost", RedisTestContainer.getHost());
            assertEquals(6379, RedisTestContainer.getPort().intValue());
            assertEquals("redis://localhost:6379", RedisTestContainer.getConnectionUrl());

            GenericContainer<?> created = mocked.constructed().get(0);
            verify(created).start();

            RedisTestContainer.stop();
            verify(created).stop();
        }
    }

    @Test
    @DisplayName("KafkaTestContainer start/accessors/stop")
    void testKafkaLifecycle() throws Exception {
        resetStatic(KafkaTestContainer.class);
        try (MockedConstruction<ConfluentKafkaContainer> mocked = mockConstruction(ConfluentKafkaContainer.class,
                (mock, ctx) -> {
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://localhost:9092");
                    when(mock.isRunning()).thenReturn(true);
                })) {

            KafkaTestContainer.start();

            assertEquals("PLAINTEXT://localhost:9092", KafkaTestContainer.getBootstrapServers());

            ConfluentKafkaContainer created = mocked.constructed().get(0);
            verify(created).start();

            KafkaTestContainer.stop();
            verify(created).stop();
        }
    }
}
