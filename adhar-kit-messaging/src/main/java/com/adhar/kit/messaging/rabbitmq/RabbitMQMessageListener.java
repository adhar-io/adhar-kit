package com.adhar.kit.messaging.rabbitmq;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of {@link MessageListener} for RabbitMQ.
 * <p>
 * This class uses Spring AMQP's {@link SimpleMessageListenerContainer} to consume messages from RabbitMQ queues.
 * It supports both simple message consumption and more advanced scenarios like
 * manual acknowledgment and error handling.
 */
public class RabbitMQMessageListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQMessageListener.class);

    private final ConnectionFactory connectionFactory;
    private final RabbitAdmin rabbitAdmin;
    private final MessageConverter messageConverter;
    private final AdharMessagingProperties properties;
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();

    /**
     * Creates a new RabbitMQMessageListener.
     *
     * @param connectionFactory the RabbitMQ connection factory
     * @param rabbitAdmin       the RabbitMQ admin
     * @param messageConverter  the message converter
     * @param properties        the messaging properties
     */
    public RabbitMQMessageListener(
            ConnectionFactory connectionFactory,
            RabbitAdmin rabbitAdmin,
            MessageConverter messageConverter,
            AdharMessagingProperties properties) {
        this.connectionFactory = connectionFactory;
        this.rabbitAdmin = rabbitAdmin;
        this.messageConverter = messageConverter;
        this.properties = properties;
    }

    @Override
    public <T> String subscribe(String source, Class<T> type, Consumer<T> consumer) {
        return subscribe(source, properties.getRabbitmq().getDefaultQueue(), type, consumer);
    }

    @Override
    public <T> String subscribe(String source, String group, Class<T> type, Consumer<T> consumer) {
        String consumerId = generateConsumerId(source, group);

        // Create a handler that delegates to the consumer
        MessageHandler<T> handler = (payload, headers, context) -> {
            try {
                consumer.accept(payload);
                return true;
            } catch (Exception e) {
                log.error("Error processing message from RabbitMQ queue {}", source, e);
                return false;
            }
        };

        // Register the handler
        registerHandler(consumerId, source, group, type, handler);

        return consumerId;
    }

    @Override
    public <T> String subscribeWithHeaders(String source, Class<T> type, BiConsumer<T, Map<String, Object>> consumer) {
        return subscribeWithHeaders(source, properties.getRabbitmq().getDefaultQueue(), type, consumer);
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
                log.error("Error processing message from RabbitMQ queue {}", source, e);
                return false;
            }
        };

        // Register the handler
        registerHandler(consumerId, source, group, type, handler);

        return consumerId;
    }

    @Override
    public <T> String subscribeWithAck(String source, Class<T> type, Function<T, Boolean> consumer) {
        return subscribeWithAck(source, properties.getRabbitmq().getDefaultQueue(), type, consumer);
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
                log.error("Error processing message from RabbitMQ queue {}", source, e);
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
        return subscribeWithHeadersAndAck(source, properties.getRabbitmq().getDefaultQueue(), type, consumer);
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
                log.error("Error processing message from RabbitMQ queue {}", source, e);
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
            SimpleMessageListenerContainer container = containers.remove(consumerId);
            if (container != null) {
                container.stop();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error unsubscribing from RabbitMQ queue", e);
            return false;
        }
    }

    @Override
    public boolean pause(String consumerId) {
        try {
            SimpleMessageListenerContainer container = containers.get(consumerId);
            if (container != null) {
                container.stop();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error pausing RabbitMQ consumer", e);
            return false;
        }
    }

    @Override
    public boolean resume(String consumerId) {
        try {
            SimpleMessageListenerContainer container = containers.get(consumerId);
            if (container != null) {
                container.start();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error resuming RabbitMQ consumer", e);
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
        return "rabbitmq-" + source + "-" + group + "-" + UUID.randomUUID();
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
        // Declare the queue, exchange, and binding if auto-declare is enabled
        if (properties.getRabbitmq().getListener().isAutoDeclare()) {
            declareQueueAndExchange(source, group);
        }

        // Create a container for the listener
        SimpleMessageListenerContainer container = createListenerContainer();
        container.setQueueNames(group);
        container.setPrefetchCount(properties.getRabbitmq().getListener().getPrefetchCount());
        container.setConcurrentConsumers(properties.getRabbitmq().getListener().getConcurrency());
        container.setMaxConcurrentConsumers(properties.getRabbitmq().getListener().getMaxConcurrency());
        container.setAcknowledgeMode(properties.getKafka().getConsumer().isEnableAutoCommit() 
                ? AcknowledgeMode.AUTO 
                : AcknowledgeMode.MANUAL);

        // Set the message listener
        container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
            processMessage(message, channel, handler, consumerId, source, group, type);
        });

        // Start the container
        container.start();

        // Store the container
        containers.put(consumerId, container);

        log.debug("Registered RabbitMQ message handler for queue {} with consumer ID {}", group, consumerId);
    }

    /**
     * Creates a new {@link SimpleMessageListenerContainer} bound to the configured
     * connection factory.
     * <p>
     * Exposed as a factory method to provide a seam for testing and to allow
     * subclasses to customize container creation.
     *
     * @return a new listener container
     */
    public SimpleMessageListenerContainer createListenerContainer() {
        return new SimpleMessageListenerContainer(connectionFactory);
    }

    /**
     * Declares the queue, exchange, and binding if they don't exist.
     *
     * @param source the source (exchange)
     * @param group  the group (queue)
     */
    private void declareQueueAndExchange(String source, String group) {
        String exchange = StringUtils.hasText(source) ? source : properties.getRabbitmq().getDefaultExchange();
        String queue = StringUtils.hasText(group) ? group : properties.getRabbitmq().getDefaultQueue();
        String routingKey = properties.getRabbitmq().getDefaultRoutingKey();

        // Declare the exchange
        Exchange exchangeObj;
        if (properties.getRabbitmq().getPublisher().isExchangeDurable()) {
            exchangeObj = ExchangeBuilder.topicExchange(exchange).durable(true).build();
        } else {
            exchangeObj = ExchangeBuilder.topicExchange(exchange).durable(false).build();
        }
        rabbitAdmin.declareExchange(exchangeObj);

        // Declare the queue
        Queue queueObj;
        if (properties.getRabbitmq().getListener().isQueueDurable()) {
            queueObj = new Queue(queue, 
                    true, // durable
                    properties.getRabbitmq().getListener().isQueueExclusive(), 
                    properties.getRabbitmq().getListener().isQueueAutoDelete());
        } else {
            queueObj = new Queue(queue, 
                    false, // non-durable
                    properties.getRabbitmq().getListener().isQueueExclusive(), 
                    properties.getRabbitmq().getListener().isQueueAutoDelete());
        }
        rabbitAdmin.declareQueue(queueObj);

        // Declare the binding
        Binding binding = BindingBuilder
                .bind(queueObj)
                .to(exchangeObj)
                .with(routingKey)
                .noargs();
        rabbitAdmin.declareBinding(binding);

        log.debug("Declared RabbitMQ exchange {}, queue {}, and binding with routing key {}", 
                exchange, queue, routingKey);
    }

    /**
     * Processes a RabbitMQ message using the given handler.
     *
     * @param message    the RabbitMQ message
     * @param channel    the RabbitMQ channel
     * @param handler    the message handler
     * @param consumerId the consumer ID
     * @param source     the source
     * @param group      the consumer group
     * @param type       the type of the payload
     * @param <T>        the type of the payload
     * @throws Exception if an error occurs
     */
    private <T> void processMessage(Message message, com.rabbitmq.client.Channel channel, 
                                   MessageHandler<T> handler, String consumerId, 
                                   String source, String group, Class<T> type) throws Exception {
        try {
            // Extract headers
            Map<String, Object> headers = new HashMap<>(message.getMessageProperties().getHeaders());

            // Convert the message to the target type
            Object rawPayload = messageConverter.fromMessage(message);
            @SuppressWarnings("unchecked")
            T payload = (T) rawPayload;

            // Create context
            RabbitMQMessageContext context = new RabbitMQMessageContext(
                    source, message.getMessageProperties().getReceivedExchange(), 
                    message.getMessageProperties().getReceivedRoutingKey(), 
                    message.getMessageProperties().getTimestamp() != null 
                            ? message.getMessageProperties().getTimestamp().getTime() 
                            : System.currentTimeMillis(),
                    consumerId, group, 
                    message.getMessageProperties().getMessageId() != null 
                            ? message.getMessageProperties().getMessageId() 
                            : UUID.randomUUID().toString(),
                    message, channel);

            // Process the message
            handler.handle(payload, headers, context);
        } catch (Exception e) {
            log.error("Error processing RabbitMQ message", e);
            // Reject the message if manual acknowledgment is enabled
            if (properties.getKafka().getConsumer().isEnableAutoCommit()) {
                throw e; // Let the container handle the exception
            } else {
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            }
        }
    }

    /**
     * Implementation of {@link MessageHandler.MessageContext} for RabbitMQ.
     */
    private static class RabbitMQMessageContext implements MessageHandler.MessageContext {
        private final String source;
        private final String destination;
        private final String routingKey;
        private final long timestamp;
        private final String consumerId;
        private final String consumerGroup;
        private final String messageId;
        private final Map<String, Object> attributes = new HashMap<>();
        private final Message message;
        private final com.rabbitmq.client.Channel channel;

        public RabbitMQMessageContext(String source, String destination, String routingKey, 
                                     long timestamp, String consumerId, String consumerGroup, 
                                     String messageId, Message message, com.rabbitmq.client.Channel channel) {
            this.source = source;
            this.destination = destination;
            this.routingKey = routingKey;
            this.timestamp = timestamp;
            this.consumerId = consumerId;
            this.consumerGroup = consumerGroup;
            this.messageId = messageId;
            this.message = message;
            this.channel = channel;
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
            try {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean reject(boolean requeue) {
            try {
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), requeue);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
