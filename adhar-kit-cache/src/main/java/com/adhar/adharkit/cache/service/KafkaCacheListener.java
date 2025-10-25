package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;

import java.io.IOException;

/**
 * Kafka listener for cache synchronization messages.
 * <p>
 * This class listens for cache operation messages from Kafka and applies them to the local cache.
 * It handles PUT, EVICT, and CLEAR operations.
 * <p>
 * The listener uses the source instance ID to prevent processing messages sent by the same instance.
 */
@Slf4j
public class KafkaCacheListener {

    private final CacheManager cacheManager;
    private final AdharCacheProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new KafkaCacheListener.
     *
     * @param cacheManager the cache manager
     * @param properties   the cache properties
     * @param objectMapper the object mapper for serialization/deserialization
     */
    public KafkaCacheListener(
            CacheManager cacheManager,
            AdharCacheProperties properties,
            ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.properties = properties;
        this.objectMapper = objectMapper;

        log.info("Initialized KafkaCacheListener");
    }

    /**
     * Listens for PUT messages from Kafka.
     *
     * @param message the cache message
     */
    @KafkaListener(
            topics = "#{T(com.adhar.adharkit.cache.properties.AdharCacheProperties).getKafka().getTopicPrefix() + '-put'}",
            groupId = "#{T(com.adhar.adharkit.cache.properties.AdharCacheProperties).getKafka().getGroupId()}",
            containerFactory = "kafkaCacheListenerContainerFactory"
    )
    public void listenForPutMessages(CacheMessage message) {
        if (message == null) {
            log.warn("Received null PUT message from Kafka");
            return;
        }

        // Skip messages sent by this instance
        if (isMessageFromThisInstance(message)) {
            log.debug("Skipping PUT message from this instance: {}", message);
            return;
        }

        log.debug("Received PUT message from Kafka: {}", message);

        try {
            // Get the cache
            Cache cache = cacheManager.getCache(message.getCacheName());
            if (cache == null) {
                log.warn("Cache not found: {}", message.getCacheName());
                return;
            }

            // Deserialize the value
            Object value = deserializeValue(message.getValue(), message.getValueType());

            // Put the value in the cache
            cache.put(message.getKey(), value);

            log.debug("Updated cache {} with key {}", message.getCacheName(), message.getKey());
        } catch (Exception e) {
            log.error("Failed to process PUT message from Kafka", e);
        }
    }

    /**
     * Listens for EVICT messages from Kafka.
     *
     * @param message the cache message
     */
    @KafkaListener(
            topics = "#{T(com.adhar.adharkit.cache.properties.AdharCacheProperties).getKafka().getTopicPrefix() + '-evict'}",
            groupId = "#{T(com.adhar.adharkit.cache.properties.AdharCacheProperties).getKafka().getGroupId()}",
            containerFactory = "kafkaCacheListenerContainerFactory"
    )
    public void listenForEvictMessages(CacheMessage message) {
        if (message == null) {
            log.warn("Received null EVICT message from Kafka");
            return;
        }

        // Skip messages sent by this instance
        if (isMessageFromThisInstance(message)) {
            log.debug("Skipping EVICT message from this instance: {}", message);
            return;
        }

        log.debug("Received EVICT message from Kafka: {}", message);

        try {
            // Get the cache
            Cache cache = cacheManager.getCache(message.getCacheName());
            if (cache == null) {
                log.warn("Cache not found: {}", message.getCacheName());
                return;
            }

            // Evict the key from the cache
            cache.evict(message.getKey());

            log.debug("Evicted key {} from cache {}", message.getKey(), message.getCacheName());
        } catch (Exception e) {
            log.error("Failed to process EVICT message from Kafka", e);
        }
    }

    /**
     * Listens for CLEAR messages from Kafka.
     *
     * @param message the cache message
     */
    @KafkaListener(
            topics = "#{T(com.adhar.adharkit.cache.properties.AdharCacheProperties).getKafka().getTopicPrefix() + '-clear'}",
            groupId = "#{T(com.adhar.adharkit.cache.properties.AdharCacheProperties).getKafka().getGroupId()}",
            containerFactory = "kafkaCacheListenerContainerFactory"
    )
    public void listenForClearMessages(CacheMessage message) {
        if (message == null) {
            log.warn("Received null CLEAR message from Kafka");
            return;
        }

        // Skip messages sent by this instance
        if (isMessageFromThisInstance(message)) {
            log.debug("Skipping CLEAR message from this instance: {}", message);
            return;
        }

        log.debug("Received CLEAR message from Kafka: {}", message);

        try {
            // Get the cache
            Cache cache = cacheManager.getCache(message.getCacheName());
            if (cache == null) {
                log.warn("Cache not found: {}", message.getCacheName());
                return;
            }

            // Clear the cache
            cache.clear();

            log.debug("Cleared cache {}", message.getCacheName());
        } catch (Exception e) {
            log.error("Failed to process CLEAR message from Kafka", e);
        }
    }

    /**
     * Checks if the message was sent by this instance.
     *
     * @param message the cache message
     * @return true if the message was sent by this instance, false otherwise
     */
    private boolean isMessageFromThisInstance(CacheMessage message) {
        // If the source instance ID is null, assume it's from another instance
        if (message.getSourceInstanceId() == null) {
            return false;
        }

        // If the cache manager is a KafkaCacheManager, check if the instance ID matches
        if (cacheManager instanceof KafkaCacheManager) {
            String instanceId = ((KafkaCacheManager) cacheManager).getInstanceId();
            return message.getSourceInstanceId().equals(instanceId);
        }

        // For other cache manager implementations, assume it's from another instance
        // This allows the listener to work with any cache manager implementation
        log.debug("Cache manager is not a KafkaCacheManager, assuming message is from another instance");
        return false;
    }

    /**
     * Deserializes a value from a string.
     *
     * @param valueStr  the serialized value
     * @param valueType the class name of the value
     * @return the deserialized value
     * @throws IOException if deserialization fails
     * @throws ClassNotFoundException if the class is not found
     */
    private Object deserializeValue(String valueStr, String valueType) throws IOException, ClassNotFoundException {
        if (valueStr == null || valueType == null) {
            return null;
        }

        Class<?> type = Class.forName(valueType);
        return objectMapper.readValue(valueStr, type);
    }
}
