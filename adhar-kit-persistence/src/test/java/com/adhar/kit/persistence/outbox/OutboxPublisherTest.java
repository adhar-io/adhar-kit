package com.adhar.kit.persistence.outbox;

import com.adhar.kit.persistence.config.PersistenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutboxPublisher Tests")
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private OutboxRelay relay;

    private PersistenceProperties properties;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new PersistenceProperties();
        properties.getOutbox().setBatchSize(50);
        publisher = new OutboxPublisher(outboxRepository, relay, properties);
    }

    private OutboxEvent pending(UUID id) {
        OutboxEvent e = OutboxEvent.create("Agg", "1", "Type", "payload");
        e.setId(id);
        return e;
    }

    @Test
    @DisplayName("does nothing when there are no due events")
    void testNoPending() {
        when(outboxRepository.findBatchForProcessing(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        publisher.pollAndPublish();

        verifyNoInteractions(relay);
        verify(outboxRepository, never()).markAsProcessed(any(), any());
        verify(outboxRepository, never()).scheduleRetry(any(), any(), any());
        verify(outboxRepository, never()).markAsDead(any(), any());
    }

    @Test
    @DisplayName("relays each due event and marks it processed")
    void testPublishSuccess() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEvent e1 = pending(id1);
        OutboxEvent e2 = pending(id2);
        when(outboxRepository.findBatchForProcessing(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(e1, e2));

        publisher.pollAndPublish();

        verify(relay).relay(e1);
        verify(relay).relay(e2);
        verify(outboxRepository).markAsProcessed(eq(id1), any(Instant.class));
        verify(outboxRepository).markAsProcessed(eq(id2), any(Instant.class));
        verify(outboxRepository, never()).scheduleRetry(any(), any(), any());
        verify(outboxRepository, never()).markAsDead(any(), any());
    }

    @Test
    @DisplayName("schedules a retry with backoff when relaying fails and attempts remain")
    void testPublishFailureSchedulesRetry() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = pending(id);
        properties.getOutbox().setMaxAttempts(5);
        properties.getOutbox().setInitialBackoffMs(1000);
        properties.getOutbox().setBackoffMultiplier(2.0);
        when(outboxRepository.findBatchForProcessing(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("boom")).when(relay).relay(event);

        publisher.pollAndPublish();

        ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).scheduleRetry(eq(id), nextAttemptCaptor.capture(), eq("boom"));
        verify(outboxRepository, never()).markAsProcessed(any(), any());
        verify(outboxRepository, never()).markAsDead(any(), any());
        // first failure (attempts becomes 1) -> backoff ~= initialBackoffMs * multiplier^0 = 1000ms
        Instant scheduledFor = nextAttemptCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(scheduledFor.isAfter(Instant.now().minusMillis(1)));
    }

    @Test
    @DisplayName("marks event DEAD once max attempts is reached")
    void testPublishFailureExhaustsRetriesAndMarksDead() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = pending(id);
        event.setAttempts(4); // next failure is the 5th attempt
        properties.getOutbox().setMaxAttempts(5);
        when(outboxRepository.findBatchForProcessing(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("fatal")).when(relay).relay(event);

        publisher.pollAndPublish();

        verify(outboxRepository).markAsDead(id, "fatal");
        verify(outboxRepository, never()).scheduleRetry(any(), any(), any());
        verify(outboxRepository, never()).markAsProcessed(any(), any());
    }

    @Test
    @DisplayName("falls back to the unlocked batch fetch when SKIP LOCKED is unsupported")
    void testFallsBackWhenSkipLockedUnsupported() {
        when(outboxRepository.findBatchForProcessing(any(Instant.class), any(Pageable.class)))
                .thenThrow(new RuntimeException("SKIP LOCKED not supported"));
        when(outboxRepository.findBatchForProcessingNoLock(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        publisher.pollAndPublish();
        // second poll: should go straight to the no-lock fallback without retrying the locked query
        publisher.pollAndPublish();

        verify(outboxRepository, times(1)).findBatchForProcessing(any(Instant.class), any(Pageable.class));
        verify(outboxRepository, times(2)).findBatchForProcessingNoLock(any(Instant.class), any(Pageable.class));
    }

    @Test
    @DisplayName("uses configured batch size for the page request")
    void testBatchSizeUsed() {
        properties.getOutbox().setBatchSize(7);
        when(outboxRepository.findBatchForProcessing(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        publisher.pollAndPublish();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxRepository).findBatchForProcessing(any(Instant.class), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(7, captor.getValue().getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(0, captor.getValue().getPageNumber());
    }
}
