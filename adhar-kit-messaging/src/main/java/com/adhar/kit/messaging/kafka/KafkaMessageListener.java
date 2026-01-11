package com.adhar.kit.messaging.kafka;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of {@link MessageListener} for Kafka.
 * <p>
 * This class uses Spring Kafka's {@link KafkaListener} to consume messages from Kafka topics.
 * It supports both simple message consumption and more advanced scenarios like
 * manual acknowledgment and error handling.
 */
public class KafkaMessageListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageListener.class);

    private final KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, Object>> kafkaListenerContainerFactory;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final AdharMessagingProperties properties;
    private final Map<String, MessageListenerContainer> containers = new ConcurrentHashMap<>();

    /**
     * Creates a new KafkaMessageListener.
     *
     * @param kafkaListenerContainerFactory the factory for creating Kafka listener containers
     * @param kafkaListenerEndpointRegistry the registry for Kafka listener endpoints
     * @param properties                    the messaging properties
     */
    public KafkaMessageListener(
            ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory,
            KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
            AdharMessagingProperties properties) {
        this.kafkaListenerContainerFactory = kafkaListenerContainerFactory;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
        this.properties = properties;
    }

    @Override
    public <T> String subscribe(String source, Class<T> type, Consumer<T> consumer) {
        return subscribeWithStringGroup(source, properties.getKafka().getConsumer().getGroupId(), type, consumer);
    }

    @Override
    public <T> String subscribe(String source, String group, Class<T> type, Consumer<T> consumer) {
        return subscribeWithStringGroup(source, group, type, consumer);
    }

    /**
     * Helper method to disambiguate method calls with String group.
     */
    private <T> String subscribeWithStringGroup(String source, String group, Class<T> type, Consumer<T> consumer) {
        String consumerId = generateConsumerId(source, group);
        
        // Create a handler that delegates to the consumer
        MessageHandler<T> handler = (payload, headers, context) -> {
            try {
                consumer.accept(payload);
                return true;
            } catch (Exception e) {
                log.error("Error processing message from Kafka topic {}", source, e);
                return false;
            }
        };
        
        // Register the handler
        registerHandler(consumerId, source, group, type, handler);
        
        return consumerId;
    }

    @Override
    public <T> String subscribeWithHeaders(String source, Class<T> type, BiConsumer<T, Map<String, Object>> consumer) {
        return subscribeWithHeaders(source, properties.getKafka().getConsumer().getGroupId(), type, consumer);
    }

    @Override
    public <T> String subscribeWithHeaders(String source, String group, Class<T> type, BiConsumer<T, Map<String, Object>> consumer) {
        String consumerId = generateConsumerId(source, group);
        
        // Create a handler that delegates to the consumer
        MessageHandler<T> handler = (payload, headers, context) -> {
            try {
                consumer.accept(payload, headers);
                return true;
            } catch (Exception e) {
                log.error("Error processing message from Kafka topic {}", source, e);
                return false;
            }
        };
        
        // Register the handler
        registerHandler(consumerId, source, group, type, handler);
        
        return consumerId;
    }

    @Override
    public <T> String subscribeWithAck(String source, Class<T> type, Function<T, Boolean> consumer) {
        return subscribeWithAck(source, properties.getKafka().getConsumer().getGroupId(), type, consumer);
    }

    @Override
    public <T> String subscribeWithAck(String source, String group, Class<T> type, Function<T, Boolean> consumer) {
        String consumerId = generateConsumerId(source, group);
        
        // Create a handler that delegates to the consumer
        MessageHandler<T> handler = (payload, headers, context) -> {
            try {
                boolean result = consumer.apply(payload);
                if (result) {
                    context.acknowledge();
                } else {
                    context.reject(true); // Requeue the message
                }
                return result;
            } catch (Exception e) {
                log.error("Error processing message from Kafka topic {}", source, e);
                context.reject(true); // Requeue the message
                return false;
            }
        };
        
        // Register the handler
        registerHandler(consumerId, source, group, type, handler);
        
        return consumerId;
    }

    @Override
    public <T> String subscribeWithHeadersAndAck(String source, Class<T> type, BiFunction<T, Map<String, Object>, Boolean> consumer) {
        return subscribeWithHeadersAndAck(source, properties.getKafka().getConsumer().getGroupId(), type, consumer);
    }

    @Override
    public <T> String subscribeWithHeadersAndAck(String source, String group, Class<T> type, BiFunction<T, Map<String, Object>, Boolean> consumer) {
        String consumerId = generateConsumerId(source, group);
        
        // Create a handler that delegates to the consumer
        MessageHandler<T> handler = (payload, headers, context) -> {
            try {
                boolean result = consumer.apply(payload, headers);
                if (result) {
                    context.acknowledge();
                } else {
                    context.reject(true); // Requeue the message
                }
                return result;
            } catch (Exception e) {
                log.error("Error processing message from Kafka topic {}", source, e);
                context.reject(true); // Requeue the message
                return false;
            }
        };
        
        // Register the handler
        registerHandler(consumerId, source, group, type, handler);
        
        return consumerId;
    }

    @Override
    public boolean unsubscribe(String consumerId) {
        try {
            MessageListenerContainer container = containers.remove(consumerId);
            if (container != null) {
                container.stop();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error unsubscribing from Kafka topic", e);
            return false;
        }
    }

    @Override
    public boolean pause(String consumerId) {
        try {
            MessageListenerContainer container = containers.get(consumerId);
            if (container != null) {
                container.pause();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error pausing Kafka consumer", e);
            return false;
        }
    }

    @Override
    public boolean resume(String consumerId) {
        try {
            MessageListenerContainer container = containers.get(consumerId);
            if (container != null) {
                container.resume();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error resuming Kafka consumer", e);
            return false;
        }
    }

    /**
     * Generates a unique consumer ID for the given source and group.
     *
     * @param source the source to consume from
     * @param group  the consumer group
     * @return a unique consumer ID
     */
    private String generateConsumerId(String source, String group) {
        return "kafka-" + source + "-" + group + "-" + UUID.randomUUID();
    }

    /**
     * Registers a message handler for the given consumer ID, source, group, and type.
     *
     * @param consumerId the consumer ID
     * @param source     the source to consume from
     * @param group      the consumer group
     * @param type       the type of the payload
     * @param handler    the message handler
     * @param <T>        the type of the payload
     */
    private <T> void registerHandler(String consumerId, String source, String group, Class<T> type, MessageHandler<T> handler) {
        // Create a container for the listener
        ConcurrentMessageListenerContainer<String, Object> container = 
                kafkaListenerContainerFactory.createContainer(source);
        
        // Configure the container
        container.getContainerProperties().setGroupId(group);
        container.getContainerProperties().setAckMode(properties.getKafka().getConsumer().isEnableAutoCommit() 
                ? org.springframework.kafka.listener.ContainerProperties.AckMode.BATCH 
                : org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        
        // Set the message listener
        container.getContainerProperties().setMessageListener(
                (org.springframework.kafka.listener.MessageListener<String, T>) record -> {
                    processRecord(record, handler, consumerId, source, group);
                });
        
        // Start the container
        container.start();
        
        // Store the container
        containers.put(consumerId, container);
        
        log.debug("Registered Kafka message handler for topic {} with consumer ID {}", source, consumerId);
    }

    /**
     * Processes a Kafka record using the given handler.
     *
     * @param record     the Kafka record
     * @param handler    the message handler
     * @param consumerId the consumer ID
     * @param source     the source
     * @param group      the consumer group
     * @param <T>        the type of the payload
     */
    private <T> void processRecord(ConsumerRecord<String, T> record, MessageHandler<T> handler, 
                                  String consumerId, String source, String group) {
        try {
            // Extract headers
            Map<String, Object> headers = new HashMap<>();
            record.headers().forEach(header -> 
                    headers.put(header.key(), new String(header.value())));
            
            // Create context
            KafkaMessageContext context = new KafkaMessageContext(
                    source, record.topic(), record.key(), record.timestamp(), 
                    consumerId, group, UUID.randomUUID().toString());
            
            // Process the message
            handler.handle(record.value(), headers, context);
        } catch (Exception e) {
            log.error("Error processing Kafka record from topic {}", record.topic(), e);
        }
    }

    /**
     * Implementation of {@link MessageHandler.MessageContext} for Kafka.
     */
    private static class KafkaMessageContext implements MessageHandler.MessageContext {
        private final String source;
        private final String destination;
        private final String routingKey;
        private final long timestamp;
        private final String consumerId;
        private final String consumerGroup;
        private final String messageId;
        private final Map<String, Object> attributes = new HashMap<>();
        private Acknowledgment acknowledgment;

        public KafkaMessageContext(String source, String destination, String routingKey, 
                                  long timestamp, String consumerId, String consumerGroup, 
                                  String messageId) {
            this.source = source;
            this.destination = destination;
            this.routingKey = routingKey;
            this.timestamp = timestamp;
            this.consumerId = consumerId;
            this.consumerGroup = consumerGroup;
            this.messageId = messageId;
        }

        public void setAcknowledgment(Acknowledgment acknowledgment) {
            this.acknowledgment = acknowledgment;
        }

        @Override
        public String getSource() {
            return source;
        }

        @Override
        public String getDestination() {
            return destination;
        }

        @Override
        public String getRoutingKey() {
            return routingKey;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String getConsumerId() {
            return consumerId;
        }

        @Override
        public String getConsumerGroup() {
            return consumerGroup;
        }

        @Override
        public String getMessageId() {
            return messageId;
        }

        @Override
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public boolean acknowledge() {
            if (acknowledgment != null) {
                try {
                    acknowledgment.acknowledge();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return true; // Auto-commit is enabled
        }

        @Override
        public boolean reject(boolean requeue) {
            // Kafka doesn't support rejecting individual messages
            // We can only acknowledge or not acknowledge
            return !acknowledge();
        }
    }
}