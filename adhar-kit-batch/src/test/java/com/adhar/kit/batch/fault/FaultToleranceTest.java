package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.config.BatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.skip.NeverSkipItemSkipPolicy;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link FaultTolerance}: how a declaration resolves against the configured
 * defaults. {@link FaultToleranceStepTest} runs the declarations in a real step.
 */
class FaultToleranceTest {

    private final BatchProperties.Retry defaults = new BatchProperties.Retry();

    private static long delayCount(RetryPolicy policy) {
        return RetryPresetsTest.delays(policy).size();
    }

    // ------------------------------------------------------------------
    // none
    // ------------------------------------------------------------------

    @Test
    @DisplayName("none retries nothing and skips nothing")
    void none() {
        FaultTolerance none = FaultTolerance.none();

        assertThat(none.isFaultTolerant()).isFalse();
        assertThat(none.resolveRetryPolicy(defaults)).isNull();
        assertThat(none.resolveSkipPolicy()).isInstanceOf(NeverSkipItemSkipPolicy.class);
        assertThat(none.getSkippable()).isEmpty();
        assertThat(none.getSkipLimit()).isZero();
    }

    @Test
    @DisplayName("none leaves the step builder alone")
    @SuppressWarnings("unchecked")
    void noneDoesNotTouchTheBuilder() {
        ChunkOrientedStepBuilder<Object, Object> builder = mock(ChunkOrientedStepBuilder.class);

        FaultTolerance.none().applyTo(builder, defaults);

        verifyNoInteractions(builder);
    }

    // ------------------------------------------------------------------
    // Retry
    // ------------------------------------------------------------------

    @Test
    @DisplayName("transientInfrastructure retries database and network failures with the configured back-off")
    void transientInfrastructure() {
        defaults.setMaxAttempts(4);
        defaults.setInitialBackoff(Duration.ofMillis(10));
        defaults.setMaxBackoff(Duration.ofMillis(25));

        RetryPolicy policy = FaultTolerance.transientInfrastructure().resolveRetryPolicy(defaults);

        assertThat(FaultTolerance.transientInfrastructure().isFaultTolerant()).isTrue();
        assertThat(policy.shouldRetry(new QueryTimeoutException("timeout"))).isTrue();
        assertThat(policy.shouldRetry(new ResourceAccessException("http"))).isFalse();
        assertThat(RetryPresetsTest.delays(policy)).containsExactly(10L, 20L, 25L);
    }

    @Test
    @DisplayName("transientHttp retries HTTP failures, not database ones")
    void transientHttp() {
        RetryPolicy policy = FaultTolerance.transientHttp().resolveRetryPolicy(defaults);

        assertThat(policy.shouldRetry(new ResourceAccessException("http"))).isTrue();
        assertThat(policy.shouldRetry(new QueryTimeoutException("timeout"))).isFalse();
    }

    @Test
    @DisplayName("retryOn matches a subclass or a cause, and never a NonRetryableBatchException")
    void retryOn() {
        RetryPolicy policy = FaultTolerance.retryOn(IOException.class).resolveRetryPolicy(defaults);

        assertThat(policy.shouldRetry(new SocketTimeoutException("subclass"))).isTrue();
        assertThat(policy.shouldRetry(new RuntimeException("wrapped", new IOException("cause")))).isTrue();
        assertThat(policy.shouldRetry(new IllegalStateException("other"))).isFalse();
        assertThat(policy.shouldRetry(new NonRetryableBatchException("invalid", new IOException("cause")))).isFalse();
    }

    @Test
    @DisplayName("alsoRetryOn adds to what is retried, and starts from nothing on none")
    void alsoRetryOn() {
        RetryPolicy extended = FaultTolerance.transientInfrastructure()
                .alsoRetryOn(IllegalStateException.class).resolveRetryPolicy(defaults);
        RetryPolicy fromNone = FaultTolerance.none().alsoRetryOn(IllegalStateException.class)
                .resolveRetryPolicy(defaults);

        assertThat(extended.shouldRetry(new QueryTimeoutException("preset"))).isTrue();
        assertThat(extended.shouldRetry(new IllegalStateException("added"))).isTrue();
        assertThat(extended.shouldRetry(new IllegalArgumentException("other"))).isFalse();
        assertThat(fromNone.shouldRetry(new IllegalStateException("added"))).isTrue();
        assertThat(fromNone.shouldRetry(new QueryTimeoutException("not declared"))).isFalse();
    }

