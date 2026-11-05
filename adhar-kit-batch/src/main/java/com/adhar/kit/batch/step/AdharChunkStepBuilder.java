package com.adhar.kit.batch.step;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.fault.FaultTolerance;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import org.springframework.batch.core.listener.StepListener;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.Assert;

import java.util.function.Consumer;

/**
 * Builds a Spring Batch 6 chunk-oriented step ({@link ChunkOrientedStep}) the platform way:
 * on the JobRepository's transaction manager, with the kit's listeners, and with a declared
 * {@link FaultTolerance}. Obtained from {@link AdharSteps#chunk(String)}.
 *
 * <p>The transaction manager is the point (INV-4): each chunk commits its business writes and the
 * step's progress in the JobRepository in one transaction, so a run killed mid-chunk restarts
 * from the last commit without duplicate output, and a zombie writer whose execution was
 * recovered fails its next commit on the repository's version check, rolling its writes back with
 * it. Spring Batch's own builder defaults to a transaction manager that does nothing.</p>
 *
 * <p>Methods not offered here are reachable through {@link #customize(Consumer)}.</p>
 *
 * @param <I> the type read
 * @param <O> the type written
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class AdharChunkStepBuilder<I, O> {

    private final ChunkOrientedStepBuilder<I, O> delegate;
    private final BatchProperties.Retry retryDefaults;
    private final BatchListenerAttacher listeners;
    private FaultTolerance faultTolerance = FaultTolerance.none();

    AdharChunkStepBuilder(ChunkOrientedStepBuilder<I, O> delegate, BatchProperties.Retry retryDefaults,
                          BatchListenerAttacher listeners) {
        this.delegate = delegate;
        this.retryDefaults = retryDefaults;
        this.listeners = listeners;
    }

    /**
     * @param reader the item reader; registered as a stream when it is an {@link ItemStream}
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> reader(ItemReader<? extends I> reader) {
        delegate.reader(reader);
        return this;
    }

    /**
     * @param processor the item processor (optional)
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> processor(ItemProcessor<? super I, ? extends O> processor) {
        delegate.processor(processor);
        return this;
    }

    /**
     * @param writer the item writer
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> writer(ItemWriter<? super O> writer) {
        delegate.writer(writer);
        return this;
    }

    /**
     * @param stream a further stream whose state is saved with each commit
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> stream(ItemStream stream) {
        delegate.stream(stream);
        return this;
    }

    /**
     * @param listener a Spring Batch listener of the application's own
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> listener(StepListener listener) {
        delegate.listener(listener);
        return this;
    }

    /**
     * @param listener an object with Spring Batch listener annotations
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> listener(Object listener) {
        delegate.listener(listener);
        return this;
    }

    /**
     * @param faultTolerance what to retry and skip
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> faultTolerance(FaultTolerance faultTolerance) {
        Assert.notNull(faultTolerance, "faultTolerance must not be null");
        this.faultTolerance = faultTolerance;
        return this;
    }

    /**
     * Processes the items of a chunk concurrently (a multi-threaded step). Restart then cannot
     * resume inside a chunk from a reader's saved position unless the reader is thread-safe and
     * its state is not saved; prefer local partitioning for restartable parallelism.
     *
     * @param taskExecutor the executor of item processing
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> taskExecutor(AsyncTaskExecutor taskExecutor) {
        delegate.taskExecutor(taskExecutor);
        return this;
    }

    /**
     * @param transactionAttribute the chunk transaction's attributes (isolation, timeout)
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> transactionAttribute(TransactionAttribute transactionAttribute) {
        delegate.transactionAttribute(transactionAttribute);
        return this;
    }

    /**
     * @param startLimit how many times the step may be started for one JobInstance
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> startLimit(int startLimit) {
        delegate.startLimit(startLimit);
        return this;
    }

    /**
     * @param allowStartIfComplete whether a restart runs the step again when it completed
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> allowStartIfComplete(boolean allowStartIfComplete) {
        delegate.allowStartIfComplete(allowStartIfComplete);
        return this;
    }

    /**
     * Configures the underlying Spring Batch builder directly, for what this builder does not
     * offer. Fault tolerance set here is replaced by {@link #faultTolerance(FaultTolerance)} when
     * that declares any.
     *
     * @param customizer receives Spring Batch's builder
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> customize(Consumer<ChunkOrientedStepBuilder<I, O>> customizer) {
        customizer.accept(delegate);
        return this;
    }

    /**
     * @return the step, with the fault tolerance applied and the kit's listeners attached
     */
    public ChunkOrientedStep<I, O> build() {
        faultTolerance.applyTo(delegate, retryDefaults);
        ChunkOrientedStep<I, O> step = delegate.build();
        listeners.attach(step);
        return step;
    }
}
