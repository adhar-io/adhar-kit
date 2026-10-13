package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The Kafka clients behind cache synchronization, owned privately by the module.
 * <p>
 * Deliberately NOT exposed as {@code ProducerFactory}/{@code ConsumerFactory}/
 * {@code KafkaTemplate}/{@code KafkaAdmin} beans: Spring Boot's Kafka
 * auto-configuration backs off when a bean of any of those types exists, so
 * registering them took over the application's own Kafka — its
 * {@code KafkaTemplate<String, String>} disappeared and its {@code @KafkaListener}s
 * were handed this module's deserializer and consumer group.
 * <p>
 * Every instance consumes in its own consumer group ({@code <group-id>-<uuid>}).
 * A shared group would deliver each invalidation to only ONE instance, which is the
 * opposite of what cache synchronization needs.
 */
@Slf4j
public class AdharCacheKafkaClients implements DisposableBean {

    private static final long TOPIC_CREATION_TIMEOUT_SECONDS = 15;

    private final AdharCacheProperties.KafkaProperties kafka;
    private final String bootstrapServers;
    private final String groupId;
    private final DefaultKafkaProducerFactory<String, CacheMessage> producerFactory;
    private final KafkaTemplate<String, CacheMessage> template;
    private final DefaultKafkaConsumerFactory<String, CacheMessage> consumerFactory;

    /**
     * Creates the clients, and the cache topics when {@code auto-create-topics} is set.
     *
     * @param properties  the cache properties
     * @param environment used to fall back to {@code spring.kafka.bootstrap-servers}
     */
    public AdharCacheKafkaClients(AdharCacheProperties properties, Environment environment) {
        this.kafka = properties.getKafka();
        this.bootstrapServers = resolveBootstrapServers(kafka, environment);
        this.groupId = kafka.getGroupId() + "-" + UUID.randomUUID();

        Map<String, Object> producer = new HashMap<>(kafka.getProperties());
        producer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.producerFactory = new DefaultKafkaProducerFactory<>(producer, new StringSerializer(), new CacheMessageSerde());
        this.template = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumer = new HashMap<>(kafka.getProperties());
        consumer.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumer.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Only operations from now on matter to a fresh instance; replaying the
        // topic's history would rebuild stale entries. Throwaway per-instance
        // groups also need no committed offsets.
        consumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        this.consumerFactory = new DefaultKafkaConsumerFactory<>(consumer, new StringDeserializer(), new CacheMessageSerde());

        if (kafka.isAutoCreateTopics()) {
            createTopics();
        }
        log.info("Adhar cache synchronization over Kafka at {} (topics '{}-*', group {})",
                bootstrapServers, kafka.getTopicPrefix(), groupId);
    }

    static String resolveBootstrapServers(AdharCacheProperties.KafkaProperties kafka, Environment environment) {
        if (StringUtils.hasText(kafka.getBootstrapServers())) {
            return kafka.getBootstrapServers();
        }
        return environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092");
    }

    /** @return the template that publishes cache operations */
    public KafkaTemplate<String, CacheMessage> getTemplate() {
        return template;
    }

    /** @return the consumer factory the synchronization listeners use */
    public ConsumerFactory<String, CacheMessage> getConsumerFactory() {
        return consumerFactory;
    }

    /** @return this instance's own consumer group */
    public String getGroupId() {
        return groupId;
    }

    /** @return the bootstrap servers in use */
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    private void createTopics() {
        Map<String, Object> admin = new HashMap<>(kafka.getProperties());
        admin.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        List<NewTopic> topics = List.of("-put", "-evict", "-clear").stream()
                .map(suffix -> new NewTopic(kafka.getTopicPrefix() + suffix, kafka.getPartitions(), kafka.getReplicationFactor()))
                .toList();
        // One shared deadline: an unreachable broker should delay startup by
        // seconds, not by a timeout per topic.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TOPIC_CREATION_TIMEOUT_SECONDS);
        Admin client = Admin.create(admin);
        try {
            client.createTopics(topics).values().forEach((name, future) -> {
                try {
                    future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) {
                        log.warn("Could not create cache topic {}: {}", name, e.getCause().getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while creating cache topic {}", name);
                } catch (Exception e) {
                    log.warn("Could not create cache topic {}: {}", name, e.toString());
                }
            });
        } finally {
            // close() without a timeout waits for in-flight requests indefinitely.
            client.close(java.time.Duration.ofSeconds(2));
        }
    }

    @Override
    public void destroy() {
        producerFactory.destroy();
    }
}
