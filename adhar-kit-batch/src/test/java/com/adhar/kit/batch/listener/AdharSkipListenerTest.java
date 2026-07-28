package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.metrics.BatchMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AdharSkipListener}.
 */
class AdharSkipListenerTest {

    @Test
    @DisplayName("records a skip metric for each skip callback")
    void recordsSkipMetrics() {
        var metrics = mock(BatchMetrics.class);
        var listener = new AdharSkipListener<String, String>(metrics);

        listener.onSkipInRead(new RuntimeException("read"));
        listener.onSkipInProcess("item", new RuntimeException("process"));
        listener.onSkipInWrite("item", new RuntimeException("write"));

        verify(metrics, times(3))
                .recordStepExecution(AdharSkipListener.SKIP_METRIC_STEP, 0, 0, 1);
    }

    @Test
    @DisplayName("no-arg listener skips metrics recording")
    void noMetricsWhenNull() {
        var metrics = mock(BatchMetrics.class);
        var listener = new AdharSkipListener<String, String>();

        assertThatCode(() -> listener.onSkipInRead(new RuntimeException("x")))
                .doesNotThrowAnyException();
        verifyNoInteractions(metrics);
    }

    @Test
    @DisplayName("metrics failure is swallowed")
    void metricsFailureSwallowed() {
        var metrics = mock(BatchMetrics.class);
        org.mockito.Mockito.doThrow(new RuntimeException("down"))
                .when(metrics).recordStepExecution(AdharSkipListener.SKIP_METRIC_STEP, 0, 0, 1);
        var listener = new AdharSkipListener<String, String>(metrics);

        assertThatCode(() -> listener.onSkipInRead(new RuntimeException("x")))
                .doesNotThrowAnyException();
    }
}
