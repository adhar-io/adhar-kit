package com.adhar.kit.batch.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetryableStepBuilder}.
 *
 * <p>Uses Mockito to stand in for the Spring Batch step builders so no real
 * job repository or database is required.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetryableStepBuilderTest {

    @Mock
    private SimpleStepBuilder<String, String> simpleStepBuilder;

    @Mock
    private FaultTolerantStepBuilder<String, String> faultTolerantStepBuilder;

    @BeforeEach
    void setUp() {
        when(simpleStepBuilder.faultTolerant()).thenReturn(faultTolerantStepBuilder);
    }

    @Test
    @DisplayName("build applies default retry limit and no skip when nothing configured")
    void buildAppliesDefaults() {
        var result = new RetryableStepBuilder<>(simpleStepBuilder).build();

        assertThat(result).isSameAs(faultTolerantStepBuilder);
        verify(faultTolerantStepBuilder).retryLimit(3);
        verify(faultTolerantStepBuilder, never()).skipLimit(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("withRetryLimit and retryOn configure retry policy")
    void configuresRetry() {
        new RetryableStepBuilder<String, String>(simpleStepBuilder)
                .withRetryLimit(5)
                .retryOn(IllegalStateException.class, RuntimeException.class)
                .build();

        verify(faultTolerantStepBuilder).retryLimit(5);
        verify(faultTolerantStepBuilder).retry(IllegalStateException.class);
        verify(faultTolerantStepBuilder).retry(RuntimeException.class);
    }

    @Test
    @DisplayName("withSkipLimit and skipOn configure skip policy")
    void configuresSkip() {
        new RetryableStepBuilder<String, String>(simpleStepBuilder)
                .withSkipLimit(10)
                .skipOn(IllegalArgumentException.class)
                .build();

        verify(faultTolerantStepBuilder).skipLimit(10);
        verify(faultTolerantStepBuilder).skip(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("skip policy applied when only skippable exceptions set (skipLimit 0)")
    void skipAppliedWithExceptionsButZeroLimit() {
        new RetryableStepBuilder<String, String>(simpleStepBuilder)
                .skipOn(IllegalArgumentException.class)
                .build();

        verify(faultTolerantStepBuilder).skipLimit(0);
        verify(faultTolerantStepBuilder).skip(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("defaults constructor applies the supplied retry limit when enabled")
    void defaultsConstructorAppliesConfiguredLimit() {
        new RetryableStepBuilder<>(simpleStepBuilder, 8, true).build();

        verify(faultTolerantStepBuilder).retryLimit(8);
    }

    @Test
    @DisplayName("defaults constructor skips retry entirely when retry disabled")
    void defaultsConstructorDisablesRetry() {
        new RetryableStepBuilder<String, String>(simpleStepBuilder, 5, false)
                .retryOn(IllegalStateException.class)
                .build();

        verify(faultTolerantStepBuilder, never()).retryLimit(org.mockito.ArgumentMatchers.anyInt());
        verify(faultTolerantStepBuilder, never()).retry(IllegalStateException.class);
    }

    @Test
    @DisplayName("explicit withRetryLimit re-enables retry disabled by defaults")
    void withRetryLimitOverridesDisabled() {
        new RetryableStepBuilder<String, String>(simpleStepBuilder, 5, false)
                .withRetryLimit(4)
                .build();

        verify(faultTolerantStepBuilder).retryLimit(4);
    }

    @Test
    @DisplayName("fluent setters return the same builder instance")
    void fluentReturnsSameInstance() {
        var builder = new RetryableStepBuilder<String, String>(simpleStepBuilder);

        assertThat(builder.withRetryLimit(2)).isSameAs(builder);
        assertThat(builder.withSkipLimit(1)).isSameAs(builder);
        assertThat(builder.retryOn(RuntimeException.class)).isSameAs(builder);
        assertThat(builder.skipOn(RuntimeException.class)).isSameAs(builder);
    }
}
