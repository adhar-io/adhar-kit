package com.adhar.kit.health.event;

import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringHealthEventPublisher} and {@link HealthTransitionApplicationEvent}.
 */
class SpringHealthEventPublisherTest {

    @Test
    void onTransition_publishesApplicationEventCarryingTransition() {
        ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
        SpringHealthEventPublisher publisher = new SpringHealthEventPublisher(delegate);
        HealthTransition transition =
                new HealthTransition("db", Health.Status.UP, Health.Status.DOWN, Instant.now());

        publisher.onTransition(transition);

        ArgumentCaptor<HealthTransitionApplicationEvent> captor =
                ArgumentCaptor.forClass(HealthTransitionApplicationEvent.class);
        verify(delegate).publishEvent(captor.capture());
        HealthTransitionApplicationEvent event = captor.getValue();
        assertThat(event.getTransition()).isSameAs(transition);
        assertThat(event.getSource()).isSameAs(publisher);
    }
}
