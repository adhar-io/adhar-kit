package com.adhar.kit.eventsourcing.repository;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.core.TestAggregate;
import com.adhar.kit.eventsourcing.store.ConcurrencyException;
import com.adhar.kit.eventsourcing.store.EventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryingAggregateRepository")
class RetryingAggregateRepositoryTest {

    @Mock
    private EventStore eventStore;

    private DomainEvent event(String aggregateId, int version, String type) {
        return new DomainEvent("evt-" + version, aggregateId, "TestAggregate",
                version, type, "{}", Instant.now());
    }

    @Test
    @DisplayName("constructor rejects maxAttempts less than 1")
    void constructorRejectsInvalidMaxAttempts() {
        AggregateRepository repository = new AggregateRepository(eventStore, new com.adhar.kit.eventsourcing.bus.SimpleEventBus());

        assertThatThrownBy(() -> new RetryingAggregateRepository(repository, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("executeWithRetry succeeds on first attempt when there is no conflict")
    void executeWithRetrySucceedsFirstAttempt() {
        var bus = new com.adhar.kit.eventsourcing.bus.SimpleEventBus();
        when(eventStore.getEvents("order-1")).thenReturn(List.of(event("order-1", 1, "OrderCreated")));
        AggregateRepository delegate = new AggregateRepository(eventStore, bus);
        RetryingAggregateRepository retrying = new RetryingAggregateRepository(delegate, 3);

        TestAggregate result = retrying.executeWithRetry("order-1", TestAggregate.class,
                aggregate -> aggregate.raise(event("order-1", 2, "OrderUpdated")));

        assertThat(result.getVersion()).isEqualTo(2);
        verify(eventStore, times(1)).saveEvents(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("executeWithRetry reloads and reapplies the command after a concurrency conflict")
    void executeWithRetryRetriesOnConflict() {
        var bus = new com.adhar.kit.eventsourcing.bus.SimpleEventBus();
        when(eventStore.getEvents("order-1")).thenReturn(List.of(event("order-1", 1, "OrderCreated")));
        AtomicInteger saveAttempts = new AtomicInteger();
        doThrow(new ConcurrencyException("order-1", 1, 2))
                .doNothing()
                .when(eventStore).saveEvents(anyString(), anyList(), anyInt());

        AggregateRepository delegate = new AggregateRepository(eventStore, bus);
        RetryingAggregateRepository retrying = new RetryingAggregateRepository(delegate, 3);

        TestAggregate result = retrying.executeWithRetry("order-1", TestAggregate.class, aggregate -> {
            saveAttempts.incrementAndGet();
            aggregate.raise(event("order-1", aggregate.getVersion() + 1, "OrderUpdated"));
        });

        assertThat(result).isNotNull();
        assertThat(saveAttempts.get()).isEqualTo(2);
        verify(eventStore, times(2)).getEvents("order-1");
        verify(eventStore, times(2)).saveEvents(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("executeWithRetry throws the last ConcurrencyException once attempts are exhausted")
    void executeWithRetryThrowsAfterExhaustingAttempts() {
        var bus = new com.adhar.kit.eventsourcing.bus.SimpleEventBus();
        when(eventStore.getEvents("order-1")).thenReturn(List.of(event("order-1", 1, "OrderCreated")));
        doThrow(new ConcurrencyException("order-1", 1, 2))
                .when(eventStore).saveEvents(anyString(), anyList(), anyInt());

        AggregateRepository delegate = new AggregateRepository(eventStore, bus);
        RetryingAggregateRepository retrying = new RetryingAggregateRepository(delegate, 2);

        assertThatThrownBy(() -> retrying.executeWithRetry("order-1", TestAggregate.class,
                aggregate -> aggregate.raise(event("order-1", 2, "OrderUpdated"))))
                .isInstanceOf(ConcurrencyException.class);

        verify(eventStore, times(2)).getEvents("order-1");
        verify(eventStore, times(2)).saveEvents(anyString(), anyList(), anyInt());
    }
}
