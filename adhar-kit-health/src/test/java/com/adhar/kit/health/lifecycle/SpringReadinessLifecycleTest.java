package com.adhar.kit.health.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SpringReadinessLifecycle}.
 */
class SpringReadinessLifecycleTest {

    private final ApplicationContext context = mock(ApplicationContext.class);

    @Test
    void contextRefreshed_opensReadinessGate() {
        ReadinessStateManager manager = new ReadinessStateManager();
        SpringReadinessLifecycle lifecycle = new SpringReadinessLifecycle(manager);

        lifecycle.onApplicationEvent(new ContextRefreshedEvent(context));

        assertThat(manager.isReady()).isTrue();
    }

    @Test
    void contextClosed_closesReadinessGateForDraining() {
        ReadinessStateManager manager = new ReadinessStateManager(true);
        SpringReadinessLifecycle lifecycle = new SpringReadinessLifecycle(manager);

        lifecycle.onApplicationEvent(new ContextClosedEvent(context));

        assertThat(manager.isReady()).isFalse();
        assertThat(manager.getReason()).contains("closing");
    }

    @Test
    void unrelatedEvent_doesNotChangeState() {
        ReadinessStateManager manager = new ReadinessStateManager(true);
        SpringReadinessLifecycle lifecycle = new SpringReadinessLifecycle(manager);

        lifecycle.onApplicationEvent(new ApplicationEvent("payload") {
        });

        assertThat(manager.isReady()).isTrue();
    }
}
