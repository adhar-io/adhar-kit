package com.adhar.kit.metrics.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TagCardinalityLimiter}.
 */
class TagCardinalityLimiterTest {

    @Test
    void admitsValuesBelowTheLimit() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter(3);

        assertThat(limiter.limit("uri", "/a")).isEqualTo("/a");
        assertThat(limiter.limit("uri", "/b")).isEqualTo("/b");
        assertThat(limiter.limit("uri", "/c")).isEqualTo("/c");
        assertThat(limiter.cardinality("uri")).isEqualTo(3);
    }

    @Test
    void replacesNewValuesWithOtherOnceLimitReached() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter(2);
        limiter.limit("uri", "/a");
        limiter.limit("uri", "/b");

        assertThat(limiter.limit("uri", "/c")).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);
        assertThat(limiter.limit("uri", "/d")).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);
        assertThat(limiter.cardinality("uri")).isEqualTo(2);
    }

    @Test
    void previouslyAdmittedValuesStillPassAfterLimitReached() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter(2);
        limiter.limit("uri", "/a");
        limiter.limit("uri", "/b");
        limiter.limit("uri", "/c"); // overflow

        assertThat(limiter.limit("uri", "/a")).isEqualTo("/a");
        assertThat(limiter.limit("uri", "/b")).isEqualTo("/b");
    }

    @Test
    void limitsAreTrackedPerTagKey() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter(1);
        assertThat(limiter.limit("uri", "/a")).isEqualTo("/a");
        assertThat(limiter.limit("uri", "/b")).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);

        // a different tag key has its own budget
        assertThat(limiter.limit("method", "GET")).isEqualTo("GET");
        assertThat(limiter.cardinality("method")).isEqualTo(1);
    }

    @Test
    void nullAndEmptyValuesMapToOther() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter();

        assertThat(limiter.limit("uri", null)).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);
        assertThat(limiter.limit("uri", "")).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);
        assertThat(limiter.cardinality("uri")).isEqualTo(0);
    }

    @Test
    void defaultMaxCardinalityIs100() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter();
        assertThat(limiter.getMaxCardinality()).isEqualTo(100);

        for (int i = 0; i < 100; i++) {
            assertThat(limiter.limit("uri", "/path/" + i)).isEqualTo("/path/" + i);
        }
        assertThat(limiter.limit("uri", "/one-too-many")).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);
    }

    @Test
    void resetClearsAdmittedValues() {
        TagCardinalityLimiter limiter = new TagCardinalityLimiter(1);
        limiter.limit("uri", "/a");
        assertThat(limiter.limit("uri", "/b")).isEqualTo(TagCardinalityLimiter.OVERFLOW_VALUE);

        limiter.reset();
        assertThat(limiter.limit("uri", "/b")).isEqualTo("/b");
    }

    @Test
    void rejectsNonPositiveMaxCardinality() {
        assertThatThrownBy(() -> new TagCardinalityLimiter(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TagCardinalityLimiter(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
