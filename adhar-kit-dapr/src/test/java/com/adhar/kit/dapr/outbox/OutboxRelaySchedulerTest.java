package com.adhar.kit.dapr.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutboxRelayScheduler}.
 */
class OutboxRelaySchedulerTest {

    @Test
    void relayDelegatesToPublisher() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(publisher.relay()).thenReturn(new OutboxRelayResult(2, 1, 0));
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(publisher);

        scheduler.relay();

        verify(publisher).relay();
    }

    @Test
    void relayWithNothingToDoIsQuiet() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(publisher.relay()).thenReturn(new OutboxRelayResult(0, 0, 0));
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(publisher);

        assertThatCode(scheduler::relay).doesNotThrowAnyException();
        verify(publisher).relay();
    }

    @Test
    void relaySwallowsExceptions() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(publisher.relay()).thenThrow(new RuntimeException("boom"));
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(publisher);

        assertThatCode(scheduler::relay).doesNotThrowAnyException();
    }

    @Test
    void nullPublisherRejected() {
        assertThatThrownBy(() -> new OutboxRelayScheduler(null))
            .isInstanceOf(NullPointerException.class);
    }
}