    @Test
    @DisplayName("maxAttempts and backoff override the configured defaults")
    void overrides() {
        RetryPolicy policy = FaultTolerance.transientInfrastructure()
                .maxAttempts(3)
                .backoff(Duration.ofMillis(5), Duration.ofMillis(8))
                .resolveRetryPolicy(defaults);

        assertThat(RetryPresetsTest.delays(policy)).containsExactly(5L, 8L);
    }

    @Test
    @DisplayName("what is not overridden keeps following the configured defaults")
    void partialOverride() {
        defaults.setInitialBackoff(Duration.ofMillis(10));
        defaults.setMaxBackoff(Duration.ofMillis(10));

        FaultTolerance declared = FaultTolerance.transientHttp().maxAttempts(2);

        assertThat(RetryPresetsTest.delays(declared.resolveRetryPolicy(defaults))).containsExactly(10L);
    }

    @Test
    @DisplayName("the application's own retry policy replaces the presets and the defaults")
    void ownRetryPolicy() {
        RetryPolicy own = RetryPolicy.withDefaults();

        FaultTolerance declared = FaultTolerance.transientInfrastructure().maxAttempts(9).retryPolicy(own);

        assertThat(declared.isFaultTolerant()).isTrue();
        assertThat(declared.resolveRetryPolicy(defaults)).isSameAs(own);
        assertThat(FaultTolerance.none().retryPolicy(own).isFaultTolerant()).isTrue();
    }

    @Test
    @DisplayName("a declaration is immutable: every method returns a new value")
    void immutable() {
        FaultTolerance base = FaultTolerance.retryOn(IOException.class);

        FaultTolerance more = base.maxAttempts(5)
                .backoff(Duration.ofMillis(1), Duration.ofMillis(1))
                .alsoRetryOn(IllegalStateException.class)
                .skip(IllegalArgumentException.class, 3);

        assertThat(more).isNotSameAs(base);
        RetryPolicy basePolicy = base.resolveRetryPolicy(defaults);
        assertThat(basePolicy.shouldRetry(new IllegalStateException("only in more"))).isFalse();
        assertThat(delayCount(basePolicy)).isEqualTo(defaults.getMaxAttempts() - 1L);
        assertThat(base.getSkippable()).isEmpty();
        assertThat(FaultTolerance.none().isFaultTolerant()).isFalse();
    }

