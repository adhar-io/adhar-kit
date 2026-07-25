package com.adhar.kit.persistence.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DomainEventOutboxBridge Tests")
class DomainEventOutboxBridgeTest {

    @Mock
    private OutboxRepository outboxRepository;

    private record TestDomainEvent(String aggregateType, String aggregateId, String eventType,
                                    String payload) implements OutboxedEvent {
    }

    @Test
    @DisplayName("rejects a null OutboxRepository")
    void rejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new DomainEventOutboxBridge(null));
    }

    @Test
    @DisplayName("onDomainEvent() persists the domain event as a new pending outbox row")
    void bridgesDomainEventToOutbox() {
        DomainEventOutboxBridge bridge = new DomainEventOutboxBridge(outboxRepository);
        TestDomainEvent event = new TestDomainEvent("Order", "42", "OrderCreated", "{\"total\":10}");

        bridge.onDomainEvent(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("Order", saved.getAggregateType());
        assertEquals("42", saved.getAggregateId());
        assertEquals("OrderCreated", saved.getEventType());
        assertEquals("{\"total\":10}", saved.getPayload());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getNextAttemptAt());
    }
}
