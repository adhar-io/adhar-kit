package com.adhar.adharkit.messaging.properties;

import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link AdharMessagingProperties} class.
 */
@SpringBootTest(classes = AdharMessagingPropertiesTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "adhar.messaging.enabled=true",
        "adhar.messaging.kafka.enabled=true",
        "adhar.messaging.kafka.bootstrap-servers=localhost:9092,localhost:9093",
        "adhar.messaging.kafka.default-topic=test-topic",
        "adhar.messaging.kafka.producer.acks=all",
        "adhar.messaging.kafka.producer.retries=5",
        "adhar.messaging.kafka.producer.batch-size=32768",
        "adhar.messaging.kafka.consumer.group-id=test-group",
        "adhar.messaging.kafka.consumer.auto-offset-reset=earliest",
        "adhar.messaging.kafka.consumer.enable-auto-commit=false",
        "adhar.messaging.kafka.consumer.max-poll-records=1000",
        "adhar.messaging.rabbitmq.enabled=true",
        "adhar.messaging.rabbitmq.host=localhost",
        "adhar.messaging.rabbitmq.port=5672",
        "adhar.messaging.rabbitmq.username=guest",
        "adhar.messaging.rabbitmq.password=guest",
        "adhar.messaging.rabbitmq.virtual-host=/",
        "adhar.messaging.rabbitmq.default-exchange=test-exchange",
        "adhar.messaging.rabbitmq.default-routing-key=test-routing-key",
        "adhar.messaging.rabbitmq.default-queue=test-queue",
        "adhar.messaging.rabbitmq.publisher.confirms=true",
        "adhar.messaging.rabbitmq.publisher.returns=true",
        "adhar.messaging.rabbitmq.publisher.exchange-type=topic",
        "adhar.messaging.rabbitmq.publisher.exchange-durable=true",
        "adhar.messaging.rabbitmq.publisher.exchange-auto-delete=false",
        "adhar.messaging.rabbitmq.listener.auto-declare=true",
        "adhar.messaging.rabbitmq.listener.queue-durable=true",
        "adhar.messaging.rabbitmq.listener.queue-exclusive=false",
        "adhar.messaging.rabbitmq.listener.queue-auto-delete=false",
        "adhar.messaging.rabbitmq.listener.prefetch-count=250",
        "adhar.messaging.rabbitmq.listener.concurrency=2",
        "adhar.messaging.rabbitmq.listener.max-concurrency=5",
        "adhar.messaging.common.default-serialization-format=json",
        "adhar.messaging.common.trace-enabled=true",
        "adhar.messaging.common.validation-enabled=true",
        "adhar.messaging.common.default-timeout-ms=60000",
        "adhar.messaging.common.default-retries=3",
        "adhar.messaging.common.default-backoff-ms=1000",
        "adhar.messaging.common.retry.enabled=false",
        "adhar.messaging.common.retry.max-attempts=5",
        "adhar.messaging.common.retry.initial-delay-ms=100",
        "adhar.messaging.common.retry.backoff-multiplier=1.5",
        "adhar.messaging.common.retry.max-delay-ms=2000",
        "adhar.messaging.common.dlq.enabled=false",
        "adhar.messaging.common.dlq.topic-suffix=.deadletter",
        "adhar.messaging.common.dedup.enabled=true",
        "adhar.messaging.common.dedup.ttl-ms=120000"
})
class AdharMessagingPropertiesTest {

    @Autowired
    private AdharMessagingProperties properties;

    @Test
    void testGlobalProperties() {
        assertTrue(properties.isEnabled());
    }

    @Test
    void testKafkaProperties() {
        assertTrue(properties.getKafka().isEnabled());
        assertEquals("localhost:9092,localhost:9093", properties.getKafka().getBootstrapServers());
        assertEquals("test-topic", properties.getKafka().getDefaultTopic());
    }

    @Test
    void testKafkaProducerProperties() {
        assertEquals("all", properties.getKafka().getProducer().getAcks());
        assertEquals(5, properties.getKafka().getProducer().getRetries());
        assertEquals(32768, properties.getKafka().getProducer().getBatchSize());
    }

