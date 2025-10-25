package com.adhar.adharkit.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a cache operation message that is sent between cache instances.
 * <p>
 * This class is used for communication between distributed cache instances to
 * synchronize cache operations like put, evict, and clear.
 * <p>
 * The message contains information about:
 * - The type of operation (PUT, EVICT, CLEAR)
 * - The cache name
 * - The key (for PUT and EVICT operations)
 * - The value (for PUT operations)
 * - Metadata like timestamp and source instance ID
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The type of cache operation.
     */
    private OperationType operationType;

    /**
     * The name of the cache.
     */
    private String cacheName;

    /**
     * The cache key (for PUT and EVICT operations).
     */
    private String key;

    /**
     * The cache value as a serialized string (for PUT operations).
     */
    private String value;

    /**
     * The class name of the value (for deserialization).
     */
    private String valueType;

    /**
     * The time-to-live in milliseconds (for PUT operations).
     */
    private Long timeToLive;

    /**
     * The timestamp when the message was created.
     */
    private Instant timestamp;

    /**
     * The ID of the instance that created the message.
     * Used to prevent processing messages sent by the same instance.
     */
    private String sourceInstanceId;

    /**
     * Enum representing the type of cache operation.
     */
    public enum OperationType {
        /**
         * Put a value in the cache.
         */
        PUT,

        /**
         * Evict a value from the cache.
         */
        EVICT,

        /**
         * Clear the entire cache.
         */
        CLEAR
    }
}