package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.adharkit.cache.service.AdharCacheKafkaClients;
import com.adhar.adharkit.cache.service.CacheMessageSerde;
import com.adhar.adharkit.cache.service.KafkaCacheListener;
import com.adhar.adharkit.cache.service.KafkaCacheManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

/**
 * Auto-configuration for Kafka-synchronized caching: a local cache on every
 * instance, with each PUT, EVICT and CLEAR broadcast to the others over Kafka.
 * <p>
 * <b>Opt-in</b> — set {@code adhar.cache.kafka.enabled=true}. Kafka being on the
 * classpath is not a request for cache synchronization; an application using Kafka
 * for its own events must keep its Kafka beans.
 * <p>
 * Applies only when no other {@link CacheManager} is configured. In particular a
 * Redis/Valkey cache ({@link RedisConfig}) is already shared by every instance and
 * needs no synchronization, so it takes precedence.
 * <p>
 * The Kafka clients are private to the module ({@link AdharCacheKafkaClients}); the
 * only Kafka bean registered is a listener container factory under its own name,
 * which Spring Boot's Kafka auto-configuration does not back off for.
 *
 * <pre>{@code
 * adhar:
 *   cache:
 *     kafka:
 *       enabled: true
 *       bootstrap-servers: kafka:9092   # default: spring.kafka.bootstrap-servers
 *       topic-prefix: adhar-cache
 * }</pre>
 */
@AutoConfiguration(
        after = RedisConfig.class,
        afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@EnableKafka
@EnableConfigurationProperties(AdharCacheProperties.class)
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnProperty(prefix = "adhar.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "adhar.cache.kafka", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(CacheManager.class)
public class AdharCacheAutoConfiguration {

    /**
     * The module's private Kafka clients.
     *
     * @param properties  the cache properties
     * @param environment the environment
     * @return the clients
     */
    @Bean
    public AdharCacheKafkaClients adharCacheKafkaClients(AdharCacheProperties properties, Environment environment) {
        return new AdharCacheKafkaClients(properties, environment);
    }

    /**
     * Listener container factory for the synchronization listeners — referenced by
     * name from {@link KafkaCacheListener}.
     *
     * @param clients the module's Kafka clients
     * @return the container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CacheMessage> kafkaCacheListenerContainerFactory(
            AdharCacheKafkaClients clients) {
        ConcurrentKafkaListenerContainerFactory<String, CacheMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(clients.getConsumerFactory());
        return factory;
    }

    /**
     * The synchronized cache manager.
     *
     * @param clients    the module's Kafka clients
     * @param properties the cache properties
     * @return the cache manager
     */
    @Bean
    public KafkaCacheManager cacheManager(AdharCacheKafkaClients clients, AdharCacheProperties properties) {
        return new KafkaCacheManager(clients.getTemplate(), properties, CacheMessageSerde.MAPPER);
    }

    /**
     * Applies operations received from other instances.
     *
     * @param cacheManager the synchronized cache manager
     * @param properties   the cache properties
     * @return the listener
     */
    @Bean
    public KafkaCacheListener kafkaCacheListener(KafkaCacheManager cacheManager, AdharCacheProperties properties) {
        return new KafkaCacheListener(cacheManager, properties, CacheMessageSerde.MAPPER);
    }
}
