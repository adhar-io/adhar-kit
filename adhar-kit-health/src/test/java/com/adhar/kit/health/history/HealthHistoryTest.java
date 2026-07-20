package com.adhar.kit.health.history;

import com.adhar.kit.health.event.HealthTransition;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HealthHistory}.
 */
class HealthHistoryTest {

    private static HealthTransition transition(String indicator, Instant timestamp) {
        return new HealthTransition(indicator, Health.Status.UP, Health.Status.DOWN, timestamp);
    }

    @Test
    void record_keepsTransitionsInOrder() {
        HealthHistory history = new HealthHistory(10);
        history.record(transition("a", Instant.now()));
        history.record(transition("b", Instant.now()));

        assertThat(history.getTransitions()).hasSize(2);
        assertThat(history.getTransitions().get(0).indicator()).isEqualTo("a");
        assertThat(history.getTransitions().get(1).indicator()).isEqualTo("b");
    }

    @Test
    void record_evictsOldestWhenFull() {
        HealthHistory history = new HealthHistory(3);
        for (int i = 0; i < 5; i++) {
            history.record(transition("i" + i, Instant.now()));
        }

        assertThat(history.size()).isEqualTo(3);
        assertThat(history.getTransitions())
            .extracting(HealthTransition::indicator)
            .containsExactly("i2", "i3", "i4");
    }

    @Test
    void record_ignoresNull() {
        HealthHistory history = new HealthHistory(3);
        history.record(null);

        assertThat(history.size()).isZero();
    }

    @Test
    void getTransitions_filtersByIndicator() {
        HealthHistory history = new HealthHistory(10);
        history.record(transition("db", Instant.now()));
        history.record(transition("cache", Instant.now()));
        history.record(transition("db", Instant.now()));

        assertThat(history.getTransitions("db")).hasSize(2);
        assertThat(history.getTransitions("cache")).hasSize(1);
        assertThat(history.getTransitions("missing")).isEmpty();
    }

    @Test
    void isFlapping_countsOnlyTransitionsWithinWindow() {
        HealthHistory history = new HealthHistory(10);
        Instant now = Instant.now();
        // two recent transitions, two outside the window
        history.record(transition("db", now.minus(Duration.ofMinutes(10))));
        history.record(transition("db", now.minus(Duration.ofMinutes(9))));
        history.record(transition("db", now.minusSeconds(5)));
        history.record(transition("db", now));

        assertThat(history.isFlapping("db", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(history.isFlapping("db", 3, Duration.ofMinutes(1))).isFalse();
        assertThat(history.isFlapping("db", 4, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    void isFlapping_ignoresOtherIndicators() {
        HealthHistory history = new HealthHistory(10);
        history.record(transition("other", Instant.now()));
        history.record(transition("other", Instant.now()));

        assertThat(history.isFlapping("db", 1, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void setCapacity_trimsOldestEntries() {
        HealthHistory history = new HealthHistory(5);
        for (int i = 0; i < 5; i++) {
            history.record(transition("i" + i, Instant.now()));
        }

        history.setCapacity(2);

        assertThat(history.getCapacity()).isEqualTo(2);
        assertThat(history.getTransitions())
            .extracting(HealthTransition::indicator)
            .containsExactly("i3", "i4");
    }

    @Test
    void invalidCapacity_throws() {
        assertThatThrownBy(() -> new HealthHistory(0)).isInstanceOf(IllegalArgumentException.class);
        HealthHistory history = new HealthHistory();
        assertThatThrownBy(() -> history.setCapacity(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultConstructor_usesDefaultCapacity() {
        assertThat(new HealthHistory().getCapacity()).isEqualTo(HealthHistory.DEFAULT_CAPACITY);
    }

    @Test
    void clear_removesEverything() {
        HealthHistory history = new HealthHistory(5);
        history.record(transition("db", Instant.now()));

        history.clear();

        assertThat(history.size()).isZero();
        assertThat(history.getTransitions()).isEmpty();
    }
}
