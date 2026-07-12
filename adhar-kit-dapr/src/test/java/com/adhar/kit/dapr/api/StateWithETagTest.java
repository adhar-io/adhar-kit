package com.adhar.kit.dapr.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StateWithETag}.
 */
class StateWithETagTest {

    @Test
    void existsReturnsTrueWhenValuePresent() {
        StateWithETag<String> state = new StateWithETag<>("hello", "etag-1");

        assertThat(state.exists()).isTrue();
        assertThat(state.getValue()).isEqualTo("hello");
        assertThat(state.getEtag()).isEqualTo("etag-1");
    }

    @Test
    void existsReturnsFalseWhenValueNull() {
        StateWithETag<String> state = new StateWithETag<>(null, null);

        assertThat(state.exists()).isFalse();
        assertThat(state.getValue()).isNull();
    }

    @Test
    void noArgsConstructorAndSettersWork() {
        StateWithETag<Integer> state = new StateWithETag<>();
        state.setValue(7);
        state.setEtag("e");

        assertThat(state.exists()).isTrue();
        assertThat(state.getValue()).isEqualTo(7);
        assertThat(state.getEtag()).isEqualTo("e");
    }

    @Test
    void equalsHashCodeAndToString() {
        StateWithETag<String> a = new StateWithETag<>("v", "e");
        StateWithETag<String> b = new StateWithETag<>("v", "e");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("v");
    }
}
