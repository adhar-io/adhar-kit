package com.adhar.kit.test;

import com.adhar.kit.test.api.TestContainerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TestContainerFacade}.
 *
 * <p>Real Testcontainers are never started. Container construction is intercepted with
 * {@link MockedConstruction} and pre-built mocks are injected via reflection, so the
 * facade's orchestration logic is exercised without Docker.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@DisplayName("TestContainerFacade Tests")
class TestContainerFacadeTest {

    // ---- reflection helpers -------------------------------------------------

    private TestContainerFacade newFacade() throws Exception {
        Constructor<TestContainerFacade> ctor = TestContainerFacade.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private void setField(TestContainerFacade facade, String name, Object value) throws Exception {
        Field f = TestContainerFacade.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(facade, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> containers(TestContainerFacade facade) throws Exception {
        Field f = TestContainerFacade.class.getDeclaredField("containers");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(facade);
    }

    // ---- singleton ----------------------------------------------------------

    @Test
    @DisplayName("getInstance should return a singleton")
    void testGetInstanceSingleton() {
        TestContainerService a = TestContainerFacade.getInstance();
        TestContainerService b = TestContainerFacade.getInstance();
        assertNotNull(a);
        assertSame(a, b, "getInstance should always return the same instance");
    }

    // ---- PostgreSQL ---------------------------------------------------------

    @Test
    @DisplayName("startPostgres should create and start a PostgreSQL container")
    void testStartPostgresFullPath() throws Exception {
        TestContainerFacade facade = newFacade();
        try (MockedConstruction<PostgreSQLContainer> mocked = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/test");
                })) {

            String url = facade.startPostgres();

            assertEquals("jdbc:postgresql://localhost:5432/test", url);
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).start();
            assertSame(mocked.constructed().get(0), containers(facade).get("postgres"));
        }
    }

    @Test
    @DisplayName("startPostgres should return existing url when already running")
    void testStartPostgresAlreadyRunning() throws Exception {
        TestContainerFacade facade = newFacade();
        PostgreSQLContainer<?> running = mock(PostgreSQLContainer.class);
        when(running.isRunning()).thenReturn(true);
        when(running.getJdbcUrl()).thenReturn("jdbc:postgresql://cached");
        setField(facade, "postgresContainer", running);

        String url = facade.startPostgres();

        assertEquals("jdbc:postgresql://cached", url);
        verify(running, never()).start();
    }

    @Test
    @DisplayName("getPostgresJdbcUrl should return null when not started and url when running")
    void testGetPostgresJdbcUrl() throws Exception {
        TestContainerFacade facade = newFacade();
        assertNull(facade.getPostgresJdbcUrl());

        PostgreSQLContainer<?> mock = mock(PostgreSQLContainer.class);
        when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://x");
        setField(facade, "postgresContainer", mock);
        assertEquals("jdbc:postgresql://x", facade.getPostgresJdbcUrl());
    }

    // ---- MongoDB ------------------------------------------------------------

