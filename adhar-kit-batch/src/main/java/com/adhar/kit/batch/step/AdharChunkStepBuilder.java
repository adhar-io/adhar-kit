package com.adhar.kit.batch.step;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.fault.DeadLetterReplayRegistry;
import com.adhar.kit.batch.fault.DeadLetterSkipListener;
import com.adhar.kit.batch.fault.FaultTolerance;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
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
@Slf4j
public final class AdharChunkStepBuilder<I, O> {

    private final ChunkOrientedStepBuilder<I, O> delegate;
    private final BatchProperties.Retry retryDefaults;
    private final BatchListenerAttacher listeners;
    private final BatchProperties.DeadLetter deadLetterSettings;
    private final DeadLetterSkipListener deadLetters;
    private final DeadLetterReplayRegistry replays;
    private FaultTolerance faultTolerance = FaultTolerance.none();
    private ControlTotals controlTotals;
    private ItemProcessor<? super I, ? extends O> processor;
    private ItemWriter<? super O> writer;

    AdharChunkStepBuilder(ChunkOrientedStepBuilder<I, O> delegate, BatchProperties properties,
                          BatchListenerAttacher listeners, DeadLetterSkipListener deadLetters,
                          DeadLetterReplayRegistry replays) {
        this.delegate = delegate;
        this.retryDefaults = properties.getRetry();
        this.deadLetterSettings = properties.getDeadLetter();
        this.listeners = listeners;
        this.deadLetters = deadLetters;
        this.replays = replays;
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
        this.processor = processor;
        return this;
    }

    /**
     * @param writer the item writer
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> writer(ItemWriter<? super O> writer) {
        delegate.writer(writer);
        this.writer = writer;
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
     * Checks the step's counts when it ends (design 0025 §5.4): every item read accounted for, and
     * the counts the job expects. A mismatch fails the step with a
     * {@link com.adhar.kit.batch.fault.ControlTotalsException}, which the Kubernetes runner exits 3
     * for, with the summary reason {@code control-totals}.
     *
     * @param controlTotals what to check, for example {@code ControlTotals.balanced()}
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> controlTotals(ControlTotals controlTotals) {
        Assert.notNull(controlTotals, "controlTotals must not be null");
        this.controlTotals = controlTotals;
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
     * that declares any. Set the processor and the writer through this builder, not here: the
     * {@code dlq-replay} ops command replays a dead-lettering step's items through the ones this
     * builder was given.
     *
     * @param customizer receives Spring Batch's builder
     * @return this builder
     */
    public AdharChunkStepBuilder<I, O> customize(Consumer<ChunkOrientedStepBuilder<I, O>> customizer) {
        customizer.accept(delegate);
        return this;
    }

    /**
     * @return the step, with the fault tolerance applied, the kit's listeners attached, its
     *         control totals checked when it ends and, when the fault tolerance asks for it,
     *         skipped items dead-lettered and its processor and writer registered for the
     *         {@code dlq-replay} ops command
     * @throws IllegalStateException when the step asks for dead letters and there is no sink (no
     *                               DataSource) although {@code adhar.batch.dead-letter.enabled}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ChunkOrientedStep<I, O> build() {
        faultTolerance.applyTo(delegate, retryDefaults);
        ChunkOrientedStep<I, O> step = delegate.build();
        listeners.attach(step);
        if (controlTotals != null && controlTotals.checksAnything()) {
            // Registered last, so its afterStep runs first (Spring Batch calls them in reverse) and
            // the kit's own listeners already see the step FAILED when its totals do not balance.
            ControlTotalsListener totals = new ControlTotalsListener(controlTotals);
            step.registerItemStream(totals);
            step.registerStepExecutionListener(totals);
        }
        if (faultTolerance.isDeadLetter()) {
            if (deadLetters != null) {
                step.registerSkipListener((SkipListener) deadLetters);
                if (replays != null) {
                    replays.register(step.getName(), processor, writer);
                }
            } else if (deadLetterSettings.isEnabled()) {
                throw new IllegalStateException("Step [" + step.getName() + "] dead-letters skipped items, but there "
                        + "is no dead-letter table: the kit writes it on the JobRepository's DataSource");
            } else {
                log.info("Step [{}] declares deadLetter(), but adhar.batch.dead-letter.enabled=false: skipped items "
                        + "are counted and logged only", step.getName());
            }
        }
        return step;
    }
}