    @Test
    @DisplayName("invalid retry settings are refused when declared")
    void invalidRetry() {
        FaultTolerance declared = FaultTolerance.transientInfrastructure();
        Duration second = Duration.ofSeconds(1);

        assertThatThrownBy(() -> declared.maxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 1, was 0");
        assertThatThrownBy(() -> declared.backoff(null, second)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> declared.backoff(second, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> declared.backoff(Duration.ZERO, second)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> declared.backoff(second.negated(), second))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> declared.backoff(second, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max not below");
        assertThatThrownBy(() -> declared.retryPolicy(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FaultTolerance.retryOn()).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Skip
    // ------------------------------------------------------------------

    @Test
    @DisplayName("skip declares a type and a limit the whole step shares")
    void skip() throws Exception {
        FaultTolerance declared = FaultTolerance.none().skip(IllegalArgumentException.class, 2);

        assertThat(declared.isFaultTolerant()).isTrue();
        assertThat(declared.resolveRetryPolicy(defaults)).isNull();
        assertThat(declared.getSkipLimit()).isEqualTo(2);
        assertThat(declared.getSkippable()).containsExactly(IllegalArgumentException.class);

        SkipPolicy policy = declared.resolveSkipPolicy();

        assertThat(policy.shouldSkip(new IllegalArgumentException("first"), 0)).isTrue();
        assertThat(policy.shouldSkip(new NumberFormatException("a subclass"), 1)).isTrue();
        assertThat(policy.shouldSkip(new IllegalStateException("not declared"), 0)).isFalse();
        assertThatThrownBy(() -> policy.shouldSkip(new IllegalArgumentException("third"), 2))
                .isInstanceOf(SkipLimitExceededException.class);
    }

    @Test
    @DisplayName("skip accumulates types under one limit and refuses a second limit")
    void skipAccumulates() {
        FaultTolerance declared = FaultTolerance.none()
                .skip(IllegalArgumentException.class, 5)
                .skip(5, IllegalStateException.class, IOException.class);

        assertThat(declared.getSkippable())
                .containsExactly(IllegalArgumentException.class, IllegalStateException.class, IOException.class);
        assertThat(declared.getSkipLimit()).isEqualTo(5);
        assertThatThrownBy(() -> declared.skip(IOException.class, 6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one skip limit")
                .hasMessageContaining("5 already, not 6");
    }

    @Test
    @DisplayName("the skippable types cannot be changed from outside")
    void skippableIsUnmodifiable() {
        var skippable = FaultTolerance.none().skip(IOException.class, 1).getSkippable();

        assertThatThrownBy(() -> skippable.add(IllegalStateException.class))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("invalid skip settings are refused when declared")
    void invalidSkip() {
        FaultTolerance none = FaultTolerance.none();

        assertThatThrownBy(() -> none.skip(IOException.class, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 1, was 0");
        assertThatThrownBy(() -> none.skip(1L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one skippable");
        assertThatThrownBy(() -> none.skipPolicy(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the application's own skip policy replaces the declared types")
    void ownSkipPolicy() {
        SkipPolicy own = (failure, count) -> true;

        FaultTolerance declared = FaultTolerance.none().skip(IOException.class, 1).skipPolicy(own);

        assertThat(declared.resolveSkipPolicy()).isSameAs(own);
        assertThat(FaultTolerance.none().skipPolicy(own).isFaultTolerant()).isTrue();
    }

    @Test
    @DisplayName("a NonRetryableBatchException can still be skipped when declared skippable")
    void nonRetryableCanBeSkipped() throws Exception {
        FaultTolerance declared = FaultTolerance.transientInfrastructure().skip(NonRetryableBatchException.class, 1);

        assertThat(declared.resolveRetryPolicy(defaults).shouldRetry(new NonRetryableBatchException("invalid")))
                .isFalse();
        assertThat(declared.resolveSkipPolicy().shouldSkip(new NonRetryableBatchException("invalid"), 0)).isTrue();
    }

    // ------------------------------------------------------------------
    // Applying
    // ------------------------------------------------------------------

    @Test
    @DisplayName("applyTo hands the builder the resolved retry and skip policies")
    @SuppressWarnings("unchecked")
    void applies() {
        ChunkOrientedStepBuilder<Object, Object> builder = mock(ChunkOrientedStepBuilder.class);
        RetryPolicy retry = RetryPolicy.withDefaults();
        SkipPolicy skip = (failure, count) -> false;

        FaultTolerance.none().retryPolicy(retry).skipPolicy(skip).applyTo(builder, defaults);

        verify(builder).faultTolerant();
        verify(builder).retryPolicy(retry);
        verify(builder).skipPolicy(skip);
    }

    @Test
    @DisplayName("applyTo sets no retry policy when only skipping is declared")
    @SuppressWarnings("unchecked")
    void appliesSkipOnly() {
        ChunkOrientedStepBuilder<Object, Object> builder = mock(ChunkOrientedStepBuilder.class);
        SkipPolicy skip = (failure, count) -> false;

        FaultTolerance.none().skipPolicy(skip).applyTo(builder, defaults);

        verify(builder).faultTolerant();
        verify(builder).skipPolicy(skip);
        verify(builder, org.mockito.Mockito.never()).retryPolicy(org.mockito.ArgumentMatchers.any());
    }
}
