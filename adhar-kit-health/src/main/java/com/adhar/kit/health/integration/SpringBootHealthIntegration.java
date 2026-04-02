package com.adhar.kit.health.integration;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.indicator.*;
import com.adhar.kit.health.registry.HealthRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring Boot integration for Adhar Health.
 *
 * <p>Provides automatic health check configuration for Spring Boot applications
 * with classpath-based auto-detection of health indicators.</p>
 *
 * <p><b>Auto-detected indicators:</b></p>
 * <ul>
 *   <li><b>Redis</b> - When spring-data-redis is on classpath</li>
 *   <li><b>Kafka</b> - When kafka-clients is on classpath</li>
 *   <li><b>MongoDB</b> - When mongodb-driver-sync is on classpath</li>
 *   <li><b>Elasticsearch</b> - When elasticsearch-java is on classpath</li>
 *   <li><b>gRPC</b> - When grpc-services is on classpath</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Configuration
 * public class HealthConfig {
 *
 *     @Bean
 *     public HealthRegistry healthRegistry(
 *             Collection<AdharHealthIndicator> indicators,
 *             AdharHealthProperties properties,
 *             Optional<RedisConnectionFactory> redisConnectionFactory,
 *             Optional<AdminClient> kafkaAdminClient,
 *             Optional<MongoClient> mongoClient,
 *             Optional<ElasticsearchClient> esClient,
 *             Optional<ManagedChannel> grpcChannel) {
 *
 *         return SpringBootHealthIntegration.createHealthRegistryWithAutoConfig(
 *             indicators,
 *             properties,
 *             redisConnectionFactory.orElse(null),
 *             kafkaAdminClient.orElse(null),
 *             mongoClient.orElse(null),
 *             esClient.orElse(null),
 *             grpcChannel.orElse(null)
 *         );
 *     }
 * }
 *
 * // Custom indicator
 * @HealthIndicator(name = "payment")
 * @Component
 * public class PaymentHealthIndicator implements AdharHealthIndicator {
 *     @Override
 *     public Health check() {
 *         return Health.up().build();
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "payment";
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SpringBootHealthIntegration {

    /**
     * Creates health registry for Spring Boot.
     *
     * @param indicators collection of health indicators
     * @return configured health registry
     */
    public static HealthRegistry createHealthRegistry(Collection<AdharHealthIndicator> indicators) {
        log.info("Creating health registry for Spring Boot");

        HealthRegistry registry = new HealthRegistry();

        // Register all discovered indicators
        for (AdharHealthIndicator indicator : indicators) {
            registry.register(indicator);
        }

        log.info("Registered {} health indicators", indicators.size());

        return registry;
    }

    /**
     * Creates health registry with auto-configured indicators based on classpath detection.
     *
     * @param indicators collection of custom health indicators
     * @param properties health configuration properties
     * @param redisConnectionFactory Redis connection factory (optional)
     * @param kafkaAdminClient Kafka admin client (optional)
     * @param mongoClient MongoDB client (optional)
     * @param elasticsearchClient Elasticsearch client (optional)
     * @param grpcChannel gRPC managed channel (optional)
     * @return configured health registry with auto-detected indicators
     */
    public static HealthRegistry createHealthRegistryWithAutoConfig(
            Collection<AdharHealthIndicator> indicators,
            AdharHealthProperties properties,
            Object redisConnectionFactory,
            Object kafkaAdminClient,
            Object mongoClient,
            Object elasticsearchClient,
            Object grpcChannel) {

        log.info("Creating health registry with auto-configuration for Spring Boot");

        List<AdharHealthIndicator> allIndicators = new ArrayList<>(indicators);

        // Auto-configure Redis health indicator
        if (redisConnectionFactory != null && isRedisAvailable()) {
            log.info("Auto-configuring Redis health indicator");
            allIndicators.add(new RedisHealthIndicator(redisConnectionFactory, properties.getRedis()));
        }

        // Auto-configure Kafka health indicator
        if (kafkaAdminClient != null && isKafkaAvailable()) {
            log.info("Auto-configuring Kafka health indicator");
            allIndicators.add(new KafkaHealthIndicator(kafkaAdminClient, properties.getKafka()));
        }

        // Auto-configure MongoDB health indicator
        if (mongoClient != null && isMongoAvailable()) {
            log.info("Auto-configuring MongoDB health indicator");
            allIndicators.add(new MongoHealthIndicator(mongoClient, properties.getMongo()));
        }

        // Auto-configure Elasticsearch health indicator
        if (elasticsearchClient != null && isElasticsearchAvailable()) {
            log.info("Auto-configuring Elasticsearch health indicator");
            allIndicators.add(new ElasticsearchHealthIndicator(elasticsearchClient, properties.getElasticsearch()));
        }

        // Auto-configure gRPC health indicator
        if (grpcChannel != null && isGrpcAvailable()) {
            log.info("Auto-configuring gRPC health indicator");
            allIndicators.add(new GrpcHealthIndicator(grpcChannel, properties.getGrpc()));
        }

        return createHealthRegistry(allIndicators);
    }

    /**
     * Checks if Spring Boot is available.
     *
     * @return true if Spring Boot is on classpath
     */
    public static boolean isSpringBootAvailable() {
        try {
            Class.forName("org.springframework.boot.SpringApplication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if Redis is available on the classpath.
     *
     * @return true if Spring Data Redis is available
     */
    public static boolean isRedisAvailable() {
        try {
            Class.forName("org.springframework.data.redis.connection.RedisConnectionFactory");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if Kafka is available on the classpath.
     *
     * @return true if Kafka clients are available
     */
    public static boolean isKafkaAvailable() {
        try {
            Class.forName("org.apache.kafka.clients.admin.AdminClient");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if MongoDB is available on the classpath.
     *
     * @return true if MongoDB driver is available
     */
    public static boolean isMongoAvailable() {
        try {
            Class.forName("com.mongodb.client.MongoClient");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if Elasticsearch is available on the classpath.
     *
     * @return true if Elasticsearch client is available
     */
    public static boolean isElasticsearchAvailable() {
        try {
            Class.forName("co.elastic.clients.elasticsearch.ElasticsearchClient");
            return true;
        } catch (ClassNotFoundException e) {
            // Try legacy RestHighLevelClient
            try {
                Class.forName("org.elasticsearch.client.RestHighLevelClient");
                return true;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }
    }

    /**
     * Checks if gRPC is available on the classpath.
     *
     * @return true if gRPC services are available
     */
    public static boolean isGrpcAvailable() {
        try {
            Class.forName("io.grpc.health.v1.HealthGrpc");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

