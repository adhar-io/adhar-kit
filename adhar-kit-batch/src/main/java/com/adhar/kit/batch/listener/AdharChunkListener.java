package com.adhar.kit.batch.listener;

import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * The kit's {@link ChunkListener}, attached to every chunk-oriented step by
 * {@link BatchListenerAttacher}: reports each chunk once its transaction has committed.
 *
 * <p>Spring Batch 6's chunk-oriented step calls {@code afterChunk} inside the chunk's
 * transaction, before the commit. A chunk is only progress once committed (a rollback
 * re-processes it), so the observers are called from the transaction's {@code afterCommit}; when
 * no transaction synchronization is active, immediately.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class AdharChunkListener implements ChunkListener<Object, Object> {

    private final BatchRunObservers observers;

    /**
     * @param observers notified of every committed chunk
     */
    public AdharChunkListener(List<BatchRunObserver> observers) {
        this.observers = new BatchRunObservers(observers);
    }

    @Override
    public void afterChunk(Chunk<Object> chunk) {
        StepContext context = StepSynchronizationManager.getContext();
        if (context == null || observers.isEmpty()) {
            return;
        }
        StepExecution step = context.getStepExecution();
        long items = chunk.size();
        Runnable committed = () -> observers.notify("chunkCommitted", observer -> observer.chunkCommitted(step, items));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed.run();
                }
            });
        } else {
            committed.run();
        }
    }
}
