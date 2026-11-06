package com.adhar.kit.batch.listener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AdharChunkListener}: a chunk counts once its transaction has committed.
 */
class AdharChunkListenerTest {

    private final BatchRunObserver observer = mock(BatchRunObserver.class);
    private final AdharChunkListener listener = new AdharChunkListener(List.of(observer));
    private final TransactionTemplate transaction = new TransactionTemplate(new ResourcelessTransactionManager());
    private final StepExecution step = new StepExecution(1L, "step",
            new JobExecution(1L, new JobInstance(1L, "job"), new JobParameters()));
    private final Chunk<Object> chunk = new Chunk<>(List.of("a", "b", "c"));

    private boolean registered;

    private void onTheStepsThread() {
        StepSynchronizationManager.register(step);
        registered = true;
    }

    @AfterEach
    void tearDown() {
        if (registered) {
            StepSynchronizationManager.close();
        }
    }

    @Test
    @DisplayName("a chunk is reported after its transaction commits, not before")
    void reportedAfterCommit() {
        onTheStepsThread();

        transaction.executeWithoutResult(status -> {
            listener.afterChunk(chunk);
            verify(observer, never()).chunkCommitted(any(), anyLong());
        });

        verify(observer).chunkCommitted(step, 3);
    }

    @Test
    @DisplayName("a rolled-back chunk is not progress and is never reported")
    void rollbackIsNotProgress() {
        onTheStepsThread();

        transaction.executeWithoutResult(status -> {
            listener.afterChunk(chunk);
            status.setRollbackOnly();
        });

        verify(observer, never()).chunkCommitted(any(), anyLong());
    }

    @Test
    @DisplayName("without a transaction the chunk is reported immediately")
    void noTransaction() {
        onTheStepsThread();

        listener.afterChunk(chunk);

        verify(observer).chunkCommitted(step, 3);
    }

    @Test
    @DisplayName("outside a step there is nothing to report")
    void outsideAStep() {
        listener.afterChunk(chunk);

        verifyNoInteractions(observer);
    }

    @Test
    @DisplayName("no observers: no synchronization registered, no failure")
    void noObservers() {
        onTheStepsThread();
        var none = new AdharChunkListener(null);

        assertThatCode(() -> transaction.executeWithoutResult(status -> none.afterChunk(chunk)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an observer that throws after the commit does not fail the run")
    void observerFailure() {
        onTheStepsThread();
        doThrow(new IllegalStateException("broken observer")).when(observer).chunkCommitted(any(), anyLong());

        assertThatCode(() -> transaction.executeWithoutResult(status -> listener.afterChunk(chunk)))
                .doesNotThrowAnyException();
    }
}
