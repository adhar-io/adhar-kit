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
import org.testcontainers.kafka.ConfluentKafkaContainer;
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

    // Unique per test (assigned in setUp) so tests never read each other's messages
    // from the shared static broker — a shared topic/group caused stale reads and
    // offset races (manifesting as "header null" and "message not received" timeouts).
    private String TEST_TOPIC;
    private static final String TEST_PAYLOAD = "Test message";

    @Container
    private static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private EmbeddedKafkaBroker embeddedKafka;
    private KafkaMessagePublisher publisher;
    private KafkaMessageListener listener;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private AdharMessagingProperties properties;

    @BeforeEach
    void setUp() {
        // Unique topic + consumer group per test => full isolation on the shared broker.
        TEST_TOPIC = "itest-topic-" + java.util.UUID.randomUUID();

        // Set up properties
        properties = new AdharMessagingProperties();
        properties.getKafka().setBootstrapServers(kafka.getBootstrapServers());
        properties.getKafka().setDefaultTopic(TEST_TOPIC);
        properties.getKafka().getConsumer().setGroupId("itest-group-" + java.util.UUID.randomUUID());

        // Set up producer factory and template
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Set up consumer factory and container factory
        // Note: in spring-kafka-test 4.x the consumerProps(String, String, String) overload takes
        // (bootstrapServers, groupId, autoCommit) - the argument order changed from 3.x.
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "test-group", "true");
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
        // Collect every payload received; the message published while paused is not
        // dropped by Kafka — it is delivered once the consumer resumes — so we wait
        // until the post-resume message has been delivered.
        java.util.List<String> received = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // Subscribe to the topic
        String consumerId = listener.subscribe(TEST_TOPIC, String.class, received::add);

        // Pause the consumer
        boolean pauseResult = listener.pause(consumerId);
        assertTrue(pauseResult);

        // Publish a message (should not be received while paused)
        boolean publishResult = publisher.publish(TEST_PAYLOAD + "-paused");
        assertTrue(publishResult);

        // Wait a bit to ensure nothing is delivered while paused
        Thread.sleep(5000);
        assertTrue(received.isEmpty(), "No messages should be delivered while paused");

        // Resume the consumer
        boolean resumeResult = listener.resume(consumerId);
        assertTrue(resumeResult);

        // Publish another message (delivered after resume, along with the paused one)
        boolean publishResult2 = publisher.publish(TEST_PAYLOAD + "-resumed");
        assertTrue(publishResult2);

        // Wait until the post-resume message has been delivered
        long deadline = System.currentTimeMillis() + 30_000;
        while (!received.contains(TEST_PAYLOAD + "-resumed") && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(received.contains(TEST_PAYLOAD + "-resumed"),
                "Resumed message was not received within timeout");
        // The message published while paused is delivered too (not dropped).
        assertTrue(received.contains(TEST_PAYLOAD + "-paused"),
                "Message published while paused should be delivered after resume");

        // Unsubscribe
        listener.unsubscribe(consumerId);
    }
}