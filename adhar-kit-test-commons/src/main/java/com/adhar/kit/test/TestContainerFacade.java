package com.adhar.kit.test;

import com.adhar.kit.test.api.TestContainerService;
import com.adhar.kit.test.container.TestContainerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Test Container facade for integration testing.
 *
 * <p>Provides simplified Testcontainers integration across all frameworks.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * TestContainerFacade containers = TestContainerFacade.getInstance();
 *
 * // Start containers
 * String jdbcUrl = containers.startPostgres();
 * String mongoUrl = containers.startMongo();
 * String redisUrl = containers.startRedis();
 *
 * // Use in tests...
 *
 * // Cleanup
 * containers.stopAll();
 * }</pre>
 *
 * <p>Every container started through this facade is also registered with the shared
 * {@link TestContainerRegistry}, so facade-managed containers and containers started through
 * the {@code base.*IntegrationTest} classes share one lifecycle bookkeeping mechanism.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class TestContainerFacade implements TestContainerService {

    private static volatile TestContainerFacade instance;

    private final Map<String, Object> containers = new ConcurrentHashMap<>();
    private PostgreSQLContainer<?> postgresContainer;
    private MongoDBContainer mongoContainer;
    private GenericContainer<?> redisContainer;
    private ConfluentKafkaContainer kafkaContainer;

    private TestContainerFacade() {
        log.info("Initialized TestContainerFacade");
    }

    public static TestContainerFacade getInstance() {
        if (instance == null) {
            synchronized (TestContainerFacade.class) {
                if (instance == null) {
                    instance = new TestContainerFacade();
                }
            }
        }
        return instance;
    }

    @Override
    public String startPostgres() {
        return startPostgres("15.3", "test", "test", "test");
    }

    @Override
    public String startPostgres(String version, String database, String username, String password) {
        if (postgresContainer != null && postgresContainer.isRunning()) {
            log.debug("PostgreSQL container already running");
            return postgresContainer.getJdbcUrl();
        }

        log.info("Starting PostgreSQL {} container", version);
        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:" + version))
                .withDatabaseName(database)
                .withUsername(username)
                .withPassword(password);

        postgresContainer.start();
        containers.put("postgres", postgresContainer);
        TestContainerRegistry.getInstance().register("postgres", postgresContainer);

        String jdbcUrl = postgresContainer.getJdbcUrl();
        log.info("PostgreSQL started at {}", jdbcUrl);
        return jdbcUrl;
    }

    @Override
    public String getPostgresJdbcUrl() {
        return postgresContainer != null ? postgresContainer.getJdbcUrl() : null;
    }

    @Override
    public String startMongo() {
        return startMongo("6.0");
    }

    @Override
    public String startMongo(String version) {
        if (mongoContainer != null && mongoContainer.isRunning()) {
            log.debug("MongoDB container already running");
            return mongoContainer.getReplicaSetUrl();
        }

        log.info("Starting MongoDB {} container", version);
        mongoContainer = new MongoDBContainer(DockerImageName.parse("mongo:" + version));
        mongoContainer.start();
        containers.put("mongo", mongoContainer);
        TestContainerRegistry.getInstance().register("mongo", mongoContainer);

        String connectionString = mongoContainer.getReplicaSetUrl();
        log.info("MongoDB started at {}", connectionString);
        return connectionString;
    }

    @Override
    public String getMongoConnectionString() {
        return mongoContainer != null ? mongoContainer.getReplicaSetUrl() : null;
    }

    @Override
    public String startRedis() {
        return startRedis("7.2");
    }

    @Override
    public String startRedis(String version) {
        if (redisContainer != null && redisContainer.isRunning()) {
            log.debug("Redis container already running");
            return getRedisUrl();
        }

        log.info("Starting Redis {} container", version);
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:" + version))
                .withExposedPorts(6379);

        redisContainer.start();
        containers.put("redis", redisContainer);
        TestContainerRegistry.getInstance().register("redis", redisContainer);

        String url = getRedisUrl();
        log.info("Redis started at {}", url);
        return url;
    }

    @Override
    public String getRedisUrl() {
        if (redisContainer == null) return null;
        return "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);
    }

    @Override
    public String startKafka() {
        if (kafkaContainer != null && kafkaContainer.isRunning()) {
            log.debug("Kafka container already running");
            return kafkaContainer.getBootstrapServers();
        }

        log.info("Starting Kafka container");
        kafkaContainer = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
        kafkaContainer.start();
        containers.put("kafka", kafkaContainer);
        TestContainerRegistry.getInstance().register("kafka", kafkaContainer);

        String bootstrapServers = kafkaContainer.getBootstrapServers();
        log.info("Kafka started at {}", bootstrapServers);
        return bootstrapServers;
    }

    @Override
    public String getKafkaBootstrapServers() {
        return kafkaContainer != null ? kafkaContainer.getBootstrapServers() : null;
    }

    @Override
    public void stop(String containerType) {
        Object container = containers.get(containerType);
        if (container instanceof GenericContainer) {
            ((GenericContainer<?>) container).stop();
            containers.remove(containerType);
            TestContainerRegistry.getInstance().unregister(containerType);
            log.info("Stopped {} container", containerType);
        }
    }

    @Override
    public void stopAll() {
        log.info("Stopping all containers");
        containers.forEach((name, container) -> {
            if (container instanceof GenericContainer) {
                ((GenericContainer<?>) container).stop();
            }
            TestContainerRegistry.getInstance().unregister(name);
        });
        containers.clear();
        log.info("All containers stopped");
    }

    @Override
    public boolean isRunning(String containerType) {
        Object container = containers.get(containerType);
        return container instanceof GenericContainer && ((GenericContainer<?>) container).isRunning();
    }

    @Override
    public Map<String, String> getAllContainers() {
        Map<String, String> info = new HashMap<>();
        if (postgresContainer != null && postgresContainer.isRunning()) {
            info.put("postgres", getPostgresJdbcUrl());
        }
        if (mongoContainer != null && mongoContainer.isRunning()) {
            info.put("mongo", getMongoConnectionString());
        }
        if (redisContainer != null && redisContainer.isRunning()) {
            info.put("redis", getRedisUrl());
        }
        if (kafkaContainer != null && kafkaContainer.isRunning()) {
            info.put("kafka", getKafkaBootstrapServers());
        }
        return info;
    }
}

