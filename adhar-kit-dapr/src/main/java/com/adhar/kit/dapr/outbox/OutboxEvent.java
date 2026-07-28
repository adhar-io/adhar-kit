package com.adhar.kit.dapr.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single event persisted in the transactional outbox before being relayed to a Dapr pub/sub
 * topic. Stored as JSON in the Dapr state store keyed by {@link #getId()}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    /** Unique event id (also the state-store key suffix). */
    private String id;

    /** Target pub/sub component name. */
    private String pubsubName;

    /** Target topic. */
    private String topic;

    /** Event payload (serialized as JSON). */
    private Object payload;

    /** Current lifecycle status. */
    private OutboxStatus status;

    /** Number of publish attempts made so far. */
    private int attempts;

    /** Creation timestamp (epoch millis). */
    private long createdAtEpochMs;

    /** Last failure message, if any. */
    private String lastError;
}
