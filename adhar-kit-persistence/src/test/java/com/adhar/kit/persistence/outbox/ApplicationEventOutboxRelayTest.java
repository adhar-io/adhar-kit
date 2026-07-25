package com.adhar.kit.persistence.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationEventOutboxRelay Tests")
class ApplicationEventOutboxRelayTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("relay() republishes the outbox event through ApplicationEventPublisher")
    void relayPublishesEvent() {
        ApplicationEventOutboxRelay relay = new ApplicationEventOutboxRelay(eventPublisher);
        OutboxEvent event = OutboxEvent.create("Order", "1", "OrderCreated", "{}");

        relay.relay(event);

        verify(eventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("rejects a null ApplicationEventPublisher")
    void rejectsNullPublisher() {
        assertThrows(NullPointerException.class, () -> new ApplicationEventOutboxRelay(null));
    }
}
