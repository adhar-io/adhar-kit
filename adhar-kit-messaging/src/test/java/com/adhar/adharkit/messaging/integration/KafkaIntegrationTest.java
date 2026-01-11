package com.adhar.adharkit.messaging.integration;

import com.adhar.kit.messaging.core.Message;
import com.adhar.kit.messaging.kafka.KafkaMessageListener;
import com.adhar.kit.messaging.kafka.KafkaMessagePublisher;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka messaging using test containers.
 */
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"test-topic"})
class KafkaIntegrationTest {

    private static final String TEST_TOPIC = "test-topic";
    private static final String TEST_PAYLOAD = "Test message";

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    private EmbeddedKafkaBroker embeddedKafka;
    private KafkaMessagePublisher publisher;
    private KafkaMessageListener listener;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private AdharMessagingProperties properties;

    @BeforeEach
    void setUp() {
        // Set up properties
        properties = new AdharMessagingProperties();
        properties.getKafka().setBootstrapServers(kafka.getBootstrapServers());
        properties.getKafka().setDefaultTopic(TEST_TOPIC);

        // Set up producer factory and template
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Set up consumer factory and container factory
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", kafka.getBootstrapServers());
        DefaultKafkaConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ConcurrentKafkaListenerContainerFactory<String, Object> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(consumerFactory);

        // Create publisher and listener
        publisher = new KafkaMessagePublisher(kafkaTemplate, properties);
        listener = new KafkaMessageListener(containerFactory, new KafkaListenerEndpointRegistry(), properties);
    }

    @AfterEach
    void tearDown() {
        // Clean up resources
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    @Test
    void testPublishAndSubscribe() throws Exception {
        // Set up a latch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String> receivedMessages = new HashMap<>();

        // Subscribe to the topic
        String consumerId = listener.subscribe(TEST_TOPIC, String.class, payload -> {
            receivedMessages.put("payload", payload);
            latch.countDown();
        });

        // Publish a message
        boolean result = publisher.publish(TEST_PAYLOAD);
        assertTrue(result);

        // Wait for the message to be received
        boolean messageReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message was not received within timeout");
        assertEquals(TEST_PAYLOAD, receivedMessages.get("payload"));

        // Unsubscribe
        listener.unsubscribe(consumerId);
    }

    @Test
    void testPublishAndSubscribeWithHeaders() throws Exception {
        // Set up a latch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> receivedData = new HashMap<>();

        // Subscribe to the topic with headers
        String consumerId = listener.subscribeWithHeaders(TEST_TOPIC, String.class, (payload, headers) -> {
            receivedData.put("payload", payload);
            receivedData.put("header1", headers.get("header1"));
            latch.countDown();
        });

        // Publish a message with headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        boolean result = publisher.publish(TEST_TOPIC, headers, TEST_PAYLOAD);
        assertTrue(result);

        // Wait for the message to be received
        boolean messageReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message was not received within timeout");
        assertEquals(TEST_PAYLOAD, receivedData.get("payload"));
        assertEquals("value1", receivedData.get("header1"));

        // Unsubscribe
        listener.unsubscribe(consumerId);
    }

    @Test
    void testPublishAsyncAndSubscribe() throws Exception {
        // Set up a latch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String> receivedMessages = new HashMap<>();

        // Subscribe to the topic
        String consumerId = listener.subscribe(TEST_TOPIC, String.class, payload -> {
            receivedMessages.put("payload", payload);
            latch.countDown();
        });

        // Publish a message asynchronously
        CompletableFuture<Boolean> future = publisher.publishAsync(TEST_PAYLOAD);
        assertTrue(future.get(30, TimeUnit.SECONDS));

        // Wait for the message to be received
        boolean messageReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message was not received within timeout");
        assertEquals(TEST_PAYLOAD, receivedMessages.get("payload"));

        // Unsubscribe
        listener.unsubscribe(consumerId);
    }

    @Test
    void testPublishAndSubscribeWithMessage() throws Exception {
        // Set up a latch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String> receivedMessages = new HashMap<>();

        // Subscribe to the topic
        String consumerId = listener.subscribe(TEST_TOPIC, String.class, payload -> {
            receivedMessages.put("payload", payload);
            latch.countDown();
        });

        // Create a message
        Message<String> message = Message.<String>builder()
                .payload(TEST_PAYLOAD)
                .destination(TEST_TOPIC)
                .header("header1", "value1")
                .build();

        // Publish the message
        boolean result = publisher.publish(message.getDestination(), message.getHeaders(), message.getPayload());
        assertTrue(result);

        // Wait for the message to be received
        boolean messageReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message was not received within timeout");
        assertEquals(TEST_PAYLOAD, receivedMessages.get("payload"));

        // Unsubscribe
        listener.unsubscribe(consumerId);
    }

    @Test
    void testPauseAndResume() throws Exception {
        // Set up a latch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String> receivedMessages = new HashMap<>();

        // Subscribe to the topic
        String consumerId = listener.subscribe(TEST_TOPIC, String.class, payload -> {
            receivedMessages.put("payload", payload);
            latch.countDown();
        });

        // Pause the consumer
        boolean pauseResult = listener.pause(consumerId);
        assertTrue(pauseResult);

        // Publish a message (should not be received while paused)
        boolean publishResult = publisher.publish(TEST_PAYLOAD + "-paused");
        assertTrue(publishResult);

        // Wait a bit to ensure the message is not received
        Thread.sleep(5000);
        assertEquals(0, receivedMessages.size());

        // Resume the consumer
        boolean resumeResult = listener.resume(consumerId);
        assertTrue(resumeResult);

        // Publish another message (should be received)
        boolean publishResult2 = publisher.publish(TEST_PAYLOAD + "-resumed");
        assertTrue(publishResult2);

        // Wait for the message to be received
        boolean messageReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message was not received within timeout");
        assertEquals(TEST_PAYLOAD + "-resumed", receivedMessages.get("payload"));

        // Unsubscribe
        listener.unsubscribe(consumerId);
    }
}