    @Test
    void testKafkaConsumerProperties() {
        assertEquals("test-group", properties.getKafka().getConsumer().getGroupId());
        assertEquals("earliest", properties.getKafka().getConsumer().getAutoOffsetReset());
        assertFalse(properties.getKafka().getConsumer().isEnableAutoCommit());
        assertEquals(1000, properties.getKafka().getConsumer().getMaxPollRecords());
    }

    @Test
    void testRabbitMQProperties() {
        assertTrue(properties.getRabbitmq().isEnabled());
        assertEquals("localhost", properties.getRabbitmq().getHost());
        assertEquals(5672, properties.getRabbitmq().getPort());
        assertEquals("guest", properties.getRabbitmq().getUsername());
        assertEquals("guest", properties.getRabbitmq().getPassword());
        assertEquals("/", properties.getRabbitmq().getVirtualHost());
        assertEquals("test-exchange", properties.getRabbitmq().getDefaultExchange());
        assertEquals("test-routing-key", properties.getRabbitmq().getDefaultRoutingKey());
        assertEquals("test-queue", properties.getRabbitmq().getDefaultQueue());
    }

    @Test
    void testRabbitMQPublisherProperties() {
        assertTrue(properties.getRabbitmq().getPublisher().isConfirms());
        assertTrue(properties.getRabbitmq().getPublisher().isReturns());
        assertEquals("topic", properties.getRabbitmq().getPublisher().getExchangeType());
        assertTrue(properties.getRabbitmq().getPublisher().isExchangeDurable());
        assertFalse(properties.getRabbitmq().getPublisher().isExchangeAutoDelete());
    }

    @Test
    void testRabbitMQListenerProperties() {
        assertTrue(properties.getRabbitmq().getListener().isAutoDeclare());
        assertTrue(properties.getRabbitmq().getListener().isQueueDurable());
        assertFalse(properties.getRabbitmq().getListener().isQueueExclusive());
        assertFalse(properties.getRabbitmq().getListener().isQueueAutoDelete());
        assertEquals(250, properties.getRabbitmq().getListener().getPrefetchCount());
        assertEquals(2, properties.getRabbitmq().getListener().getConcurrency());
        assertEquals(5, properties.getRabbitmq().getListener().getMaxConcurrency());
    }

    @Test
    void testCommonProperties() {
        assertEquals("json", properties.getCommon().getDefaultSerializationFormat());
        assertTrue(properties.getCommon().isTraceEnabled());
        assertTrue(properties.getCommon().isValidationEnabled());
        assertEquals(60000, properties.getCommon().getDefaultTimeoutMs());
        assertEquals(3, properties.getCommon().getDefaultRetries());
        assertEquals(1000, properties.getCommon().getDefaultBackoffMs());
    }

    @Test
    void testCommonRetryProperties() {
        assertFalse(properties.getCommon().getRetry().isEnabled());
        assertEquals(5, properties.getCommon().getRetry().getMaxAttempts());
        assertEquals(100, properties.getCommon().getRetry().getInitialDelayMs());
        assertEquals(1.5, properties.getCommon().getRetry().getBackoffMultiplier());
        assertEquals(2000, properties.getCommon().getRetry().getMaxDelayMs());
    }

    @Test
    void testCommonDlqProperties() {
        assertFalse(properties.getCommon().getDlq().isEnabled());
        assertEquals(".deadletter", properties.getCommon().getDlq().getTopicSuffix());
    }

    @Test
    void testCommonDedupProperties() {
        assertTrue(properties.getCommon().getDedup().isEnabled());
        assertEquals(120000, properties.getCommon().getDedup().getTtlMs());
    }

