package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.adharkit.cache.service.KafkaCacheListener;
import com.adhar.adharkit.cache.service.KafkaCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for Adhar Cache.
 * <p>
 * This class provides auto-configuration for the Adhar cache system, including:
 * - Configuration properties
 * - Kafka configuration for cache synchronization
 * - Cache manager
 * - Cache listeners
 * <p>
 * The auto-configuration is conditionally enabled based on the "adhar.cache.enabled" property.
 */
@Slf4j
@AutoConfiguration
@EnableKafka
@EnableConfigurationProperties(AdharCacheProperties.class)
@ConditionalOnProperty(prefix = "adhar.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdharCacheAutoConfiguration {

    /**
     * Creates a Kafka admin client for topic management.
     *
     * @param properties the cache properties
     * @return the Kafka admin client factory
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin kafkaAdmin(AdharCacheProperties properties) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());

        // Add any additional properties
        configs.putAll(properties.getKafka().getProperties());

        return new KafkaAdmin(configs);
    }

    /**
     * Creates the Kafka topics for cache operations if they don't exist.
     *
     * @param properties the cache properties
     * @return the Kafka topics
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = {"enabled", "auto-create-topics"}, havingValue = "true", matchIfMissing = true)
    public NewTopic[] cacheTopics(AdharCacheProperties properties) {
        String topicPrefix = properties.getKafka().getTopicPrefix();
        int partitions = properties.getKafka().getPartitions();
        short replicationFactor = properties.getKafka().getReplicationFactor();

        return new NewTopic[] {
            TopicBuilder.name(topicPrefix + "-put")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build(),
            TopicBuilder.name(topicPrefix + "-evict")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build(),
            TopicBuilder.name(topicPrefix + "-clear")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build()
        };
    }

    /**
     * Creates a Kafka producer factory for sending cache messages.
     *
     * @param properties the cache properties
     * @return the Kafka producer factory
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProducerFactory<String, CacheMessage> kafkaCacheProducerFactory(AdharCacheProperties properties) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Add any additional properties
        configProps.putAll(properties.getKafka().getProperties());

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates a Kafka template for sending cache messages.
     *
     * @param producerFactory the Kafka producer factory
     * @return the Kafka template
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaTemplate<String, CacheMessage> kafkaCacheTemplate(ProducerFactory<String, CacheMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Creates a Kafka consumer factory for receiving cache messages.
     *
     * @param properties the cache properties
     * @return the Kafka consumer factory
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConsumerFactory<String, CacheMessage> kafkaCacheConsumerFactory(AdharCacheProperties properties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getKafka().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.adhar.adharkit.cache.model");

        // Add any additional properties
        props.putAll(properties.getKafka().getProperties());

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates a Kafka listener container factory for receiving cache messages.
     *
     * @param consumerFactory the Kafka consumer factory
     * @return the Kafka listener container factory
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, CacheMessage>> kafkaCacheListenerContainerFactory(
            ConsumerFactory<String, CacheMessage> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CacheMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    /**
     * Creates an ObjectMapper for serialization/deserialization.
     *
     * @return the ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Creates a cache manager that uses Kafka for distributed cache synchronization.
     * This bean is only created if Kafka is enabled and Redis is disabled.
     *
     * @param kafkaTemplate the Kafka template
     * @param properties    the cache properties
     * @param objectMapper  the ObjectMapper
     * @return the cache manager
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheManager cacheManager(
            KafkaTemplate<String, CacheMessage> kafkaTemplate,
            AdharCacheProperties properties,
            ObjectMapper objectMapper) {
        return new KafkaCacheManager(kafkaTemplate, properties, objectMapper);
    }

    /**
     * Creates a Kafka cache listener for receiving cache messages.
     *
     * @param cacheManager the cache manager
     * @param properties   the cache properties
     * @param objectMapper the ObjectMapper
     * @return the Kafka cache listener
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaCacheListener kafkaCacheListener(
            CacheManager cacheManager,
            AdharCacheProperties properties,
            ObjectMapper objectMapper) {
        return new KafkaCacheListener(cacheManager, properties, objectMapper);
    }
}
