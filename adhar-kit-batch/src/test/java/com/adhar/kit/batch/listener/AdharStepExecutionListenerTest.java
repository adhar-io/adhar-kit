package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.metrics.BatchMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.step.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdharStepExecutionListener}.
 */
class AdharStepExecutionListenerTest {

    private StepExecution stepExecution(String name, long reads, long writes, long skips) {
        var exec = mock(StepExecution.class);
        when(exec.getStepName()).thenReturn(name);
        when(exec.getReadCount()).thenReturn(reads);
        when(exec.getWriteCount()).thenReturn(writes);
        when(exec.getSkipCount()).thenReturn(skips);
        when(exec.getStatus()).thenReturn(BatchStatus.COMPLETED);
        return exec;
    }

    @Test
    @DisplayName("afterStep records step metrics and returns null")
    void afterStepRecordsMetrics() {
        var metrics = mock(BatchMetrics.class);
        var listener = new AdharStepExecutionListener(metrics);
        var exec = stepExecution("step1", 100, 95, 5);

        var result = listener.afterStep(exec);

        assertThat(result).isNull();
        verify(metrics).recordStepExecution("step1", 100, 95, 5);
    }

    @Test
    @DisplayName("beforeStep does not throw")
    void beforeStepLogs() {
        var listener = new AdharStepExecutionListener();
        var exec = stepExecution("step1", 0, 0, 0);

        assertThatCode(() -> listener.beforeStep(exec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no-arg listener skips metrics recording")
    void noMetricsWhenNull() {
        var metrics = mock(BatchMetrics.class);
        var listener = new AdharStepExecutionListener();
        var exec = stepExecution("step1", 1, 1, 0);

        assertThatCode(() -> listener.afterStep(exec)).doesNotThrowAnyException();
        verifyNoInteractions(metrics);
    }

    @Test
    @DisplayName("metrics failure is swallowed")
    void metricsFailureSwallowed() {
        var metrics = mock(BatchMetrics.class);
        doThrow(new RuntimeException("registry down"))
                .when(metrics).recordStepExecution(eq("step1"), eq(1L), eq(1L), eq(0L));
        var listener = new AdharStepExecutionListener(metrics);
        var exec = stepExecution("step1", 1, 1, 0);

        assertThatCode(() -> listener.afterStep(exec)).doesNotThrowAnyException();
    }
}
