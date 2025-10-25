package com.adhar.adharkit.cache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Adhar Cache.
 * <p>
 * This class provides a comprehensive set of configuration options for the Adhar cache system.
 * It supports both local caching and distributed caching via Kafka with sensible defaults
 * for all options.
 * <p>
 * The configuration is organized hierarchically, allowing for fine-grained control over
 * different aspects of the caching system.
 */
@Data
@ConfigurationProperties(prefix = "adhar.cache")
public class AdharCacheProperties {

    /**
     * Whether to enable the Adhar cache features.
     */
    private boolean enabled = true;

    /**
     * Default time-to-live for cache entries.
     */
    private Duration timeToLive = Duration.ofMinutes(30);

    /**
     * Default maximum size of the cache.
     */
    private long maxSize = 1000;

    /**
     * Whether to allow null values in the cache.
     */
    private boolean allowNullValues = true;

    /**
     * Configuration for Kafka-based distributed caching.
     */
    private final KafkaProperties kafka = new KafkaProperties();

    /**
     * Configuration for Redis-based distributed caching.
     */
    private final RedisProperties redis = new RedisProperties();

    /**
     * Properties for Kafka-based distributed caching.
     */
    @Data
    public static class KafkaProperties {
        /**
         * Whether to enable Kafka-based distributed caching.
         */
        private boolean enabled = true;

        /**
         * Bootstrap servers for Kafka.
         */
        private String bootstrapServers = "localhost:9092";

        /**
         * Group ID for the Kafka consumer.
         */
        private String groupId = "adhar-cache-group";

        /**
         * Prefix for Kafka topics used for cache operations.
         */
        private String topicPrefix = "adhar-cache";

        /**
         * Number of partitions for Kafka topics.
         */
        private int partitions = 3;

        /**
         * Replication factor for Kafka topics.
         */
        private short replicationFactor = 1;

        /**
         * Whether to automatically create Kafka topics.
         */
        private boolean autoCreateTopics = true;

        /**
         * Additional properties for Kafka client.
         */
        private Map<String, String> properties = new HashMap<>();
    }

    /**
     * Properties for Redis-based distributed caching.
     */
    @Data
    public static class RedisProperties {
        /**
         * Whether to enable Redis-based distributed caching.
         */
        private boolean enabled = true;

        /**
         * Redis host.
         */
        private String host = "localhost";

        /**
         * Redis port.
         */
        private int port = 6379;

        /**
         * Redis password.
         */
        private String password = null;

        /**
         * Redis database index.
         */
        private int database = 0;

        /**
         * Whether to use SSL for Redis connection.
         */
        private boolean ssl = false;

        /**
         * Connection timeout for Redis.
         */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * Read timeout for Redis.
         */
        private Duration readTimeout = Duration.ofSeconds(5);

        /**
         * Key prefix for Redis cache keys.
         */
        private String keyPrefix = "adhar-cache:";

        /**
         * Whether to use Redis key expiration events for cache synchronization.
         */
        private boolean useKeyExpirationEvents = true;

        /**
         * Additional properties for Redis client.
         */
        private Map<String, String> properties = new HashMap<>();
    }
}