    @Test
    void testDefaultValues() {
        // Create a new instance to test default values
        AdharMessagingProperties defaultProperties = new AdharMessagingProperties();
        
        // Test default values
        assertTrue(defaultProperties.isEnabled());
        assertTrue(defaultProperties.getKafka().isEnabled());
        assertEquals("localhost:9092", defaultProperties.getKafka().getBootstrapServers());
        assertEquals("default-topic", defaultProperties.getKafka().getDefaultTopic());
        
        assertEquals("all", defaultProperties.getKafka().getProducer().getAcks());
        assertEquals(3, defaultProperties.getKafka().getProducer().getRetries());
        assertEquals(16384, defaultProperties.getKafka().getProducer().getBatchSize());
        
        assertEquals("adhar-consumer-group", defaultProperties.getKafka().getConsumer().getGroupId());
        assertEquals("latest", defaultProperties.getKafka().getConsumer().getAutoOffsetReset());
        assertTrue(defaultProperties.getKafka().getConsumer().isEnableAutoCommit());
        assertEquals(500, defaultProperties.getKafka().getConsumer().getMaxPollRecords());
        
        assertTrue(defaultProperties.getRabbitmq().isEnabled());
        assertEquals("localhost", defaultProperties.getRabbitmq().getHost());
        assertEquals(5672, defaultProperties.getRabbitmq().getPort());
        assertEquals("guest", defaultProperties.getRabbitmq().getUsername());
        assertEquals("guest", defaultProperties.getRabbitmq().getPassword());
        assertEquals("/", defaultProperties.getRabbitmq().getVirtualHost());
        assertEquals("adhar.default", defaultProperties.getRabbitmq().getDefaultExchange());
        assertEquals("adhar.default", defaultProperties.getRabbitmq().getDefaultRoutingKey());
        assertEquals("adhar.default", defaultProperties.getRabbitmq().getDefaultQueue());
        
        assertFalse(defaultProperties.getRabbitmq().getPublisher().isConfirms());
        assertFalse(defaultProperties.getRabbitmq().getPublisher().isReturns());
        assertEquals("topic", defaultProperties.getRabbitmq().getPublisher().getExchangeType());
        assertTrue(defaultProperties.getRabbitmq().getPublisher().isExchangeDurable());
        assertFalse(defaultProperties.getRabbitmq().getPublisher().isExchangeAutoDelete());
        
        assertTrue(defaultProperties.getRabbitmq().getListener().isAutoDeclare());
        assertTrue(defaultProperties.getRabbitmq().getListener().isQueueDurable());
        assertFalse(defaultProperties.getRabbitmq().getListener().isQueueExclusive());
        assertFalse(defaultProperties.getRabbitmq().getListener().isQueueAutoDelete());
        assertEquals(250, defaultProperties.getRabbitmq().getListener().getPrefetchCount());
        assertEquals(1, defaultProperties.getRabbitmq().getListener().getConcurrency());
        assertEquals(10, defaultProperties.getRabbitmq().getListener().getMaxConcurrency());
        
        assertEquals("json", defaultProperties.getCommon().getDefaultSerializationFormat());
        assertTrue(defaultProperties.getCommon().isTraceEnabled());
        assertTrue(defaultProperties.getCommon().isValidationEnabled());
        assertEquals(30000, defaultProperties.getCommon().getDefaultTimeoutMs());
        assertEquals(3, defaultProperties.getCommon().getDefaultRetries());
        assertEquals(1000, defaultProperties.getCommon().getDefaultBackoffMs());

        assertTrue(defaultProperties.getCommon().getRetry().isEnabled());
        assertEquals(3, defaultProperties.getCommon().getRetry().getMaxAttempts());
        assertEquals(200, defaultProperties.getCommon().getRetry().getInitialDelayMs());
        assertEquals(2.0, defaultProperties.getCommon().getRetry().getBackoffMultiplier());
        assertEquals(5000, defaultProperties.getCommon().getRetry().getMaxDelayMs());

        assertTrue(defaultProperties.getCommon().getDlq().isEnabled());
        assertEquals(".dlq", defaultProperties.getCommon().getDlq().getTopicSuffix());

        assertFalse(defaultProperties.getCommon().getDedup().isEnabled());
        assertEquals(600_000, defaultProperties.getCommon().getDedup().getTtlMs());
    }

    /**
     * Test configuration that enables the properties.
     */
    @EnableConfigurationProperties(AdharMessagingProperties.class)
    static class TestConfiguration {
    }
}