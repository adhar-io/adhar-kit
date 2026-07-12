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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private ApplicationEventPublisher eventPublisher;

    private PersistenceProperties properties;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new PersistenceProperties();
        properties.getOutbox().setBatchSize(50);
        publisher = new OutboxPublisher(outboxRepository, eventPublisher, properties);
    }

    private OutboxEvent pending(UUID id) {
        OutboxEvent e = OutboxEvent.create("Agg", "1", "Type", "payload");
        e.setId(id);
        return e;
    }

    @Test
    @DisplayName("does nothing when there are no pending events")
    void testNoPending() {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        publisher.pollAndPublish();

        verifyNoInteractions(eventPublisher);
        verify(outboxRepository, never()).markAsProcessed(any(), any());
        verify(outboxRepository, never()).markAsFailed(any());
    }

    @Test
    @DisplayName("publishes each pending event and marks it processed")
    void testPublishSuccess() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEvent e1 = pending(id1);
        OutboxEvent e2 = pending(id2);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(e1, e2));

        publisher.pollAndPublish();

        verify(eventPublisher).publishEvent(e1);
        verify(eventPublisher).publishEvent(e2);
        verify(outboxRepository).markAsProcessed(eq(id1), any(Instant.class));
        verify(outboxRepository).markAsProcessed(eq(id2), any(Instant.class));
        verify(outboxRepository, never()).markAsFailed(any());
    }

    @Test
    @DisplayName("marks event as failed when publishing throws")
    void testPublishFailure() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = pending(id);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("boom")).when(eventPublisher).publishEvent(event);

        publisher.pollAndPublish();

        verify(outboxRepository).markAsFailed(id);
        verify(outboxRepository, never()).markAsProcessed(any(), any());
    }

    @Test
    @DisplayName("uses configured batch size for the page request")
    void testBatchSizeUsed() {
        properties.getOutbox().setBatchSize(7);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        publisher.pollAndPublish();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxRepository).findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.OutboxStatus.PENDING), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(7, captor.getValue().getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(0, captor.getValue().getPageNumber());
    }
}
