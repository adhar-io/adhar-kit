package com.adhar.kit.dapr.outbox;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.support.MemoryDapr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * {@link OutboxPublisher} on an in-memory facade: the event and its index entry are written in
 * one transaction, the relay publishes under a lock, and failures retry then park as DEAD.
 */
class OutboxPublisherTest {

    private static final String STORE = "statestore";
    private static final String PUBSUB = "pubsub";

    private DaprFacade facade;
    private OutboxPublisher publisher;
    private final List<Object> delivered = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        facade = MemoryDapr.facade();
        publisher = new OutboxPublisher(facade, STORE, PUBSUB, 3, "lockstore");
        facade.subscribe("orders", Object.class, event -> {
            delivered.add(event.data());
            return DispatchStatus.SUCCESS;
        });
    }

    @Test
    void appendPersistsTheEventAndIndexesItTogether() {
        String id = publisher.append("orders", "payload-1");

        OutboxEvent event = publisher.getEvent(id).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPubsubName()).isEqualTo(PUBSUB);
        assertThat(facade.getState(STORE, OutboxPublisher.INDEX_KEY, OutboxIndex.class).getPendingIds())
                .containsExactly(id);
        assertThat(delivered).isEmpty();
    }

    @Test
    void appendWithExplicitPubsub() {
        String id = publisher.append("events", "orders", "p");
        assertThat(publisher.getEvent(id).orElseThrow().getPubsubName()).isEqualTo("events");
    }

    @Test
    void concurrentAppendsNeverLoseAnIndexEntry() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> ids = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int n = i;
            ids.add(pool.submit(() -> publisher.append("orders", "p-" + n)));
        }
        List<String> appended = new ArrayList<>();
        for (Future<String> id : ids) {
            appended.add(id.get());
        }
        pool.shutdown();

        assertThat(facade.getState(STORE, OutboxPublisher.INDEX_KEY, OutboxIndex.class).getPendingIds())
                .containsExactlyInAnyOrderElementsOf(appended);
    }

    @Test
    void relayPublishesPendingEventsAndEmptiesTheIndex() {
        String id = publisher.append("orders", "payload-1");

        OutboxRelayResult result = publisher.relay();

        assertThat(result.getPublished()).isEqualTo(1);
        assertThat(delivered).containsExactly("payload-1");
        assertThat(publisher.getEvent(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publisher.relay().total()).isZero();
    }

    @Test
    void relayRetriesThenParksTheEventAsDead() {
        DaprFacade failing = spy(facade);
        doThrow(new DaprError("Failed to publish event: broker down", 503, "ERR_PUBSUB", "broker down"))
                .when(failing).publishEvent(eq(PUBSUB), eq("orders"), any());
        OutboxPublisher flaky = new OutboxPublisher(failing, STORE, PUBSUB, 2, "lockstore");
        String id = flaky.append("orders", "payload-1");

        assertThat(flaky.relay().getRetried()).isEqualTo(1);
        assertThat(flaky.getEvent(id).orElseThrow().getLastError()).contains("broker down");
        assertThat(flaky.relay().getDead()).isEqualTo(1);
        assertThat(flaky.getEvent(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(flaky.relay().total()).isZero();
    }

    @Test
    void onlyOneReplicaRelaysAtATime() {
        publisher.append("orders", "payload-1");
        facade.lock().tryLock(OutboxPublisher.RELAY_LOCK, "other-replica", 60);

        assertThat(publisher.relay().total()).isZero();
        assertThat(delivered).isEmpty();

        facade.lock().unlock(OutboxPublisher.RELAY_LOCK, "other-replica");
        assertThat(publisher.relay().getPublished()).isEqualTo(1);
    }

    @Test
    void anUnusableLockStoreFallsBackToRelayingUnguarded() {
        DaprFacade noLocks = spy(facade);
        when(noLocks.tryLock(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new DaprError("Failed to acquire lock", 400, "ERR_LOCK_STORE_NOT_FOUND", "no lock store"));
        OutboxPublisher unguarded = new OutboxPublisher(noLocks, STORE, PUBSUB, 3, "missing");
        unguarded.append("orders", "payload-1");

        assertThat(unguarded.relay().getPublished()).isEqualTo(1);
    }

    @Test
    void orphanedIndexEntriesAreDropped() {
        String id = publisher.append("orders", "payload-1");
        facade.deleteState(STORE, OutboxPublisher.EVENT_KEY_PREFIX + id);

        assertThat(publisher.relay().total()).isZero();
        assertThat(facade.getState(STORE, OutboxPublisher.INDEX_KEY, OutboxIndex.class).getPendingIds()).isEmpty();
    }

    @Test
    void constructorRejectsInvalidArguments() {
        assertThatThrownBy(() -> new OutboxPublisher(facade, STORE, PUBSUB, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxPublisher(null, STORE, PUBSUB))
                .isInstanceOf(NullPointerException.class);
    }
}
