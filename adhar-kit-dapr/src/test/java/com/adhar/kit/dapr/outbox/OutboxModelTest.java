package com.adhar.kit.dapr.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the outbox model POJOs ({@link OutboxEvent}, {@link OutboxIndex},
 * {@link OutboxRelayResult}, {@link OutboxStatus}).
 */
class OutboxModelTest {

    @Test
    void outboxEventAccessors() {
        OutboxEvent event = new OutboxEvent();
        event.setId("id");
        event.setPubsubName("pubsub");
        event.setTopic("topic");
        event.setPayload("payload");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(2);
        event.setCreatedAtEpochMs(123L);
        event.setLastError("err");

        assertThat(event.getId()).isEqualTo("id");
        assertThat(event.getPubsubName()).isEqualTo("pubsub");
        assertThat(event.getTopic()).isEqualTo("topic");
        assertThat(event.getPayload()).isEqualTo("payload");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getCreatedAtEpochMs()).isEqualTo(123L);
        assertThat(event.getLastError()).isEqualTo("err");

        OutboxEvent full = new OutboxEvent("i", "p", "t", "d", OutboxStatus.DEAD, 5, 1L, "e");
        assertThat(full.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(full).isEqualTo(new OutboxEvent("i", "p", "t", "d", OutboxStatus.DEAD, 5, 1L, "e"));
        assertThat(full.toString()).contains("DEAD");
        assertThat(full.hashCode()).isNotZero();
    }

    @Test
    void outboxIndexDefaults() {
        OutboxIndex index = new OutboxIndex();
        assertThat(index.getPendingIds()).isEmpty();
        index.getPendingIds().add("a");
        assertThat(index.getPendingIds()).containsExactly("a");
    }

    @Test
    void outboxRelayResultTotals() {
        OutboxRelayResult result = new OutboxRelayResult(3, 2, 1);
        assertThat(result.getPublished()).isEqualTo(3);
        assertThat(result.getRetried()).isEqualTo(2);
        assertThat(result.getDead()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(6);
    }

    @Test
    void outboxStatusValues() {
        assertThat(OutboxStatus.valueOf("PENDING")).isEqualTo(OutboxStatus.PENDING);
        assertThat(OutboxStatus.values()).containsExactly(
            OutboxStatus.PENDING, OutboxStatus.PUBLISHED, OutboxStatus.DEAD);
    }
}
