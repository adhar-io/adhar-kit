package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * A cache manager implementation that uses Kafka for distributed cache synchronization.
 * <p>
 * This cache manager extends Spring's ConcurrentMapCacheManager and adds Kafka-based
 * synchronization between multiple cache instances. When a cache operation (put, evict, clear)
 * is performed on one instance, a message is sent to Kafka to notify other instances to
 * perform the same operation.
 * <p>
 * The cache manager uses a unique instance ID to prevent processing messages sent by the
 * same instance.
 */
@Slf4j
public class KafkaCacheManager implements CacheManager {

    private final ConcurrentMapCacheManager internalCacheManager;
    private final KafkaTemplate<String, CacheMessage> kafkaTemplate;
    private final AdharCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final String instanceId;
    private final ConcurrentMap<String, KafkaCache> caches = new ConcurrentHashMap<>();

    /**
     * Gets the unique instance ID of this cache manager.
     * This is used to prevent processing messages sent by the same instance.
     *
     * @return the instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Creates a new KafkaCacheManager.
     *
     * @param kafkaTemplate the Kafka template for sending cache messages
     * @param properties    the cache properties
     * @param objectMapper  the object mapper for serialization/deserialization
     */
    public KafkaCacheManager(
            KafkaTemplate<String, CacheMessage> kafkaTemplate,
            AdharCacheProperties properties,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.instanceId = UUID.randomUUID().toString();

        // Create the internal cache manager
        this.internalCacheManager = new ConcurrentMapCacheManager() {
            @Override
            protected Cache createConcurrentMapCache(String name) {
                return new ConcurrentMapCache(
                    name,
                    new ConcurrentHashMap<>(256),
                    properties.isAllowNullValues()
                );
            }
        };

        log.info("Initialized KafkaCacheManager with instance ID: {}", instanceId);
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, cacheName -> {
            Cache internalCache = internalCacheManager.getCache(cacheName);
            return new KafkaCache(internalCache, cacheName);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return internalCacheManager.getCacheNames();
    }

    /**
     * A cache implementation that uses Kafka for distributed cache synchronization.
     */
    private class KafkaCache implements Cache {
        private final Cache internalCache;
        private final String name;

        /**
         * Creates a new KafkaCache.
         *
         * @param internalCache the internal cache
         * @param name          the cache name
         */
        public KafkaCache(Cache internalCache, String name) {
            this.internalCache = internalCache;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return internalCache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return internalCache.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return internalCache.get(key, type);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return internalCache.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            internalCache.put(key, value);

            // Send a message to Kafka to notify other instances
            if (properties.getKafka().isEnabled()) {
                sendPutMessage(key, value, null);
            }
        }

        @Override
        public void evict(Object key) {
            internalCache.evict(key);

            // Send a message to Kafka to notify other instances
            if (properties.getKafka().isEnabled()) {
                sendEvictMessage(key);
            }
        }

        @Override
        public void clear() {
            internalCache.clear();

            // Send a message to Kafka to notify other instances
            if (properties.getKafka().isEnabled()) {
                sendClearMessage();
            }
        }

        /**
         * Puts a value in the cache with a time-to-live.
         *
         * @param key        the key
         * @param value      the value
         * @param timeToLive the time-to-live
         * @param timeUnit   the time unit
         */
        public void put(Object key, Object value, long timeToLive, TimeUnit timeUnit) {
            internalCache.put(key, value);

            // Send a message to Kafka to notify other instances
            if (properties.getKafka().isEnabled()) {
                sendPutMessage(key, value, timeUnit.toMillis(timeToLive));
            }
        }

        /**
         * Sends a PUT message to Kafka.
         *
         * @param key        the key
         * @param value      the value
         * @param timeToLive the time-to-live in milliseconds
         */
        private void sendPutMessage(Object key, Object value, Long timeToLive) {
            try {
                String keyStr = key.toString();
                String valueStr = objectMapper.writeValueAsString(value);
                String valueType = value.getClass().getName();

                CacheMessage message = CacheMessage.builder()
                    .operationType(CacheMessage.OperationType.PUT)
                    .cacheName(name)
                    .key(keyStr)
                    .value(valueStr)
                    .valueType(valueType)
                    .timeToLive(timeToLive)
                    .timestamp(java.time.Instant.now())
                    .sourceInstanceId(instanceId)
                    .build();

                String topic = properties.getKafka().getTopicPrefix() + "-put";
                kafkaTemplate.send(topic, keyStr, message);

                KafkaCacheManager.log.debug("Sent PUT message to Kafka topic {}: {}", topic, message);
            } catch (JsonProcessingException e) {
                KafkaCacheManager.log.error("Failed to serialize cache value", e);
            } catch (Exception e) {
                KafkaCacheManager.log.error("Failed to send PUT message to Kafka", e);
            }
        }

        /**
         * Sends an EVICT message to Kafka.
         *
         * @param key the key
         */
        private void sendEvictMessage(Object key) {
            try {
                String keyStr = key.toString();

                CacheMessage message = CacheMessage.builder()
                    .operationType(CacheMessage.OperationType.EVICT)
                    .cacheName(name)
                    .key(keyStr)
                    .timestamp(java.time.Instant.now())
                    .sourceInstanceId(instanceId)
                    .build();

                String topic = properties.getKafka().getTopicPrefix() + "-evict";
                kafkaTemplate.send(topic, keyStr, message);

                KafkaCacheManager.log.debug("Sent EVICT message to Kafka topic {}: {}", topic, message);
            } catch (Exception e) {
                KafkaCacheManager.log.error("Failed to send EVICT message to Kafka", e);
            }
        }

        /**
         * Sends a CLEAR message to Kafka.
         */
        private void sendClearMessage() {
            try {
                CacheMessage message = CacheMessage.builder()
                    .operationType(CacheMessage.OperationType.CLEAR)
                    .cacheName(name)
                    .timestamp(java.time.Instant.now())
                    .sourceInstanceId(instanceId)
                    .build();

                String topic = properties.getKafka().getTopicPrefix() + "-clear";
                kafkaTemplate.send(topic, name, message);

                KafkaCacheManager.log.debug("Sent CLEAR message to Kafka topic {}: {}", topic, message);
            } catch (Exception e) {
                KafkaCacheManager.log.error("Failed to send CLEAR message to Kafka", e);
            }
        }
    }
}
