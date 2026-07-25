package com.adhar.kit.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * A single transactional-outbox record: a message that has been durably persisted
 * (in the same transaction as the business change, when a JDBC-backed store is used)
 * and is awaiting relay to the broker by the {@link OutboxRelay}.
 * <p>
 * The payload is stored as a JSON string alongside the fully-qualified class name of
 * the original payload type, so the relay can rebuild the original object before
 * publishing it through the regular {@link com.adhar.kit.messaging.core.MessagePublisher}.
 */
public class OutboxEntry {

    /** Unique identifier of this entry. */
    private String id;

    /** Destination (topic/exchange) the message should be published to. */
    private String destination;

    /** Optional partition/routing key. */
    private String routingKey;

    /** JSON-serialized payload. */
    private String payload;

    /** Fully-qualified class name of the original payload, used to deserialize before relay. */
    private String payloadType;

    /** Current lifecycle status. */
    private OutboxStatus status = OutboxStatus.PENDING;

    /** Number of relay attempts made so far. */
    private int attempts;

    /** When the entry was persisted. */
    private Instant createdAt;

    /** When the last relay attempt was made, or {@code null} if never attempted. */
    private Instant lastAttemptAt;

    /** Description of the last relay failure, or {@code null}. */
    private String lastError;

    public OutboxEntry() {
    }

    /**
     * Creates a new {@link OutboxStatus#PENDING} entry with a generated id and the
     * current timestamp.
     *
     * @param destination destination to publish to
     * @param routingKey  optional partition/routing key (may be {@code null})
     * @param payload     JSON-serialized payload
     * @param payloadType fully-qualified class name of the original payload
     * @return the new pending entry
     */
    public static OutboxEntry pending(String destination, String routingKey, String payload, String payloadType) {
        OutboxEntry entry = new OutboxEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setDestination(destination);
        entry.setRoutingKey(routingKey);
        entry.setPayload(payload);
        entry.setPayloadType(payloadType);
        entry.setStatus(OutboxStatus.PENDING);
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public String toString() {
        return "OutboxEntry{id=" + id + ", destination=" + destination + ", status=" + status
                + ", attempts=" + attempts + "}";
    }
}
