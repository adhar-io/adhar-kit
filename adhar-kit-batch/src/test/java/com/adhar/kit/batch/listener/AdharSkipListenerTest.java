package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.listener.BatchRunObserver.SkipPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdharSkipListener}.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdharSkipListenerTest {

    private final BatchRunObserver observer = mock(BatchRunObserver.class);
    private final AdharSkipListener listener = new AdharSkipListener(List.of(observer));

    @Test
    @DisplayName("reports each skip callback with its phase")
    void reportsEachPhase() {
        var read = new RuntimeException("read");
        var process = new RuntimeException("process");
        var write = new RuntimeException("write");

        listener.onSkipInRead(read);
        listener.onSkipInProcess("item", process);
        listener.onSkipInWrite("item", write);

        verify(observer).itemSkipped(null, SkipPhase.READ, read);
        verify(observer).itemSkipped(null, SkipPhase.PROCESS, process);
        verify(observer).itemSkipped(null, SkipPhase.WRITE, write);
    }

    @Test
    @DisplayName("never logs the content of a skipped item")
    void neverLogsItemContent(CapturedOutput output) {
        Object personalData = new Object() {
            @Override
            public String toString() {
                return "card-4111111111111111";
            }
        };

        listener.onSkipInProcess(personalData, new IllegalArgumentException("invalid"));
        listener.onSkipInWrite(personalData, new IllegalArgumentException("invalid"));

        assertThat(output).contains("Skipped an item in process");
        assertThat(output).doesNotContain("card-4111111111111111");
    }

    @Test
    @DisplayName("a listener without observers does not throw")
    void noObservers() {
        var empty = new AdharSkipListener(null);

        assertThatCode(() -> empty.onSkipInRead(new RuntimeException("x"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an observer that fails does not fail the step")
    void observerFailureSwallowed() {
        doThrow(new RuntimeException("down")).when(observer).itemSkipped(any(), any(), any());

        assertThatCode(() -> listener.onSkipInRead(new RuntimeException("x"))).doesNotThrowAnyException();
    }
}
