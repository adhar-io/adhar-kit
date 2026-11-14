package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.listener.BatchRunObserver.SkipPhase;
import com.adhar.kit.batch.telemetry.BatchProgressMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;

import org.springframework.util.ClassUtils;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Sets every item a step skips aside in a {@link DeadLetterSink}: the item as JSON, the
 * exception's class and message, the step, the execution and the business date. Registered on a
 * step that declares {@link FaultTolerance#deadLetter()}.
 *
 * <p>It also counts the step's dead letters, in the step's execution context
 * ({@value #COUNT_KEY}, read by the termination summary) and in the
 * {@code adhar.batch.items.dead.lettered} counter. A sink that fails fails the chunk: an item is
 * never skipped without being kept.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class DeadLetterSkipListener implements SkipListener<Object, Object> {

    /** The step execution context key counting the step's dead letters. */
    public static final String COUNT_KEY = "adhar.batch.dead-lettered";

    /** The parameter the business date is taken from first. */
    static final String BUSINESS_DATE = "businessDate";

    private final DeadLetterSink sink;
    private final Function<Object, String> serializer;
    private final BatchProgressMetrics metrics;

    /**
     * @param sink       where the items go
     * @param serializer the item as JSON; may return {@code null} or throw, the item is then kept
     *                   by type only
     * @param metrics    the kit's meters, may be {@code null}
     */
    public DeadLetterSkipListener(DeadLetterSink sink, Function<Object, String> serializer, BatchProgressMetrics metrics) {
        this.sink = sink;
        this.serializer = serializer;
        this.metrics = metrics;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        deadLetter(SkipPhase.READ, null, t);
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        deadLetter(SkipPhase.PROCESS, item, t);
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        deadLetter(SkipPhase.WRITE, item, t);
    }

    private void deadLetter(SkipPhase phase, Object item, Throwable error) {
        StepContext context = StepSynchronizationManager.getContext();
        if (context == null) {
            throw new IllegalStateException("A skipped item can only be dead-lettered on its step's thread");
        }
        StepExecution step = context.getStepExecution();
        String jobName = step.getJobExecution().getJobInstance().getJobName();
        sink.write(new DeadLetterSink.Entry(step.getJobExecutionId(), jobName, step.getStepName(),
                businessDate(step.getJobExecution().getJobParameters()), phase.label(),
                item == null ? null : item.getClass().getName(), json(item),
                error == null ? "unknown" : error.getClass().getName(), error == null ? null : error.getMessage()));
        step.getExecutionContext().putLong(COUNT_KEY, step.getExecutionContext().getLong(COUNT_KEY, 0) + 1);
        if (metrics != null) {
            metrics.recordDeadLettered(jobName, step.getStepName(), 1);
        }
    }

    /**
     * @return the item as JSON through Jackson 3 when the application has it, otherwise nothing
     *         (the item is then kept by type only)
     */
    public static Function<Object, String> defaultSerializer() {
        boolean jackson = ClassUtils.isPresent("tools.jackson.databind.json.JsonMapper",
                DeadLetterSkipListener.class.getClassLoader());
        return jackson ? Jackson.serializer() : item -> null;
    }

    private String json(Object item) {
        if (item == null) {
            return null;
        }
        try {
            return serializer.apply(item);
        } catch (RuntimeException ex) {
            log.warn("Could not write a skipped {} as JSON; it is dead-lettered by type only: {}",
                    item.getClass().getName(), ex.getClass().getName());
            return null;
        }
    }

    /**
     * The business date of a run, as the dead-letter table and the log context record it.
     *
     * @param parameters a run's parameters
     * @return {@value #BUSINESS_DATE} when it is a date, else the first identifying date, else {@code null}
     */
    public static LocalDate businessDate(JobParameters parameters) {
        JobParameter<?> named = parameters.getParameter(BUSINESS_DATE);
        if (named != null && named.value() instanceof LocalDate date) {
            return date;
        }
        return parameters.getIdentifyingParameters().stream()
                .filter(parameter -> parameter.value() instanceof LocalDate)
                .sorted(Comparator.comparing(JobParameter::name))
                .map(parameter -> (LocalDate) parameter.value())
                .findFirst().orElse(null);
    }

    /** Loaded only when Jackson 3 is on the classpath. */
    private static final class Jackson {

        static Function<Object, String> serializer() {
            tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.shared();
            return mapper::writeValueAsString;
        }
    }
}
