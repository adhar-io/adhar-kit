package com.adhar.adharkit.cache;

import com.adhar.adharkit.cache.config.AdharCacheAutoConfiguration;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Kafka-based caching.
 * <p>
 * This test class uses Testcontainers to start a Kafka container for testing.
 */
@SpringBootTest
@Testcontainers
@EnableCaching
public class KafkaCacheTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"));

    @Autowired
    private CacheManager cacheManager;

    /**
     * Dynamically sets the Kafka connection properties for the test.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("adhar.cache.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("adhar.cache.redis.enabled", () -> "false");
    }

    /**
     * Test configuration for Kafka cache.
     */
    @Configuration
    @ImportAutoConfiguration(AdharCacheAutoConfiguration.class)
    static class TestConfig {
        @Bean
        public AdharCacheProperties adharCacheProperties() {
            return new AdharCacheProperties();
        }

        @Bean
        public ProducerFactory<String, Object> kafkaProducerFactory() {
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(configProps);
        }

        @Bean
        public KafkaTemplate<String, Object> kafkaTemplate() {
            return new KafkaTemplate<>(kafkaProducerFactory());
        }

        @Bean
        public DefaultKafkaConsumerFactory<String, Object> kafkaConsumerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            return new DefaultKafkaConsumerFactory<>(props);
        }
    }

    @Test
    void shouldPutAndGetFromCache() {
        // Given
        String cacheName = "testCache";
        String key = "testKey";
        String value = "testValue-" + UUID.randomUUID();

        // When
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key, value);

        // Then
        assertThat(cache.get(key, String.class)).isEqualTo(value);
    }

    @Test
    void shouldEvictFromCache() {
        // Given
        String cacheName = "testCache";
        String key = "testKey";
        String value = "testValue-" + UUID.randomUUID();

        // When
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key, value);
        cache.evict(key);

        // Then
        assertThat(cache.get(key)).isNull();
    }

    @Test
    void shouldClearCache() {
        // Given
        String cacheName = "testCache";
        String key1 = "testKey1";
        String key2 = "testKey2";
        String value = "testValue-" + UUID.randomUUID();

        // When
        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key1, value);
        cache.put(key2, value);
        cache.clear();

        // Then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(cache.get(key1)).isNull();
            assertThat(cache.get(key2)).isNull();
        });
    }
}