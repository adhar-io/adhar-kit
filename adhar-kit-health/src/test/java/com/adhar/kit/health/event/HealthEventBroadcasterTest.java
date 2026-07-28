package com.adhar.kit.health.event;

import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HealthEventBroadcaster}.
 */
class HealthEventBroadcasterTest {

    private static HealthTransition transition(String name) {
        return new HealthTransition(name, Health.Status.UP, Health.Status.DOWN, Instant.now());
    }

    @Test
    void subscribe_receivesTransitions_andReportsCount() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        List<HealthTransition> received = new ArrayList<>();

        assertThat(broadcaster.subscriberCount()).isZero();
        broadcaster.subscribe(received::add);
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);

        HealthTransition t = transition("db");
        broadcaster.onTransition(t);

        assertThat(received).containsExactly(t);
    }

    @Test
    void unsubscribe_stopsDelivery_andIsIdempotent() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        List<HealthTransition> received = new ArrayList<>();
        Runnable handle = broadcaster.subscribe(received::add);

        handle.run();
        assertThat(broadcaster.subscriberCount()).isZero();
        handle.run(); // idempotent, no exception
        broadcaster.onTransition(transition("db"));

        assertThat(received).isEmpty();
    }

    @Test
    void failingSubscriber_doesNotBreakOthers() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        List<HealthTransition> received = new ArrayList<>();
        Consumer<HealthTransition> bad = t -> {
            throw new IllegalStateException("boom");
        };

        broadcaster.subscribe(bad);
        broadcaster.subscribe(received::add);

        broadcaster.onTransition(transition("db"));

        assertThat(received).hasSize(1);
    }

    @Test
    void subscribe_null_throws() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        assertThatThrownBy(() -> broadcaster.subscribe(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
