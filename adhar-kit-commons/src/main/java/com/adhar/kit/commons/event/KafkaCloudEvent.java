package com.adhar.kit.commons.event;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka-specific CloudEvent wrapper.
 *
 * <p>Wraps a CloudEvent and adds Kafka-specific metadata:</p>
 * <ul>
 *   <li><b>partitionKey</b> - Kafka partition key for routing</li>
 *   <li><b>topic</b> - Kafka topic name</li>
 *   <li><b>headers</b> - Kafka headers</li>
 * </ul>
 *
 * <p><b>Example - Publishing to Kafka:</b></p>
 * <pre>{@code
 * CloudEvent<Order> cloudEvent = CloudEvent.<Order>create(
 *     "com.example.order.created",
 *     URI.create("https://orders.example.com"),
 *     order
 * );
 *
 * KafkaCloudEvent<Order> kafkaEvent = new KafkaCloudEvent<>(
 *     cloudEvent,
 *     "order-events",
 *     order.getCustomerId()  // Partition key
 * );
 *
 * kafkaEvent.addHeader("tenant-id", tenantId);
 * kafkaEvent.addHeader("correlation-id", correlationId);
 *
 * kafkaTemplate.send(kafkaEvent.getTopic(), kafkaEvent.getPartitionKey(), kafkaEvent.getEvent());
 * }</pre>
 *
 * @param <T> event data type
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see CloudEvent
 */
@Data
public class KafkaCloudEvent<T> {

    /**
     * The wrapped CloudEvent.
     */
    private final CloudEvent<T> event;

    /**
     * Kafka topic name.
     */
    private final String topic;

    /**
     * Kafka partition key for routing.
     */
    private final String partitionKey;

    /**
     * Kafka headers.
     */
    private final Map<String, String> headers;

    /**
     * Kafka offset (populated after publishing).
     */
    private Long offset;

    /**
     * Kafka partition (populated after publishing).
     */
    private Integer partition;

    /**
     * Kafka timestamp (populated after publishing).
     */
    private Long kafkaTimestamp;

    /**
     * Constructor.
     */
    public KafkaCloudEvent(CloudEvent<T> event, String topic, String partitionKey) {
        this.event = event;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.headers = new HashMap<>();

        // Add Kafka metadata to CloudEvent extensions
        event.extensionAttribute("partitionkey", partitionKey);
        event.extensionAttribute("kafkatopic", topic);
    }

    /**
     * Adds a Kafka header.
     *
     * @param key header key
     * @param value header value
     * @return this instance
     */
    public KafkaCloudEvent<T> addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    /**
     * Gets a header value.
     *
     * @param key header key
     * @return header value or null
     */
    public String getHeader(String key) {
        return headers.get(key);
    }

    /**
     * Sets publishing metadata after Kafka send.
     *
     * @param offset Kafka offset
     * @param partition Kafka partition
     * @param timestamp Kafka timestamp
     */
    public void setPublishingMetadata(long offset, int partition, long timestamp) {
        this.offset = offset;
        this.partition = partition;
        this.kafkaTimestamp = timestamp;

        // Update CloudEvent extensions
        event.extensionAttribute("kafkaoffset", offset);
        event.extensionAttribute("kafkapartition", partition);
        event.extensionAttribute("kafkatimestamp", timestamp);
    }
}

