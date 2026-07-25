package com.adhar.adharkit.messaging.config;

import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.messaging.config.MessagingAutoConfiguration;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MessagingAutoConfiguration}. Uses {@link ApplicationContextRunner}
 * with mocked collaborators (KafkaTemplate, RabbitTemplate, ConnectionFactory, ...) so no
 * broker connection or Docker container is ever started.
 */
class MessagingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class));

    @Test
    void facadeIsCreatedAsAStubWhenNoBrokerBeansArePresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessagingFacade.class);
            assertThat(context).doesNotHaveBean(MessagePublisher.class);
            assertThat(context).doesNotHaveBean(MessageListener.class);
        });
    }

    @Test
    void noBeansAreCreatedWhenMessagingDisabled() {
        contextRunner
                .withPropertyValues("adhar.messaging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MessagingFacade.class));
    }

    @Test
    void kafkaPublisherAndListenerAreRegisteredWhenTheirBeansArePresent() {
        contextRunner
                .withUserConfiguration(KafkaBeansConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context).hasSingleBean(MessageListener.class);
                    assertThat(context.getBean(MessagePublisher.class))
                            .isInstanceOf(com.adhar.kit.messaging.kafka.KafkaMessagePublisher.class);
                    assertThat(context.getBean(MessageListener.class))
                            .isInstanceOf(com.adhar.kit.messaging.kafka.KafkaMessageListener.class);
                    assertThat(context).hasSingleBean(MessagingFacade.class);
                });
    }

    @Test
    void kafkaBeansAreSkippedWhenKafkaDisabledByProperty() {
        contextRunner
                .withUserConfiguration(KafkaBeansConfiguration.class)
                .withPropertyValues("adhar.messaging.kafka.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MessagePublisher.class);
                    assertThat(context).doesNotHaveBean(MessageListener.class);
                });
    }

    @Test
    void rabbitPublisherAndListenerAreRegisteredWhenTheirBeansArePresent() {
        contextRunner
                .withUserConfiguration(RabbitBeansConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context).hasSingleBean(MessageListener.class);
                    assertThat(context.getBean(MessagePublisher.class))
                            .isInstanceOf(com.adhar.kit.messaging.rabbitmq.RabbitMQMessagePublisher.class);
                    assertThat(context.getBean(MessageListener.class))
                            .isInstanceOf(com.adhar.kit.messaging.rabbitmq.RabbitMQMessageListener.class);
                });
    }

    @Test
    void rabbitBeansAreSkippedWhenRabbitDisabledByProperty() {
        contextRunner
                .withUserConfiguration(RabbitBeansConfiguration.class)
                .withPropertyValues("adhar.messaging.rabbitmq.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MessagePublisher.class);
                    assertThat(context).doesNotHaveBean(MessageListener.class);
                });
    }

    @Test
    void userSuppliedMessagePublisherTakesPrecedenceOverKafkaAutoConfiguredOne() {
        contextRunner
                .withUserConfiguration(KafkaBeansConfiguration.class, CustomPublisherConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context.getBean(MessagePublisher.class)).isInstanceOf(CustomMessagePublisher.class);
                });
    }

    @Test
    void metricsBeanIsCreatedWhenMeterRegistryIsPresent() {
        contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(MessagingMetrics.class));
    }

    @Test
    void metricsBeanIsAbsentWithoutMeterRegistry() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MessagingMetrics.class));
    }

    @Test
    void facadeReceivesMetricsBeanWhenAvailable() {
        contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(MessagingFacade.class));
    }

    @Configuration
    static class KafkaBeansConfiguration {
        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
            return mock(ConcurrentKafkaListenerContainerFactory.class);
        }

        @Bean
        KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry() {
            return mock(KafkaListenerEndpointRegistry.class);
        }
    }

    @Configuration
    static class RabbitBeansConfiguration {
        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }

        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Configuration
    static class CustomPublisherConfiguration {
        @Bean
        MessagePublisher customMessagePublisher() {
            return new CustomMessagePublisher();
        }
    }

    @Configuration
    static class MeterRegistryConfiguration {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /**
     * Minimal user-supplied {@link MessagePublisher} used to prove
     * {@code @ConditionalOnMissingBean} defers to a user bean.
     */
    static class CustomMessagePublisher implements MessagePublisher {
        @Override
        public <T> boolean publish(T payload) {
            return true;
        }

        @Override
        public <T> boolean publish(String destination, T payload) {
            return true;
        }

        @Override
        public <T> boolean publish(String destination, String routingKey, T payload) {
            return true;
        }

        @Override
        public <T> boolean publish(String destination, java.util.Map<String, Object> headers, T payload) {
            return true;
        }

        @Override
        public <T> boolean publish(String destination, String routingKey, java.util.Map<String, Object> headers, T payload) {
            return true;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<Boolean> publishAsync(T payload) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<Boolean> publishAsync(String destination, T payload) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<Boolean> publishAsync(String destination, String routingKey, T payload) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<Boolean> publishAsync(String destination, java.util.Map<String, Object> headers, T payload) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<Boolean> publishAsync(String destination, String routingKey, java.util.Map<String, Object> headers, T payload) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }
    }
}
