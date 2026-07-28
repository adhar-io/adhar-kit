package com.adhar.kit.dapr.outbox;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutboxPublisher}. The Dapr state store is simulated with an in-memory
 * map behind a mocked {@link DaprFacade}, so persistence and relay logic are exercised end to
 * end without a sidecar.
 */
class OutboxPublisherTest {

    private static final String STORE = "statestore";
    private static final String PUBSUB = "pubsub";

    private DaprFacade facade;
    private Map<String, Object> store;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        facade = mock(DaprFacade.class);
        store = new HashMap<>();

        when(facade.getStateWithETag(eq(STORE), anyString(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(1);
            Object value = store.get(key);
            return new StateWithETag<>(value, value != null ? "etag" : null);
        });
        doAnswer(inv -> {
            store.put(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(facade).saveState(eq(STORE), anyString(), any());
        when(facade.saveStateWithETag(eq(STORE), anyString(), any(), any())).thenAnswer(inv -> {
            store.put(inv.getArgument(1), inv.getArgument(2));
            return true;
        });

        publisher = new OutboxPublisher(facade, STORE, PUBSUB);
    }

    @Test
    void appendPersistsEventAndIndexesIt() {
        String id = publisher.append("orders", "order-payload");

        Optional<OutboxEvent> stored = publisher.getEvent(id);
        assertThat(stored).isPresent();
        assertThat(stored.get().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(stored.get().getPubsubName()).isEqualTo(PUBSUB);
        assertThat(stored.get().getTopic()).isEqualTo("orders");

        OutboxIndex index = (OutboxIndex) store.get(OutboxPublisher.INDEX_KEY);
        assertThat(index.getPendingIds()).contains(id);
    }

    @Test
    void appendWithExplicitPubsub() {
        String id = publisher.append("other-pubsub", "topic", "p");

        assertThat(publisher.getEvent(id)).get()
            .extracting(OutboxEvent::getPubsubName).isEqualTo("other-pubsub");
    }

    @Test
    void relayPublishesPendingEvents() {
        doNothing().when(facade).publishEvent(anyString(), anyString(), any());
        String id1 = publisher.append("t1", "p1");
        String id2 = publisher.append("t2", "p2");

        OutboxRelayResult result = publisher.relay();

        assertThat(result.getPublished()).isEqualTo(2);
        assertThat(result.getRetried()).isZero();
        assertThat(result.getDead()).isZero();
        assertThat(result.total()).isEqualTo(2);
        assertThat(publisher.getEvent(id1)).get()
            .extracting(OutboxEvent::getStatus).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publisher.getEvent(id2)).get()
            .extracting(OutboxEvent::getStatus).isEqualTo(OutboxStatus.PUBLISHED);

        OutboxIndex index = (OutboxIndex) store.get(OutboxPublisher.INDEX_KEY);
        assertThat(index.getPendingIds()).isEmpty();

        verify(facade).publishEvent(PUBSUB, "t1", "p1");
        verify(facade).publishEvent(PUBSUB, "t2", "p2");
    }

    @Test
    void relayRetriesThenDeadLetters() {
        OutboxPublisher retryPublisher = new OutboxPublisher(facade, STORE, PUBSUB, 2);
        doThrow(new RuntimeException("broker down"))
            .when(facade).publishEvent(anyString(), anyString(), any());
        String id = retryPublisher.append("t", "p");

        // First pass: failure -> stays pending, retried.
        OutboxRelayResult first = retryPublisher.relay();
        assertThat(first.getRetried()).isEqualTo(1);
        assertThat(first.getDead()).isZero();
        OutboxEvent afterFirst = retryPublisher.getEvent(id).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastError()).isEqualTo("broker down");

        // Second pass: exhausts attempts -> DEAD, removed from index.
        OutboxRelayResult second = retryPublisher.relay();
        assertThat(second.getDead()).isEqualTo(1);
        OutboxEvent afterSecond = retryPublisher.getEvent(id).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(afterSecond.getAttempts()).isEqualTo(2);

        OutboxIndex index = (OutboxIndex) store.get(OutboxPublisher.INDEX_KEY);
        assertThat(index.getPendingIds()).doesNotContain(id);
    }

    @Test
    void relayWithNoPendingReturnsEmpty() {
        OutboxRelayResult result = publisher.relay();

        assertThat(result.total()).isZero();
    }

    @Test
    void relayDropsOrphanIndexEntries() {
        OutboxIndex index = new OutboxIndex();
        index.getPendingIds().add("ghost-id");
        store.put(OutboxPublisher.INDEX_KEY, index);

        OutboxRelayResult result = publisher.relay();

        assertThat(result.total()).isZero();
        OutboxIndex after = (OutboxIndex) store.get(OutboxPublisher.INDEX_KEY);
        assertThat(after.getPendingIds()).doesNotContain("ghost-id");
    }

    @Test
    void getEventUnknownReturnsEmpty() {
        assertThat(publisher.getEvent("nope")).isEmpty();
    }

    @Test
    void constructorRejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new OutboxPublisher(facade, STORE, PUBSUB, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNullFacade() {
        assertThatThrownBy(() -> new OutboxPublisher(null, STORE, PUBSUB))
            .isInstanceOf(NullPointerException.class);
    }
}
