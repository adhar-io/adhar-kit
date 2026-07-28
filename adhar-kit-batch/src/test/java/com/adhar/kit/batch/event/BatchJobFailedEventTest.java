package com.adhar.kit.batch.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BatchJobFailedEvent}.
 */
class BatchJobFailedEventTest {

    @Test
    @DisplayName("exposes all failure details")
    void exposesDetails() {
        var cause = new IllegalStateException("boom");
        var event = new BatchJobFailedEvent(this, "importJob", 42L, BatchStatus.FAILED,
                "FAILED", "step failed", 1234L, List.of(cause));

        assertThat(event.getSource()).isSameAs(this);
        assertThat(event.getJobName()).isEqualTo("importJob");
        assertThat(event.getJobExecutionId()).isEqualTo(42L);
        assertThat(event.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(event.getExitCode()).isEqualTo("FAILED");
        assertThat(event.getExitDescription()).isEqualTo("step failed");
        assertThat(event.getDurationMs()).isEqualTo(1234L);
        assertThat(event.getFailureExceptions()).containsExactly(cause);
        assertThat(event.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("null failure exceptions become an empty immutable list")
    void nullExceptionsBecomeEmpty() {
        var event = new BatchJobFailedEvent(this, "job", 1L, BatchStatus.FAILED,
                "FAILED", null, 0L, null);

        assertThat(event.getFailureExceptions()).isEmpty();
        assertThat(event.getExitDescription()).isNull();
        assertThatThrownBy(() -> event.getFailureExceptions().add(new RuntimeException()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("failure exception list is a defensive copy")
    void failureExceptionsAreCopied() {
        var mutable = new java.util.ArrayList<Throwable>();
        mutable.add(new RuntimeException("first"));
        var event = new BatchJobFailedEvent(this, "job", 1L, BatchStatus.FAILED,
                "FAILED", null, 0L, mutable);

        mutable.add(new RuntimeException("second"));

        assertThat(event.getFailureExceptions()).hasSize(1);
    }
}
