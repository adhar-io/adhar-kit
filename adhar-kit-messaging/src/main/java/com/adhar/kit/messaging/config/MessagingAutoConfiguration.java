package com.adhar.kit.messaging.config;

import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.kafka.KafkaMessageListener;
import com.adhar.kit.messaging.kafka.KafkaMessagePublisher;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.outbox.OutboxPayloadCodec;
import com.adhar.kit.messaging.outbox.OutboxStore;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.rabbitmq.RabbitMQMessageListener;
import com.adhar.kit.messaging.rabbitmq.RabbitMQMessagePublisher;
import com.adhar.kit.messaging.requestreply.KafkaRequestReplyClient;
import com.adhar.kit.messaging.requestreply.RabbitRequestReplyClient;
import com.adhar.kit.messaging.requestreply.RequestReplyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration wiring the messaging module's broker adapters and cross-cutting
 * concerns (metrics) into a usable {@link MessagingFacade} bean.
 * <p>
 * This is the auto-configuration referenced in the gap analysis: previously
 * {@link KafkaMessagePublisher}, {@link KafkaMessageListener},
 * {@link RabbitMQMessagePublisher} and {@link RabbitMQMessageListener} were fully
 * functional classes that were never registered as Spring beans, and
 * {@link MessagingFacade} was an unwired stub. This class:
 * <ul>
 *   <li>registers {@link AdharMessagingProperties} as a configuration properties bean</li>
 *   <li>registers a Kafka {@link MessagePublisher}/{@link MessageListener} pair when
 *       {@code KafkaTemplate} is on the classpath and Kafka is enabled</li>
 *   <li>registers a RabbitMQ {@link MessagePublisher}/{@link MessageListener} pair when
 *       {@code RabbitTemplate} is on the classpath and RabbitMQ is enabled</li>
 *   <li>registers a {@link MessagingMetrics} bean when {@code micrometer-core} is on the
 *       classpath and a {@link MeterRegistry} bean is available</li>
 *   <li>registers the {@link MessagingFacade} bean itself, wiring in whichever of the
 *       above happen to be present (any/all may be absent, in which case the facade
 *       falls back to its logging-stub behaviour for that capability)</li>
 * </ul>
 * Users may substitute any of these beans (Kafka/RabbitMQ publisher or listener,
 * metrics, or the facade itself) by declaring their own bean of the same type -
 * every {@code @Bean} method here is guarded with {@code @ConditionalOnMissingBean}.
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = "com.adhar.kit.dapr.config.DaprAutoConfiguration")
@EnableConfigurationProperties(AdharMessagingProperties.class)
@ConditionalOnProperty(prefix = "adhar.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessagingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MessagingAutoConfiguration.class);

    /**
     * Registers the {@link MessagingFacade} bean, wiring in whichever
     * {@link MessagePublisher}/{@link MessageListener}/{@link MessagingMetrics} beans are
     * present in the context (any/all may be absent).
     *
     * @param publisherProvider provides the {@link MessagePublisher} bean, if any
     * @param listenerProvider  provides the {@link MessageListener} bean, if any
     * @param properties        the bound messaging properties
     * @param metricsProvider   provides the {@link MessagingMetrics} bean, if any
     * @return the configured {@link MessagingFacade}
     */
    @Bean
    @ConditionalOnMissingBean
    public MessagingFacade messagingFacade(ObjectProvider<MessagePublisher> publisherProvider,
                                            ObjectProvider<MessageListener> listenerProvider,
                                            AdharMessagingProperties properties,
                                            ObjectProvider<MessagingMetrics> metricsProvider,
                                            ObjectProvider<OutboxStore> outboxStoreProvider,
                                            ObjectProvider<OutboxPayloadCodec> outboxCodecProvider,
                                            ObjectProvider<RequestReplyClient> requestReplyClientProvider) {
        return new MessagingFacade(publisherProvider.getIfAvailable(), listenerProvider.getIfAvailable(),
                properties, metricsProvider.getIfAvailable(),
                outboxStoreProvider.getIfAvailable(), outboxCodecProvider.getIfAvailable(),
                requestReplyClientProvider.getIfAvailable());
    }

    /**
     * Registers the Kafka-backed {@link MessagePublisher}/{@link MessageListener} pair.
     * Active only when {@code spring-kafka} is on the classpath and
     * {@code adhar.messaging.kafka.enabled} is not explicitly {@code false}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "adhar.messaging.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class KafkaMessagingConfiguration {

        @Bean
        @ConditionalOnMissingBean(MessagePublisher.class)
        @ConditionalOnBean(KafkaTemplate.class)
        public MessagePublisher kafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                                       AdharMessagingProperties properties) {
            log.debug("Registering Kafka-backed MessagePublisher");
            return new KafkaMessagePublisher(kafkaTemplate, properties);
        }

        @Bean
        @ConditionalOnMissingBean(MessageListener.class)
        @ConditionalOnBean({ConcurrentKafkaListenerContainerFactory.class, KafkaListenerEndpointRegistry.class})
        public MessageListener kafkaMessageListener(
                ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory,
                KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
                AdharMessagingProperties properties) {
            log.debug("Registering Kafka-backed MessageListener");
            return new KafkaMessageListener(kafkaListenerContainerFactory, kafkaListenerEndpointRegistry, properties);
        }

        @Bean
        @ConditionalOnMissingBean(RequestReplyClient.class)
        @ConditionalOnBean(KafkaTemplate.class)
        public RequestReplyClient kafkaRequestReplyClient(KafkaTemplate<String, Object> kafkaTemplate,
                                                          ObjectProvider<MessageListener> listenerProvider,
                                                          AdharMessagingProperties properties,
                                                          ObjectProvider<ObjectMapper> objectMapperProvider) {
            log.debug("Registering Kafka-backed RequestReplyClient");
            return new KafkaRequestReplyClient(kafkaTemplate, listenerProvider.getIfAvailable(),
                    properties, objectMapperProvider.getIfAvailable(ObjectMapper::new));
        }
    }

    /**
     * Registers the RabbitMQ-backed {@link MessagePublisher}/{@link MessageListener} pair.
     * Active only when {@code spring-rabbit} is on the classpath and
     * {@code adhar.messaging.rabbitmq.enabled} is not explicitly {@code false}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnProperty(prefix = "adhar.messaging.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class RabbitMessagingConfiguration {

        @Bean
        @ConditionalOnMissingBean(MessagePublisher.class)
        @ConditionalOnBean(RabbitTemplate.class)
        public MessagePublisher rabbitMessagePublisher(RabbitTemplate rabbitTemplate, AdharMessagingProperties properties) {
            log.debug("Registering RabbitMQ-backed MessagePublisher");
            return new RabbitMQMessagePublisher(rabbitTemplate, properties);
        }

        @Bean
        @ConditionalOnMissingBean(MessageListener.class)
        @ConditionalOnBean(ConnectionFactory.class)
        public MessageListener rabbitMessageListener(ConnectionFactory connectionFactory,
                                                      ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                                                      ObjectProvider<MessageConverter> messageConverterProvider,
                                                      AdharMessagingProperties properties) {
            log.debug("Registering RabbitMQ-backed MessageListener");
            RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable(() -> new RabbitAdmin(connectionFactory));
            MessageConverter messageConverter = messageConverterProvider.getIfAvailable(SimpleMessageConverter::new);
            return new RabbitMQMessageListener(connectionFactory, rabbitAdmin, messageConverter, properties);
        }

        @Bean
        @ConditionalOnMissingBean(RequestReplyClient.class)
        @ConditionalOnBean(RabbitTemplate.class)
        public RequestReplyClient rabbitRequestReplyClient(RabbitTemplate rabbitTemplate,
                                                           AdharMessagingProperties properties,
                                                           ObjectProvider<ObjectMapper> objectMapperProvider) {
            log.debug("Registering RabbitMQ-backed RequestReplyClient");
            return new RabbitRequestReplyClient(rabbitTemplate, properties,
                    objectMapperProvider.getIfAvailable(ObjectMapper::new));
        }
    }

    /**
     * Registers the Dapr-backed {@link MessagePublisher}, letting the facade publish
     * through the Dapr pub/sub building block (any broker the sidecar is configured
     * with). Active only when the optional {@code adhar-kit-dapr} module is on the
     * classpath, Dapr is explicitly enabled ({@code adhar.dapr.enabled=true}), and
     * {@code adhar.messaging.dapr.enabled} is not {@code false}. Declared after the
     * Kafka/RabbitMQ configurations and guarded by {@code @ConditionalOnMissingBean},
     * so an explicitly configured broker always wins.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.adhar.kit.dapr.DaprFacade")
    @ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true")
    public static class DaprMessagingConfiguration {

        @Bean
        @ConditionalOnMissingBean(MessagePublisher.class)
        @ConditionalOnBean(com.adhar.kit.dapr.DaprFacade.class)
        @ConditionalOnProperty(prefix = "adhar.messaging.dapr", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        public MessagePublisher daprMessagePublisher(com.adhar.kit.dapr.DaprFacade daprFacade,
                                                     AdharMessagingProperties properties) {
            AdharMessagingProperties.DaprProperties dapr = properties.getDapr();
            log.debug("Registering Dapr-backed MessagePublisher (pubsub component '{}')",
                    dapr.getPubsubName());
            return new com.adhar.kit.messaging.dapr.DaprMessagePublisher(
                    daprFacade, dapr.getPubsubName(), dapr.getDefaultTopic());
        }
    }

    /**
     * Registers the {@link MessagingMetrics} bean. Active only when
     * {@code micrometer-core} is on the classpath and a {@link MeterRegistry} bean is
     * available in the context.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    public static class MessagingMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(MeterRegistry.class)
        public MessagingMetrics messagingMetrics(MeterRegistry meterRegistry) {
            log.debug("Registering MessagingMetrics");
            return new MessagingMetrics(meterRegistry);
        }
    }
}
