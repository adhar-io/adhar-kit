package com.adhar.kit.test.api;

import java.util.Map;

/**
 * Framework-agnostic Test Container Service API.
 *
 * <p>This interface provides a unified abstraction for managing test containers
 * (Testcontainers) across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Common Use Cases:</b></p>
 * <ul>
 *   <li>PostgreSQL database for integration tests</li>
 *   <li>MongoDB for document store testing</li>
 *   <li>Redis for cache testing</li>
 *   <li>Kafka for messaging testing</li>
 *   <li>LocalStack for AWS services</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // In test class
 * @TestInstance(Lifecycle.PER_CLASS)
 * class OrderServiceIntegrationTest {
 *
 *     private TestContainerService containers = TestContainerFacade.getInstance();
 *
 *     @BeforeAll
 *     void setup() {
 *         containers.startPostgres();
 *         containers.startRedis();
 *     }
 *
 *     @Test
 *     void testOrderCreation() {
 *         String jdbcUrl = containers.getPostgresJdbcUrl();
 *         // Use in test...
 *     }
 *
 *     @AfterAll
 *     void cleanup() {
 *         containers.stopAll();
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.test.TestContainerFacade
 */
public interface TestContainerService {

    /**
     * Starts a PostgreSQL container for testing.
     *
     * @return JDBC URL for connecting to the PostgreSQL instance
     */
    String startPostgres();

    /**
     * Starts a PostgreSQL container with custom configuration.
     *
     * @param version PostgreSQL version (e.g., "15.3")
     * @param database database name
     * @param username username
     * @param password password
     * @return JDBC URL for connecting
     */
    String startPostgres(String version, String database, String username, String password);

    /**
     * Gets the PostgreSQL JDBC URL.
     *
     * @return JDBC URL or null if not started
     */
    String getPostgresJdbcUrl();

    /**
     * Starts a MongoDB container for testing.
     *
     * @return MongoDB connection string
     */
    String startMongo();

    /**
     * Starts a MongoDB container with custom version.
     *
     * @param version MongoDB version (e.g., "6.0")
     * @return MongoDB connection string
     */
    String startMongo(String version);

    /**
     * Gets the MongoDB connection string.
     *
     * @return connection string or null if not started
     */
    String getMongoConnectionString();

    /**
     * Starts a Redis container for testing.
     *
     * @return Redis connection URL
     */
    String startRedis();

    /**
     * Starts a Redis container with custom version.
     *
     * @param version Redis version (e.g., "7.2")
     * @return Redis connection URL
     */
    String startRedis(String version);

    /**
     * Gets the Redis connection URL.
     *
     * @return connection URL or null if not started
     */
    String getRedisUrl();

    /**
     * Starts a Kafka container for testing.
     *
     * @return Kafka bootstrap servers
     */
    String startKafka();

    /**
     * Gets the Kafka bootstrap servers.
     *
     * @return bootstrap servers or null if not started
     */
    String getKafkaBootstrapServers();

    /**
     * Stops a specific container.
     *
     * @param containerType type of container (postgres, mongo, redis, kafka)
     */
    void stop(String containerType);

    /**
     * Stops all running containers.
     */
    void stopAll();

    /**
     * Checks if a container is running.
     *
     * @param containerType type of container
     * @return true if running, false otherwise
     */
    boolean isRunning(String containerType);

    /**
     * Gets all running container information.
     *
     * @return map of container type to connection info
     */
    Map<String, String> getAllContainers();
}