    @Test
    @DisplayName("startMongo should create and start a MongoDB container")
    void testStartMongoFullPath() throws Exception {
        TestContainerFacade facade = newFacade();
        try (MockedConstruction<MongoDBContainer> mocked = mockConstruction(MongoDBContainer.class,
                (mock, ctx) -> when(mock.getReplicaSetUrl()).thenReturn("mongodb://localhost:27017/test"))) {

            String url = facade.startMongo();

            assertEquals("mongodb://localhost:27017/test", url);
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).start();
        }
    }

    @Test
    @DisplayName("startMongo should return existing url when already running")
    void testStartMongoAlreadyRunning() throws Exception {
        TestContainerFacade facade = newFacade();
        MongoDBContainer running = mock(MongoDBContainer.class);
        when(running.isRunning()).thenReturn(true);
        when(running.getReplicaSetUrl()).thenReturn("mongodb://cached");
        setField(facade, "mongoContainer", running);

        assertEquals("mongodb://cached", facade.startMongo("6.0"));
        verify(running, never()).start();
    }

    @Test
    @DisplayName("getMongoConnectionString should return null when not started and url when running")
    void testGetMongoConnectionString() throws Exception {
        TestContainerFacade facade = newFacade();
        assertNull(facade.getMongoConnectionString());

        MongoDBContainer mock = mock(MongoDBContainer.class);
        when(mock.getReplicaSetUrl()).thenReturn("mongodb://y");
        setField(facade, "mongoContainer", mock);
        assertEquals("mongodb://y", facade.getMongoConnectionString());
    }

    // ---- Redis --------------------------------------------------------------

    @Test
    @DisplayName("startRedis should create and start a Redis container")
    void testStartRedisFullPath() throws Exception {
        TestContainerFacade facade = newFacade();
        try (MockedConstruction<GenericContainer> mocked = mockConstruction(GenericContainer.class,
                (mock, ctx) -> {
                    when(mock.withExposedPorts(anyInt())).thenReturn(mock);
                    when(mock.getHost()).thenReturn("localhost");
                    when(mock.getMappedPort(6379)).thenReturn(6379);
                })) {

            String url = facade.startRedis();

            assertEquals("redis://localhost:6379", url);
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).start();
        }
    }

    @Test
    @DisplayName("startRedis should return existing url when already running")
    void testStartRedisAlreadyRunning() throws Exception {
        TestContainerFacade facade = newFacade();
        GenericContainer<?> running = mock(GenericContainer.class);
        when(running.isRunning()).thenReturn(true);
        when(running.getHost()).thenReturn("redishost");
        when(running.getMappedPort(6379)).thenReturn(6380);
        setField(facade, "redisContainer", running);

        assertEquals("redis://redishost:6380", facade.startRedis("7.2"));
        verify(running, never()).start();
    }

    @Test
    @DisplayName("getRedisUrl should return null when not started and url when running")
    void testGetRedisUrl() throws Exception {
        TestContainerFacade facade = newFacade();
        assertNull(facade.getRedisUrl());

        GenericContainer<?> mock = mock(GenericContainer.class);
        when(mock.getHost()).thenReturn("h");
        when(mock.getMappedPort(6379)).thenReturn(1234);
        setField(facade, "redisContainer", mock);
        assertEquals("redis://h:1234", facade.getRedisUrl());
    }

    // ---- Kafka --------------------------------------------------------------

    @Test
    @DisplayName("startKafka should create and start a Kafka container")
    void testStartKafkaFullPath() throws Exception {
        TestContainerFacade facade = newFacade();
        try (MockedConstruction<ConfluentKafkaContainer> mocked = mockConstruction(ConfluentKafkaContainer.class,
                (mock, ctx) -> when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://localhost:9092"))) {

            String servers = facade.startKafka();

            assertEquals("PLAINTEXT://localhost:9092", servers);
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).start();
        }
    }

    @Test
    @DisplayName("startKafka should return existing servers when already running")
    void testStartKafkaAlreadyRunning() throws Exception {
        TestContainerFacade facade = newFacade();
        ConfluentKafkaContainer running = mock(ConfluentKafkaContainer.class);
        when(running.isRunning()).thenReturn(true);
        when(running.getBootstrapServers()).thenReturn("PLAINTEXT://cached:9092");
        setField(facade, "kafkaContainer", running);

        assertEquals("PLAINTEXT://cached:9092", facade.startKafka());
        verify(running, never()).start();
    }

    @Test
    @DisplayName("getKafkaBootstrapServers should return null when not started and servers when running")
    void testGetKafkaBootstrapServers() throws Exception {
        TestContainerFacade facade = newFacade();
        assertNull(facade.getKafkaBootstrapServers());

        ConfluentKafkaContainer mock = mock(ConfluentKafkaContainer.class);
        when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://z:9092");
        setField(facade, "kafkaContainer", mock);
        assertEquals("PLAINTEXT://z:9092", facade.getKafkaBootstrapServers());
    }

    // ---- stop / stopAll / isRunning ----------------------------------------

    @Test
    @DisplayName("stop should stop and remove a known container")
    void testStopKnownContainer() throws Exception {
        TestContainerFacade facade = newFacade();
        GenericContainer<?> mock = mock(GenericContainer.class);
        containers(facade).put("redis", mock);

        facade.stop("redis");

        verify(mock).stop();
        assertFalse(containers(facade).containsKey("redis"));
    }

    @Test
    @DisplayName("stop should be a no-op for an unknown container type")
    void testStopUnknownContainer() throws Exception {
        TestContainerFacade facade = newFacade();
        assertDoesNotThrow(() -> facade.stop("nope"));
    }

    @Test
    @DisplayName("stop should ignore entries that are not GenericContainers")
    void testStopNonGenericContainer() throws Exception {
        TestContainerFacade facade = newFacade();
        containers(facade).put("weird", new Object());

        facade.stop("weird");

        // Non-GenericContainer entries are left untouched
        assertTrue(containers(facade).containsKey("weird"));
    }

    @Test
    @DisplayName("stopAll should stop every GenericContainer and clear the map")
    void testStopAll() throws Exception {
        TestContainerFacade facade = newFacade();
        GenericContainer<?> c1 = mock(GenericContainer.class);
        GenericContainer<?> c2 = mock(GenericContainer.class);
        containers(facade).put("redis", c1);
        containers(facade).put("postgres", c2);
        containers(facade).put("other", new Object());

        facade.stopAll();

        verify(c1).stop();
        verify(c2).stop();
        assertTrue(containers(facade).isEmpty(), "All containers should be cleared");
    }

    @Test
    @DisplayName("isRunning should reflect the container state")
    void testIsRunning() throws Exception {
        TestContainerFacade facade = newFacade();
        assertFalse(facade.isRunning("redis"), "Unknown type should be reported as not running");

        GenericContainer<?> running = mock(GenericContainer.class);
        when(running.isRunning()).thenReturn(true);
        containers(facade).put("redis", running);
        assertTrue(facade.isRunning("redis"));

        containers(facade).put("weird", new Object());
        assertFalse(facade.isRunning("weird"), "Non-GenericContainer should be reported as not running");
    }

    // ---- getAllContainers ---------------------------------------------------

    @Test
    @DisplayName("getAllContainers should be empty when nothing is running")
    void testGetAllContainersEmpty() throws Exception {
        TestContainerFacade facade = newFacade();
        assertTrue(facade.getAllContainers().isEmpty());
    }

    @Test
    @DisplayName("getAllContainers should report every running container")
    void testGetAllContainersPopulated() throws Exception {
        TestContainerFacade facade = newFacade();

        PostgreSQLContainer<?> pg = mock(PostgreSQLContainer.class);
        when(pg.isRunning()).thenReturn(true);
        when(pg.getJdbcUrl()).thenReturn("jdbc:postgresql://pg");
        setField(facade, "postgresContainer", pg);

        MongoDBContainer mongo = mock(MongoDBContainer.class);
        when(mongo.isRunning()).thenReturn(true);
        when(mongo.getReplicaSetUrl()).thenReturn("mongodb://m");
        setField(facade, "mongoContainer", mongo);

        GenericContainer<?> redis = mock(GenericContainer.class);
        when(redis.isRunning()).thenReturn(true);
        when(redis.getHost()).thenReturn("rh");
        when(redis.getMappedPort(6379)).thenReturn(6379);
        setField(facade, "redisContainer", redis);

        ConfluentKafkaContainer kafka = mock(ConfluentKafkaContainer.class);
        when(kafka.isRunning()).thenReturn(true);
        when(kafka.getBootstrapServers()).thenReturn("PLAINTEXT://k:9092");
        setField(facade, "kafkaContainer", kafka);

        Map<String, String> all = facade.getAllContainers();

        assertEquals(4, all.size());
        assertEquals("jdbc:postgresql://pg", all.get("postgres"));
        assertEquals("mongodb://m", all.get("mongo"));
        assertEquals("redis://rh:6379", all.get("redis"));
        assertEquals("PLAINTEXT://k:9092", all.get("kafka"));
    }
}
