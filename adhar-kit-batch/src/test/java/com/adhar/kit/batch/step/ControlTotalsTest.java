package com.adhar.kit.batch.step;

import com.adhar.kit.batch.fault.ControlTotalsException;
import com.adhar.kit.batch.fault.FaultTolerance;
import com.adhar.kit.batch.fault.NonRetryableBatchException;
import com.adhar.kit.batch.partition.PartitionOutcomeAggregator;
import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Control totals (design 0025 §5.4, §5.7, §5.13) on a real JobRepository: what a chunk step
 * reports is checked when it ends, across the restarts of its identity, and a mismatch is a
 * non-retryable failure with the summary reason {@code control-totals}.
 */
// TestJobs only because the kit's auto-configuration needs at least one Job bean.
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class})
@Execution(ExecutionMode.SAME_THREAD)
class ControlTotalsTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.h2().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    AdharSteps steps;
    @Autowired
    JobRepository jobRepository;
    @Autowired
    JobOperator jobOperator;

    private final List<Long> written = new CopyOnWriteArrayList<>();

    /** A skippable write failure, to put a chunk into Spring Batch's item-by-item scan. */
    static class Rejected extends RuntimeException {
        Rejected(String message) {
            super(message);
        }
    }

    private AdharChunkStepBuilder<Long, Long> step(long items) {
        return steps.<Long, Long>chunk("totalsStep", 5).reader(new Counting(items))
                .writer(chunk -> written.addAll(chunk.getItems()));
    }

    private Job job(AdharChunkStepBuilder<Long, Long> builder) {
        return new JobBuilder("totalsJob-" + UUID.randomUUID(), jobRepository).start(builder.build()).build();
    }

    private static JobParameters identity() {
        return new JobParametersBuilder().addString("run", UUID.randomUUID().toString()).toJobParameters();
    }

    private static StepExecution onlyStep(JobExecution execution) {
        return execution.getStepExecutions().iterator().next();
    }

    @Test
    @DisplayName("a step whose items are all written, filtered or skipped balances and completes")
    void balanced() throws Exception {
        var builder = step(12)
                .processor(item -> {
                    if (item == 4) {
                        return null;
                    }
                    if (item == 7) {
                        throw new Rejected("process " + item);
                    }
                    return item;
                })
                .writer(chunk -> {
                    if (chunk.getItems().contains(11L)) {
                        throw new Rejected("write chunk");
                    }
                    written.addAll(chunk.getItems());
                })
                .faultTolerance(FaultTolerance.none().skip(Rejected.class, 5))
                .controlTotals(ControlTotals.balanced().expectRead(step -> 12).expect(ControlTotals.Count.FILTERED,
                        step -> 1).expect(ControlTotals.Count.SKIPPED, step -> 2));

        JobExecution execution = jobOperator.start(job(builder), identity());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution step = onlyStep(execution);
        assertThat(step.getReadCount()).isEqualTo(12);
        assertThat(step.getWriteCount() + step.getFilterCount() + step.getProcessSkipCount() + step.getWriteSkipCount())
                .isEqualTo(12);
        assertThat(step.getWriteSkipCount()).isEqualTo(1);
        assertThat(step.getExecutionContext().getLong(ControlTotals.KEY_PREFIX + "read")).isEqualTo(12);
        assertThat(RunOutcome.of(execution).reason()).isNull();
    }

    @Test
    @DisplayName("a count other than the one expected fails the step: exit 3, reason control-totals, the mismatch in words")
    void expectedCountDiffers() throws Exception {
        var builder = step(10).controlTotals(ControlTotals.expected()
                .expectRead(step -> step.getJobParameters().getLong("trailer")));

        JobExecution execution = jobOperator.start(job(builder), new JobParametersBuilder(identity())
                .addLong("trailer", 12L).toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(onlyStep(execution).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(onlyStep(execution).getFailureExceptions()).singleElement()
                .isInstanceOfSatisfying(ControlTotalsException.class, failure -> {
                    assertThat(failure.getStepName()).isEqualTo("totalsStep");
                    assertThat(failure.getMismatches()).containsExactly("read 10 but 12 expected");
                });
        assertThat(written).hasSize(10);
        RunOutcome outcome = RunOutcome.of(execution);
        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(outcome.reason()).isEqualTo(RunOutcome.CONTROL_TOTALS);
        assertThat(outcome.message()).contains("read 10 but 12 expected");
        assertThat(jobRepository.getStepExecution(execution.getId(), onlyStep(execution).getId()).getStatus())
                .as("persisted FAILED, not only in memory").isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("a remote partition's mismatch reaches its manager as exit 3 control-totals, through what the repository recorded")
    void remotePartitionMismatch() throws Exception {
        var builder = step(10).controlTotals(ControlTotals.expected().expectRead(step -> 12));
        JobExecution worker = jobOperator.start(job(builder), identity());
        // What a manager in another pod sees of the partition: its record, without the exception.
        StepExecution recorded = jobRepository.getStepExecution(worker.getId(), onlyStep(worker).getId());
        assertThat(recorded.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(recorded.getFailureExceptions()).isEmpty();

        JobExecution run = new JobExecution(worker.getId() + 1000, new JobInstance(worker.getId() + 1000, "manager"),
                new JobParameters());
        StepExecution manager = new StepExecution(recorded.getId() + 1000, "load", run);
        Throwable failure = catchThrowable(() -> new PartitionOutcomeAggregator(jobRepository)
                .aggregate(manager, List.of(recorded)));

        assertThat(failure).isInstanceOf(NonRetryableBatchException.class)
                .hasMessageContaining(ControlTotalsException.class.getName())
                .rootCause().isInstanceOfSatisfying(ControlTotalsException.class, cause -> {
                    assertThat(cause.getStepName()).isEqualTo("totalsStep");
                    assertThat(cause.getMismatches()).containsExactly("read 10 but 12 expected");
                });
        run.setStatus(BatchStatus.FAILED);
        run.addFailureException(failure);
        RunOutcome outcome = RunOutcome.of(run);
        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(outcome.reason()).isEqualTo(RunOutcome.CONTROL_TOTALS);
    }

    @Test
    @DisplayName("an expectation that cannot be computed fails the step with its own failure, retryably")
    void expectationThrows() throws Exception {
        var builder = step(3).controlTotals(ControlTotals.expected().expectWritten(step -> {
            throw new IllegalStateException("source count unavailable");
        }));

        JobExecution execution = jobOperator.start(job(builder), identity());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(ExitCodes.of(execution)).isEqualTo(ExitCodes.FAILED_RETRYABLE);
        assertThat(RunOutcome.of(execution).reason()).isNull();
    }

    @Test
    @DisplayName("the totals are the identity's: a restart adds what earlier executions committed")
    void restartCountsTheWholeIdentity() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        var builder = step(10)
                .writer(chunk -> {
                    if (fail.get() && chunk.getItems().contains(7L)) {
                        throw new IllegalStateException("database gone");
                    }
                    written.addAll(chunk.getItems());
                })
                .controlTotals(ControlTotals.balanced().expectRead(step -> 10).expectWritten(step -> 10));
        Job job = job(builder);
        JobParameters identity = identity();

        JobExecution first = jobOperator.start(job, identity);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(onlyStep(first).getFailureExceptions()).noneMatch(ControlTotalsException.class::isInstance);
        fail.set(false);
        JobExecution second = jobOperator.start(job, identity);

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(onlyStep(second).getReadCount()).as("the restart read only the rest").isEqualTo(5);
        assertThat(written).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    @DisplayName("items a failed scan left behind are caught: without totals the restart completes, with them it fails")
    void itemsLostInAFailedScan() throws Exception {
        // Spring Batch 6: a skippable write failure re-writes the chunk item by item, committing each;
        // a NON-skippable failure in that scan fails the step, and the reader's position saved with the
        // scan's commits is already past the whole chunk, so the restart never reads its remaining items.
        assertThat(lostInScan(null).getStatus()).as("Spring Batch alone").isEqualTo(BatchStatus.COMPLETED);
        assertThat(written).doesNotContain(3L, 4L, 5L);

        written.clear();
        JobExecution checked = lostInScan(ControlTotals.balanced());

        assertThat(checked.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(onlyStep(checked).getFailureExceptions()).singleElement()
                .isInstanceOfSatisfying(ControlTotalsException.class, failure -> assertThat(failure.getMismatches())
                        .singleElement().asString().contains("read 10 but written 7").contains("3 item(s) unaccounted for"));
        assertThat(ExitCodes.of(checked)).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
    }

    /** Runs the scan-loss job twice (fail, restart) and returns the restart. */
    private JobExecution lostInScan(ControlTotals totals) throws Exception {
        AtomicBoolean first = new AtomicBoolean(true);
        var builder = step(10)
                .writer(chunk -> {
                    if (first.get() && chunk.getItems().contains(3L)) {
                        if (chunk.size() > 1) {
                            throw new Rejected("chunk with 3");
                        }
                        throw new IllegalStateException("3 cannot be written");
                    }
                    written.addAll(chunk.getItems());
                })
                .faultTolerance(FaultTolerance.none().skip(Rejected.class, 5));
        if (totals != null) {
            builder.controlTotals(totals);
        }
        Job job = job(builder);
        JobParameters identity = identity();
        assertThat(jobOperator.start(job, identity).getStatus()).isEqualTo(BatchStatus.FAILED);
        first.set(false);
        return jobOperator.start(job, identity);
    }

    @Test
    @DisplayName("the balance in words, both ways, and an empty check checks nothing")
    void mismatchesInWords() {
        StepExecution step = new StepExecution("s", null);
        ControlTotals balanced = ControlTotals.balanced();

        assertThat(balanced.mismatches(step, new ControlTotals.Totals(10, 7, 1, 4, 1, 1)))
                .as("read skips are no item read: on neither side").isEmpty();
        assertThat(balanced.mismatches(step, new ControlTotals.Totals(10, 6, 1, 0, 1, 0)))
                .containsExactly("read 10 but written 6 + filtered 1 + skipped 1 (process 1, write 0) = 8: "
                        + "2 item(s) unaccounted for");
        assertThat(balanced.mismatches(step, new ControlTotals.Totals(3, 4, 0, 0, 0, 0)))
                .singleElement().asString().endsWith(": 1 more than read");
        assertThat(ControlTotals.expected().expect(ControlTotals.Count.SKIPPED, s -> 5)
                .mismatches(step, new ControlTotals.Totals(10, 6, 1, 3, 1, 1))).isEmpty();
        assertThat(ControlTotals.expected().checksAnything()).isFalse();
        assertThat(ControlTotals.expected().expectRead(s -> 1).expectRead(s -> 2).getExpected()).hasSize(1);
    }

    /** Reads 1..n, restartable: its position is saved with every commit. */
    static final class Counting implements ItemStreamReader<Long> {

        private final long last;
        private long next = 1;

        Counting(long last) {
            this.last = last;
        }

        @Override
        public void open(ExecutionContext context) {
            next = context.getLong("next", 1L);
        }

        @Override
        public void update(ExecutionContext context) {
            context.putLong("next", next);
        }

        @Override
        public Long read() {
            return next <= last ? next++ : null;
        }
    }
}
