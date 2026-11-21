package com.adhar.kit.batch.step;

import com.adhar.kit.batch.fault.ControlTotalsException;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * The control totals a chunk step checks when it ends (design 0025 §5.4, §5.13): the counts the
 * run reports in its termination summary, held to account. Declared on
 * {@link AdharChunkStepBuilder#controlTotals(ControlTotals)}.
 *
 * <pre>{@code
 * steps.<Payment, Payment>chunk("load")
 *      .reader(reader).processor(processor).writer(writer)
 *      .controlTotals(ControlTotals.balanced()
 *              .expectRead(step -> step.getExecutionContext().getLong("trailer.records")))
 *      .build();
 * }</pre>
 *
 * <ul>
 *   <li><b>Balanced</b>: every item read is accounted for, as written, filtered (the processor
 *       returned {@code null}) or skipped in processing or writing:
 *       {@code read = written + filtered + processSkips + writeSkips}. A read skip is no item
 *       read, so it is in no side of the balance.</li>
 *   <li><b>Expected</b>: a count equals what the job says it should be, for example the record
 *       count of a file's trailer that the reader put in the step's ExecutionContext, or a
 *       {@code COUNT(*)} of the source. The expectation is evaluated once, when the step ends.</li>
 * </ul>
 *
 * <p><b>Across restarts.</b> The totals are those of the step's whole JobInstance: what earlier
 * executions committed plus this one. The committed totals travel in the step's ExecutionContext,
 * written with each chunk commit, so a chunk that rolled back is never counted, although Spring
 * Batch 6 folds its counts into the failed StepExecution's own. A restart whose step reads nothing
 * new is still checked against everything the identity did.</p>
 *
 * <p><b>On a mismatch</b> the step ends FAILED with a {@link ControlTotalsException}, a
 * non-retryable failure: the Kubernetes runner exits 3 and the summary says
 * {@code "reason":"control-totals"}. The step's chunks are committed by then, so the job decides
 * what follows: fix the data and restart (the same totals are checked again), or abandon the
 * execution. Only a step that COMPLETED is checked; a failed or stopped one fails for its own
 * reason.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class ControlTotals {

    /** The ExecutionContext key prefix of the committed totals. */
    public static final String KEY_PREFIX = "adhar.batch.control-totals.";

    private final boolean balanced;
    private final Map<Count, ToLongFunction<StepExecution>> expected;

    private ControlTotals(boolean balanced, Map<Count, ToLongFunction<StepExecution>> expected) {
        this.balanced = balanced;
        this.expected = Collections.unmodifiableMap(new LinkedHashMap<>(expected));
    }

    /**
     * @return totals that must balance: every item read is written, filtered or skipped
     */
    public static ControlTotals balanced() {
        return new ControlTotals(true, Map.of());
    }

    /**
     * @return totals with no balance check, only the expectations added to them
     */
    public static ControlTotals expected() {
        return new ControlTotals(false, Map.of());
    }

    /**
     * @param count    the count
     * @param expected what it must be once the step ends, given its StepExecution (its
     *                 ExecutionContext, its job's parameters)
     * @return these totals with that expectation; a second one for the same count replaces the first
     */
    public ControlTotals expect(Count count, ToLongFunction<StepExecution> expected) {
        Assert.notNull(count, "count must not be null");
        Assert.notNull(expected, "expected must not be null");
        Map<Count, ToLongFunction<StepExecution>> next = new LinkedHashMap<>(this.expected);
        next.put(count, expected);
        return new ControlTotals(balanced, next);
    }

    /**
     * @param expected the number of items the step must have read
     * @return these totals with that expectation
     */
    public ControlTotals expectRead(ToLongFunction<StepExecution> expected) {
        return expect(Count.READ, expected);
    }

    /**
     * @param expected the number of items the step must have written
     * @return these totals with that expectation
     */
    public ControlTotals expectWritten(ToLongFunction<StepExecution> expected) {
        return expect(Count.WRITTEN, expected);
    }

    /**
     * @return whether the balance is checked
     */
    public boolean isBalanced() {
        return balanced;
    }

    /**
     * @return the expectations, in the order declared
     */
    public Map<Count, ToLongFunction<StepExecution>> getExpected() {
        return expected;
    }

    /**
     * @return whether these totals check anything
     */
    public boolean checksAnything() {
        return balanced || !expected.isEmpty();
    }

    /**
     * Checks a step's totals.
     *
     * @param step   the step, for the expectations
     * @param totals its totals across the JobInstance
     * @return every mismatch, in words; empty when the totals balance
     * @throws RuntimeException what an expectation threw
     */
    List<String> mismatches(StepExecution step, Totals totals) {
        List<String> mismatches = new ArrayList<>();
        if (balanced) {
            long accounted = totals.written() + totals.filtered() + totals.processSkips() + totals.writeSkips();
            if (accounted != totals.read()) {
                mismatches.add("read " + totals.read() + " but written " + totals.written() + " + filtered "
                        + totals.filtered() + " + skipped " + (totals.processSkips() + totals.writeSkips())
                        + " (process " + totals.processSkips() + ", write " + totals.writeSkips() + ") = " + accounted
                        + (accounted < totals.read() ? ": " + (totals.read() - accounted) + " item(s) unaccounted for"
                        : ": " + (accounted - totals.read()) + " more than read"));
            }
        }
        expected.forEach((count, expectation) -> {
            long want = expectation.applyAsLong(step);
            long got = count.of(totals);
            if (want != got) {
                mismatches.add(count.label() + " " + got + " but " + want + " expected");
            }
        });
        return mismatches;
    }

    /**
     * A count a step reports.
     */
    public enum Count {
        /** Items read. */
        READ,
        /** Items written. */
        WRITTEN,
        /** Items the processor filtered out (returned {@code null} for). */
        FILTERED,
        /** Items skipped, in reading, processing and writing: the summary's {@code skipped}. */
        SKIPPED;

        long of(Totals totals) {
            return switch (this) {
                case READ -> totals.read();
                case WRITTEN -> totals.written();
                case FILTERED -> totals.filtered();
                case SKIPPED -> totals.readSkips() + totals.processSkips() + totals.writeSkips();
            };
        }

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A step's counts.
     *
     * @param read         items read
     * @param written      items written
     * @param filtered     items filtered
     * @param readSkips    read failures skipped
     * @param processSkips items skipped in processing
     * @param writeSkips   items skipped in writing
     */
    public record Totals(long read, long written, long filtered, long readSkips, long processSkips,
                         long writeSkips) {

        /** No items. */
        public static final Totals NONE = new Totals(0, 0, 0, 0, 0, 0);

        /**
         * @param step a StepExecution
         * @return its own counts, this execution's only
         */
        public static Totals of(StepExecution step) {
            return new Totals(step.getReadCount(), step.getWriteCount(), step.getFilterCount(),
                    step.getReadSkipCount(), step.getProcessSkipCount(), step.getWriteSkipCount());
        }

        /**
         * @param other more counts
         * @return the sum
         */
        public Totals plus(Totals other) {
            return new Totals(read + other.read, written + other.written, filtered + other.filtered,
                    readSkips + other.readSkips, processSkips + other.processSkips, writeSkips + other.writeSkips);
        }
    }
}
