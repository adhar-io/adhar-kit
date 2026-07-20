package com.adhar.kit.health.registry;

import com.adhar.kit.health.event.HealthTransition;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HealthRegistry} transition events, history and flapping detection.
 */
class HealthRegistryTransitionTest {

    private HealthRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HealthRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    private static final class MutableIndicator implements AdharHealthIndicator {
        private final String name;
        private final AtomicReference<Health.Status> status;

        MutableIndicator(String name, Health.Status initial) {
            this.name = name;
            this.status = new AtomicReference<>(initial);
        }

        void setStatus(Health.Status newStatus) {
            status.set(newStatus);
        }

        @Override
        public Health check() {
            return Health.builder().status(status.get()).component(name).build();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @Test
    void firstObservation_recordsInitialTransition() {
        List<HealthTransition> events = new CopyOnWriteArrayList<>();
        registry.addTransitionListener(events::add);
        registry.register(new MutableIndicator("db", Health.Status.UP));

        registry.checkHealth();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).indicator()).isEqualTo("db");
        assertThat(events.get(0).isInitial()).isTrue();
        assertThat(events.get(0).from()).isNull();
        assertThat(events.get(0).to()).isEqualTo(Health.Status.UP);
        assertThat(events.get(0).timestamp()).isNotNull();
    }

    @Test
    void statusChange_notifiesListenersAndRecordsHistory() {
        List<HealthTransition> events = new CopyOnWriteArrayList<>();
        registry.addTransitionListener(events::add);
        MutableIndicator indicator = new MutableIndicator("db", Health.Status.UP);
        registry.register(indicator);

        registry.checkHealth();
        indicator.setStatus(Health.Status.DOWN);
        registry.checkHealth();

        assertThat(events).hasSize(2);
        assertThat(events.get(1).from()).isEqualTo(Health.Status.UP);
        assertThat(events.get(1).to()).isEqualTo(Health.Status.DOWN);
        assertThat(events.get(1).isInitial()).isFalse();

        List<HealthTransition> history = registry.getHistory().getTransitions("db");
        assertThat(history).hasSize(2);
    }

    @Test
    void unchangedStatus_doesNotNotify() {
        List<HealthTransition> events = new CopyOnWriteArrayList<>();
        registry.addTransitionListener(events::add);
        registry.register(new MutableIndicator("db", Health.Status.UP));

        registry.checkHealth();
        registry.checkHealth();
        registry.checkHealth();

        assertThat(events).hasSize(1);
    }

    @Test
    void listenerException_isSwallowedAndOthersStillNotified() {
        List<HealthTransition> events = new CopyOnWriteArrayList<>();
        registry.addTransitionListener(t -> {
            throw new IllegalStateException("boom");
        });
        registry.addTransitionListener(events::add);
        registry.register(new MutableIndicator("db", Health.Status.UP));

        registry.checkHealth();

        assertThat(events).hasSize(1);
    }

    @Test
    void removeTransitionListener_stopsNotifications() {
        List<HealthTransition> events = new CopyOnWriteArrayList<>();
        var listener = (com.adhar.kit.health.event.HealthTransitionListener) events::add;
        registry.addTransitionListener(listener);

        assertThat(registry.removeTransitionListener(listener)).isTrue();
        assertThat(registry.removeTransitionListener(listener)).isFalse();

        registry.register(new MutableIndicator("db", Health.Status.UP));
        registry.checkHealth();

        assertThat(events).isEmpty();
    }

    @Test
    void flappingIndicator_isDetected() {
        registry.configureFlapping(3, 60_000);
        MutableIndicator flappy = new MutableIndicator("flappy", Health.Status.UP);
        MutableIndicator stable = new MutableIndicator("stable", Health.Status.UP);
        registry.register(flappy);
        registry.register(stable);

        for (int i = 0; i < 4; i++) {
            registry.checkHealth();
            flappy.setStatus(i % 2 == 0 ? Health.Status.DOWN : Health.Status.UP);
        }

        assertThat(registry.isFlapping("flappy")).isTrue();
        assertThat(registry.isFlapping("stable")).isFalse();
    }

    @Test
    void unregister_clearsLastStatusSoReregistrationIsInitial() {
        List<HealthTransition> events = new CopyOnWriteArrayList<>();
        registry.addTransitionListener(events::add);
        MutableIndicator indicator = new MutableIndicator("db", Health.Status.UP);
        registry.register(indicator);
        registry.checkHealth();

        registry.unregister("db");
        registry.register(indicator);
        registry.checkHealth();

        assertThat(events).hasSize(2);
        assertThat(events.get(1).isInitial()).isTrue();
    }
}
