package com.adhar.kit.batch.retry;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetryableStepBuilderFactory}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetryableStepBuilderFactoryTest {

    @Mock
    private SimpleStepBuilder<String, String> simpleStepBuilder;
    @Mock
    private FaultTolerantStepBuilder<String, String> faultTolerantStepBuilder;

    @Test
    @DisplayName("exposes the configured defaults")
    void exposesDefaults() {
        var factory = new RetryableStepBuilderFactory(7, true);

        assertThat(factory.getDefaultRetryLimit()).isEqualTo(7);
        assertThat(factory.isRetryEnabled()).isTrue();
    }

    @Test
    @DisplayName("created builder applies the configured retry limit by default")
    void createdBuilderAppliesRetryLimit() {
        when(simpleStepBuilder.faultTolerant()).thenReturn(faultTolerantStepBuilder);
        var factory = new RetryableStepBuilderFactory(5, true);

        factory.create(simpleStepBuilder).build();

        verify(faultTolerantStepBuilder).retryLimit(5);
    }

    @Test
    @DisplayName("created builder applies no retry when retry is disabled")
    void createdBuilderNoRetryWhenDisabled() {
        when(simpleStepBuilder.faultTolerant()).thenReturn(faultTolerantStepBuilder);
        var factory = new RetryableStepBuilderFactory(3, false);

        factory.create(simpleStepBuilder).build();

        verify(faultTolerantStepBuilder, never()).retryLimit(anyInt());
    }

    @Test
    @DisplayName("explicit withRetryLimit overrides the disabled default")
    void explicitOverrideReenablesRetry() {
        when(simpleStepBuilder.faultTolerant()).thenReturn(faultTolerantStepBuilder);
        var factory = new RetryableStepBuilderFactory(3, false);

        factory.create(simpleStepBuilder).withRetryLimit(9).build();

        verify(faultTolerantStepBuilder).retryLimit(9);
    }
}
