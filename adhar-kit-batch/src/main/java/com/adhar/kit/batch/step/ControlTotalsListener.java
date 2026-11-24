package com.adhar.kit.batch.step;

import com.adhar.kit.batch.fault.ControlTotalsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStream;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a chunk step's {@link ControlTotals} when it ends, on the totals of its whole JobInstance.
 *
 * <p>As an {@link ItemStream}, it is updated only when a chunk commits, inside the chunk's
 * transaction, after the chunk's counts were applied: what it writes to the ExecutionContext
 * ({@value ControlTotals#KEY_PREFIX}{@code <count>}) is therefore exactly what the identity has
 * committed so far, and is what the next execution of the step starts from
 * ({@value ControlTotals#KEY_PREFIX}{@code base.<count>}). Its state lives in each
 * StepExecution's own context, so one listener serves concurrent partitions of the same step.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
final class ControlTotalsListener implements StepExecutionListener, ItemStream {

    private static final String BASE = ControlTotals.KEY_PREFIX + "base.";

    private final ControlTotals totals;
    private final Map<ExecutionContext, StepExecution> running = Collections.synchronizedMap(new IdentityHashMap<>());

    ControlTotalsListener(ControlTotals totals) {
        this.totals = totals;
    }

    @Override
    public void beforeStep(StepExecution step) {
        ExecutionContext context = step.getExecutionContext();
        // What the identity's earlier executions committed is the base of this one.
        write(context, BASE, committed(context, ControlTotals.KEY_PREFIX));
        running.put(context, step);
    }

    @Override
    public void update(ExecutionContext context) {
        StepExecution step = running.get(context);
        if (step != null) {
            write(context, ControlTotals.KEY_PREFIX, totalsOf(step));
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution step) {
        running.remove(step.getExecutionContext());
        if (step.getStatus() != BatchStatus.COMPLETED) {
            return null;
        }
        ControlTotals.Totals counted = totalsOf(step);
        List<String> mismatches;
        try {
            mismatches = totals.mismatches(step, counted);
        } catch (RuntimeException ex) {
            log.error("Step [{}]: an expected control total could not be computed", step.getStepName(), ex);
            return fail(step, ex);
        }
        if (mismatches.isEmpty()) {
            log.info("Step [{}]: control totals balance ({})", step.getStepName(), counted);
            return null;
        }
        ControlTotalsException mismatch = new ControlTotalsException(step.getStepName(), mismatches);
        log.error("{}", mismatch.getMessage());
        return fail(step, mismatch);
    }

    private static ExitStatus fail(StepExecution step, Throwable failure) {
        step.setStatus(BatchStatus.FAILED);
        step.addFailureException(failure);
        // The stack trace, as Spring Batch records any failure: a manager in another process sees a
        // remote partition only through this description, and reads the exception's class from it
        // (PartitionOutcomeAggregator).
        return ExitStatus.FAILED.addExitDescription(failure);
    }

    /** The identity's totals: the base this execution started from plus its own counts. */
    private static ControlTotals.Totals totalsOf(StepExecution step) {
        return committed(step.getExecutionContext(), BASE).plus(ControlTotals.Totals.of(step));
    }

    private static ControlTotals.Totals committed(ExecutionContext context, String prefix) {
        return new ControlTotals.Totals(context.getLong(prefix + "read", 0), context.getLong(prefix + "written", 0),
                context.getLong(prefix + "filtered", 0), context.getLong(prefix + "readSkips", 0),
                context.getLong(prefix + "processSkips", 0), context.getLong(prefix + "writeSkips", 0));
    }

    private static void write(ExecutionContext context, String prefix, ControlTotals.Totals totals) {
        context.putLong(prefix + "read", totals.read());
        context.putLong(prefix + "written", totals.written());
        context.putLong(prefix + "filtered", totals.filtered());
        context.putLong(prefix + "readSkips", totals.readSkips());
        context.putLong(prefix + "processSkips", totals.processSkips());
        context.putLong(prefix + "writeSkips", totals.writeSkips());
    }
}
