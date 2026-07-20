package com.adhar.kit.core.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.function.LongSupplier;

/**
 * Distributed 64-bit unique ID generator (Twitter Snowflake layout).
 *
 * <p>Generated IDs are positive, roughly time-ordered longs composed of:</p>
 * <ul>
 *   <li>41 bits - milliseconds since a custom epoch (2024-01-01T00:00:00Z)</li>
 *   <li>10 bits - node/worker id (0-1023)</li>
 *   <li>12 bits - per-millisecond sequence (0-4095)</li>
 * </ul>
 *
 * <p>The node id is resolved (in order) from the explicit configuration value,
 * the {@code adhar.core.snowflake.node-id} system property, the
 * {@code ADHAR_SNOWFLAKE_NODE_ID} environment variable, and finally a
 * hostname-derived fallback.</p>
 *
 * <p>If the wall clock moves backwards the generator does not hand out
 * duplicate or out-of-order IDs; it waits until the clock catches up with the
 * last observed timestamp (clock-drift guard).</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42);
 * long id = generator.nextId();
 *
 * long ts = SnowflakeIdGenerator.extractTimestamp(id);   // epoch millis
 * long node = SnowflakeIdGenerator.extractNodeId(id);    // 42
 * long seq = SnowflakeIdGenerator.extractSequence(id);   // 0-4095
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SnowflakeIdGenerator {

    /** Custom epoch: 2024-01-01T00:00:00Z. */
    public static final long CUSTOM_EPOCH = 1704067200000L;

    static final long NODE_ID_BITS = 10L;
    static final long SEQUENCE_BITS = 12L;

    /** Maximum node id (1023). */
    public static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    static final long NODE_ID_SHIFT = SEQUENCE_BITS;
    static final long TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;

    /** System property consulted when no explicit node id is configured. */
    public static final String NODE_ID_PROPERTY = "adhar.core.snowflake.node-id";
    /** Environment variable consulted when no explicit node id is configured. */
    public static final String NODE_ID_ENV = "ADHAR_SNOWFLAKE_NODE_ID";

    private final long nodeId;
    private final LongSupplier clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Creates a generator with an auto-resolved node id.
     */
    public SnowflakeIdGenerator() {
        this(-1);
    }

    /**
     * Creates a generator with the given node id.
     *
     * @param nodeId node id (0-1023); negative values trigger auto-resolution
     *               from system property/environment/hostname
     */
    public SnowflakeIdGenerator(long nodeId) {
        this(nodeId, System::currentTimeMillis);
    }

    /**
     * Creates a generator with the given node id and clock (visible for testing).
     *
     * @param nodeId node id (0-1023); negative values trigger auto-resolution
     * @param clock  millisecond clock source
     */
    public SnowflakeIdGenerator(long nodeId, LongSupplier clock) {
        long resolved = nodeId < 0 ? resolveNodeId() : nodeId;
        if (resolved > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                "Node id must be between 0 and " + MAX_NODE_ID + " but was " + resolved);
        }
        this.nodeId = resolved;
        this.clock = clock;
        log.info("SnowflakeIdGenerator initialized with nodeId={}", resolved);
    }

    /**
     * Generates the next unique ID.
     *
     * @return 64-bit unique, time-ordered ID
     */
    public synchronized long nextId() {
        long timestamp = clock.getAsLong();

        if (timestamp < lastTimestamp) {
            // Clock drift guard: wait until the clock catches up instead of
            // issuing duplicate/out-of-order IDs.
            log.warn("Clock moved backwards by {}ms; waiting for clock to catch up",
                lastTimestamp - timestamp);
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // Sequence exhausted within this millisecond - spin to the next one
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
            | (nodeId << NODE_ID_SHIFT)
            | sequence;
    }

    /**
     * Gets the node id used by this generator.
     *
     * @return node id (0-1023)
     */
    public long getNodeId() {
        return nodeId;
    }

    /**
     * Extracts the timestamp (epoch millis) from an ID.
     *
     * @param id snowflake ID
     * @return timestamp in epoch milliseconds
     */
    public static long extractTimestamp(long id) {
        return (id >>> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
    }

    /**
     * Extracts the node id from an ID.
     *
     * @param id snowflake ID
     * @return node id
     */
    public static long extractNodeId(long id) {
        return (id >>> NODE_ID_SHIFT) & MAX_NODE_ID;
    }

    /**
     * Extracts the sequence from an ID.
     *
     * @param id snowflake ID
     * @return sequence number
     */
    public static long extractSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    private long waitUntil(long targetTimestamp) {
        long timestamp = clock.getAsLong();
        while (timestamp < targetTimestamp) {
            Thread.onSpinWait();
            timestamp = clock.getAsLong();
        }
        return timestamp;
    }

    /**
     * Resolves a node id from the system property, environment variable, or a
     * hostname-derived fallback.
     *
     * @return node id in the range 0-1023
     */
    static long resolveNodeId() {
        String configured = System.getProperty(NODE_ID_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(NODE_ID_ENV);
        }

        if (configured != null && !configured.isBlank()) {
            try {
                long nodeId = Long.parseLong(configured.trim());
                if (nodeId >= 0 && nodeId <= MAX_NODE_ID) {
                    return nodeId;
                }
                log.warn("Configured snowflake node id {} out of range 0-{}; falling back", nodeId, MAX_NODE_ID);
            } catch (NumberFormatException e) {
                log.warn("Invalid snowflake node id '{}'; falling back", configured);
            }
        }

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return Math.floorMod(hostname.hashCode(), (int) (MAX_NODE_ID + 1));
        } catch (Exception e) {
            long random = new SecureRandom().nextLong(MAX_NODE_ID + 1);
            log.warn("Unable to resolve hostname for snowflake node id; using random node id {}", random);
            return random;
        }
    }
}
