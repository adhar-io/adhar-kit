package com.adhar.kit.batch.fault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SkipPresets}.
 */
class SkipPresetsTest {

    @Test
    @DisplayName("never skips nothing, whatever the failure")
    void never() throws Exception {
        assertThat(SkipPresets.never().shouldSkip(new IllegalStateException("any"), 0)).isFalse();
    }

    @Test
    @DisplayName("limit skips the declared types up to the limit, then fails the step")
    void limit() throws Exception {
        var policy = SkipPresets.limit(2, List.of(IllegalArgumentException.class));

        assertThat(policy.shouldSkip(new IllegalArgumentException("first"), 0)).isTrue();
        assertThat(policy.shouldSkip(new NumberFormatException("subclass"), 1)).isTrue();
        assertThat(policy.shouldSkip(new IllegalStateException("not declared"), 0)).isFalse();
        assertThatThrownBy(() -> policy.shouldSkip(new IllegalArgumentException("third"), 2))
                .isInstanceOf(SkipLimitExceededException.class);
    }

    @Test
    @DisplayName("a limit below one or no types are refused")
    void refusesAnEmptyPolicy() {
        List<Class<? extends Throwable>> none = List.of();

        assertThatThrownBy(() -> SkipPresets.limit(0, List.of(IllegalArgumentException.class)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 1, was 0");
        assertThatThrownBy(() -> SkipPresets.limit(1, none))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one skippable");
    }
}
