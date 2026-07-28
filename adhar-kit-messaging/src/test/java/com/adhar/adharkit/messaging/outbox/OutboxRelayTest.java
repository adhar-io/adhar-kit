package com.adhar.adharkit.messaging.outbox;

import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.outbox.InMemoryOutboxStore;
import com.adhar.kit.messaging.outbox.OutboxEntry;
import com.adhar.kit.messaging.outbox.OutboxPayloadCodec;
import com.adhar.kit.messaging.outbox.OutboxRelay;
import com.adhar.kit.messaging.outbox.OutboxStatus;
import com.adhar.kit.messaging.properties.AdharMessagingProperties.OutboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

/**
 * Tests for {@link OutboxRelay} using an in-memory store and a mocked publisher (no broker).
 */
class OutboxRelayTest {

    private InMemoryOutboxStore store;
    private MessagePublisher publisher;
    private OutboxPayloadCodec codec;
    private OutboxProperties properties;

    @BeforeEach
    void setUp() {
        store = new InMemoryOutboxStore();
        publisher = mock(MessagePublisher.class);
        codec = new OutboxPayloadCodec();
        properties = new OutboxProperties();
        properties.setMaxAttempts(2);
    }

    private OutboxRelay newRelay() {
        return new OutboxRelay(store, publisher, codec, properties, null);
    }

    @Test
    void relayPublishesPendingEntryAndMarksPublished() {
        when(publisher.publish("orders", "payload")).thenReturn(true);
        OutboxEntry entry = OutboxEntry.pending("orders", null, codec.serialize("payload"), String.class.getName());
        store.save(entry);

        int published = newRelay().relayOnce();

        assertEquals(1, published);
        verify(publisher).publish("orders", "payload");
        OutboxEntry stored = store.findById(entry.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, stored.getStatus());
        assertEquals(1, stored.getAttempts());
    }

    @Test
    void relayUsesRoutingKeyWhenPresent() {
        when(publisher.publish(eq("orders"), eq("cust-1"), any())).thenReturn(true);
        OutboxEntry entry = OutboxEntry.pending("orders", "cust-1", codec.serialize("payload"), String.class.getName());
        store.save(entry);

        newRelay().relayOnce();

        verify(publisher).publish("orders", "cust-1", "payload");
    }

    @Test
    void failedPublishMarksEntryFailedThenDeadAfterMaxAttempts() {
        when(publisher.publish("orders", "payload")).thenReturn(false);
        OutboxEntry entry = OutboxEntry.pending("orders", null, codec.serialize("payload"), String.class.getName());
        store.save(entry);
        OutboxRelay relay = newRelay();

        relay.relayOnce();
        OutboxEntry afterFirst = store.findById(entry.getId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, afterFirst.getStatus());
        assertEquals(1, afterFirst.getAttempts());

        relay.relayOnce();
        OutboxEntry afterSecond = store.findById(entry.getId()).orElseThrow();
        assertEquals(OutboxStatus.DEAD, afterSecond.getStatus());
        assertEquals(2, afterSecond.getAttempts());
        assertEquals("publisher returned false", afterSecond.getLastError());
    }

    @Test
    void deadEntriesAreNoLongerRelayed() {
        when(publisher.publish("orders", "payload")).thenReturn(false);
        OutboxEntry entry = OutboxEntry.pending("orders", null, codec.serialize("payload"), String.class.getName());
        store.save(entry);
        OutboxRelay relay = newRelay();

        relay.relayOnce();
        relay.relayOnce(); // now DEAD
        int publishedThirdPass = relay.relayOnce();

        assertEquals(0, publishedThirdPass);
    }

    @Test
    void publisherExceptionIsRecordedAsFailure() {
        when(publisher.publish("orders", "payload")).thenThrow(new RuntimeException("broker down"));
        OutboxEntry entry = OutboxEntry.pending("orders", null, codec.serialize("payload"), String.class.getName());
        store.save(entry);

        newRelay().relayOnce();

        OutboxEntry stored = store.findById(entry.getId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, stored.getStatus());
        assertEquals("broker down", stored.getLastError());
    }

    @Test
    void metricsAreRecordedForSuccessAndFailure() {
        MessagingMetrics metrics = mock(MessagingMetrics.class);
        when(publisher.publish("ok", "p")).thenReturn(true);
        when(publisher.publish("bad", "p")).thenReturn(false);
        store.save(OutboxEntry.pending("ok", null, codec.serialize("p"), String.class.getName()));
        store.save(OutboxEntry.pending("bad", null, codec.serialize("p"), String.class.getName()));

        new OutboxRelay(store, publisher, codec, properties, metrics).relayOnce();

        verify(metrics).recordPublish("ok");
        verify(metrics).recordPublishFailure("bad");
    }

    @Test
    void startSchedulesBackgroundRelayAndStopIsIdempotent() {
        when(publisher.publish("orders", "payload")).thenReturn(true);
        store.save(OutboxEntry.pending("orders", null, codec.serialize("payload"), String.class.getName()));
        properties.setRelayIntervalMs(10);
        OutboxRelay relay = newRelay();

        relay.start();
        relay.start(); // idempotent - no second scheduler
        try {
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> store.countByStatus(OutboxStatus.PUBLISHED) == 1);
        } finally {
            relay.stop();
            relay.stop(); // idempotent
        }
        assertEquals(1, store.countByStatus(OutboxStatus.PUBLISHED));
    }
}
