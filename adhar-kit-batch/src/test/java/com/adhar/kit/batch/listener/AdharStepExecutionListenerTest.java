package com.adhar.kit.batch.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.step.StepExecution;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdharStepExecutionListener}.
 */
class AdharStepExecutionListenerTest {

    private final BatchRunObserver observer = mock(BatchRunObserver.class);
    private final AdharStepExecutionListener listener = new AdharStepExecutionListener(List.of(observer));

    private StepExecution stepExecution() {
        var exec = mock(StepExecution.class);
        when(exec.getStepName()).thenReturn("step1");
        when(exec.getStatus()).thenReturn(BatchStatus.COMPLETED);
        return exec;
    }

    @Test
    @DisplayName("beforeStep notifies the observers")
    void beforeStepNotifies() {
        var exec = stepExecution();

        listener.beforeStep(exec);

        verify(observer).stepStarted(exec);
    }

    @Test
    @DisplayName("afterStep notifies the observers and keeps the step's exit status")
    void afterStepNotifies() {
        var exec = stepExecution();

        assertThat(listener.afterStep(exec)).isNull();

        verify(observer).stepFinished(exec);
    }

    @Test
    @DisplayName("a listener without observers does not throw")
    void noObservers() {
        var empty = new AdharStepExecutionListener(null);
        var exec = stepExecution();

        assertThatCode(() -> {
            empty.beforeStep(exec);
            empty.afterStep(exec);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an observer that fails does not fail the step")
    void observerFailureSwallowed() {
        var exec = stepExecution();
        doThrow(new RuntimeException("registry down")).when(observer).stepFinished(exec);

        assertThat(listener.afterStep(exec)).isNull();
    }
}
