package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.eventsourcing.bus.DomainEventKafkaSerde;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprEventStore} with a mocked {@link DaprFacade}.
 */
class DaprEventStoreTest {

    private DaprFacade daprFacade;
    private DomainEventKafkaSerde serde;
    private DaprEventStore store;

    @BeforeEach
    void setUp() {
        daprFacade = mock(DaprFacade.class);
        serde = new DomainEventKafkaSerde(new ObjectMapper(), new EventTypeRegistry());
        store = new DaprEventStore(daprFacade, "statestore", serde, new UpcasterChain(List.of()));
    }

    private DomainEvent event(String aggregateId, int version) {
        return new DomainEvent("e-" + version, aggregateId, "Order", version,
                "order.created", "{\"total\":" + version + "}",
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void firstAppendCreatesStreamWithNullEtag() {
        when(daprFacade.getStateWithETag("statestore", "es:agg-1", DaprEventStore.StreamDocument.class))
                .thenReturn(new StateWithETag<>(null, null));
        when(daprFacade.saveStateWithETag(eq("statestore"), eq("es:agg-1"), any(), isNull()))
                .thenReturn(true);

        store.saveEvents("agg-1", List.of(event("agg-1", 1), event("agg-1", 2)), 0);

        ArgumentCaptor<DaprEventStore.StreamDocument> doc =
                ArgumentCaptor.forClass(DaprEventStore.StreamDocument.class);
        verify(daprFacade).saveStateWithETag(eq("statestore"), eq("es:agg-1"), doc.capture(), isNull());
        assertThat(doc.getValue().getVersion()).isEqualTo(2);
        assertThat(doc.getValue().getEvents()).hasSize(2);
    }

    @Test
    void versionMismatchThrowsConcurrencyException() {
        DaprEventStore.StreamDocument existing = new DaprEventStore.StreamDocument();
        existing.setVersion(3);
        when(daprFacade.getStateWithETag("statestore", "es:agg-1", DaprEventStore.StreamDocument.class))
                .thenReturn(new StateWithETag<>(existing, "etag-3"));

        assertThatThrownBy(() -> store.saveEvents("agg-1", List.of(event("agg-1", 2)), 1))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("expected version 1 but found 3");
    }

    @Test
    void etagConflictOnWriteThrowsConcurrencyException() {
        DaprEventStore.StreamDocument existing = new DaprEventStore.StreamDocument();
        existing.setVersion(1);
        existing.getEvents().add(serde.serialize(event("agg-1", 1)));
        DaprEventStore.StreamDocument racedAhead = new DaprEventStore.StreamDocument();
        racedAhead.setVersion(2);

        when(daprFacade.getStateWithETag("statestore", "es:agg-1", DaprEventStore.StreamDocument.class))
                .thenReturn(new StateWithETag<>(existing, "etag-1"))
                .thenReturn(new StateWithETag<>(racedAhead, "etag-2"));
        when(daprFacade.saveStateWithETag(eq("statestore"), eq("es:agg-1"), any(), eq("etag-1")))
                .thenReturn(false);

        assertThatThrownBy(() -> store.saveEvents("agg-1", List.of(event("agg-1", 2)), 1))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("found 2");
    }

    @Test
    void getEventsRoundTripsThroughSerde() {
        DomainEvent first = event("agg-1", 1);
        DomainEvent second = event("agg-1", 2);
        DaprEventStore.StreamDocument doc = new DaprEventStore.StreamDocument();
        doc.setVersion(2);
        doc.getEvents().add(serde.serialize(first));
        doc.getEvents().add(serde.serialize(second));
        when(daprFacade.getState("statestore", "es:agg-1", DaprEventStore.StreamDocument.class))
                .thenReturn(doc);

        assertThat(store.getEvents("agg-1")).containsExactly(first, second);
        assertThat(store.getEventsAfterVersion("agg-1", 1)).containsExactly(second);
    }

    @Test
    void missingStreamReturnsEmptyList() {
        when(daprFacade.getState("statestore", "es:missing", DaprEventStore.StreamDocument.class))
                .thenReturn(null);

        assertThat(store.getEvents("missing")).isEmpty();
    }

    @Test
    void emptyEventListIsANoOp() {
        store.saveEvents("agg-1", List.of(), 0);
        verify(daprFacade, org.mockito.Mockito.never())
                .saveStateWithETag(any(), any(), any(), any());
    }
}
