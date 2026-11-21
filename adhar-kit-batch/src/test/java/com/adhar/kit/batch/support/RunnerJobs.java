package com.adhar.kit.batch.support;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.fault.NonRetryableBatchException;
import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.step.ControlTotals;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The jobs of the runner's tests, declared the platform way ({@link AdharBatchJob}).
 *
 * <ul>
 *   <li>{@value #REPORT_JOB}: a tasklet recording its business date in {@code SLOT_RUNS};
 *       {@link #FAILURE} makes it fail retryably ({@code retryable}) or not ({@code fatal}).</li>
 *   <li>{@value #SLOW_CHUNK_JOB}: 50 items in chunks of 5, each chunk taking {@link #CHUNK_MILLIS}:
 *       long enough to be stopped at a chunk boundary.</li>
 *   <li>{@value #INCREMENTED_JOB}: carries an incrementer it is not allowed.</li>
 *   <li>{@value #TOTALS_JOB}: reads 20 items and checks its control totals: balanced, and read
 *       equal to its non-identifying {@code expected} parameter.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class RunnerJobs {

    public static final String REPORT_JOB = "reportJob";
    public static final String SLOW_CHUNK_JOB = "slowChunkJob";
    public static final String INCREMENTED_JOB = "incrementedJob";
    public static final String TOTALS_JOB = "totalsJob";

    /** {@code ""}, {@code retryable} or {@code fatal}. */
    public static final AtomicReference<String> FAILURE = new AtomicReference<>("");

    /** How long each chunk of {@value #SLOW_CHUNK_JOB} takes. */
    public static final AtomicLong CHUNK_MILLIS = new AtomicLong(100);

    @Bean
    @AdharBatchJob(description = "Daily report", parameters = {
            @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE),
            @JobParam(name = "region", defaultValue = "sg", allowed = {"sg", "my"}),
            @JobParam(name = "note", identifying = false, sensitive = true)})
    Job reportJob(JobRepository jobRepository, AdharSteps steps, JdbcTemplate jdbc) {
        return new JobBuilder(REPORT_JOB, jobRepository).start(steps.tasklet("reportStep", (contribution, context) -> {
            switch (FAILURE.get()) {
                case "retryable" -> throw new IllegalStateException("downstream unavailable");
                case "fatal" -> throw new NonRetryableBatchException("control totals differ");
                default -> { }
            }
            Object date = context.getStepContext().getJobParameters().get("businessDate");
            jdbc.update("INSERT INTO SLOT_RUNS (RUNNER, SCHEDULED_TIME) VALUES (?, ?)", REPORT_JOB, String.valueOf(date));
            return RepeatStatus.FINISHED;
        })).build();
    }

    @Bean
    @AdharBatchJob(parameters = @JobParam(name = "run", required = true))
    Job slowChunkJob(JobRepository jobRepository, AdharSteps steps) {
        var step = steps.<Long, Long>chunk("slowChunkStep", 5)
                .reader(new CountingReader(50))
                .writer(chunk -> Thread.sleep(CHUNK_MILLIS.get()))
                .build();
        return new JobBuilder(SLOW_CHUNK_JOB, jobRepository).start(step).build();
    }

    @Bean
    @AdharBatchJob
    Job incrementedJob(JobRepository jobRepository, AdharSteps steps) {
        return new JobBuilder(INCREMENTED_JOB, jobRepository).incrementer(new RunIdIncrementer())
                .start(steps.tasklet("noop", (contribution, context) -> RepeatStatus.FINISHED)).build();
    }

    @Bean
    @AdharBatchJob(parameters = {@JobParam(name = "run", required = true),
            @JobParam(name = "expected", type = ParamType.LONG, identifying = false, required = true)})
    Job totalsJob(JobRepository jobRepository, AdharSteps steps) {
        var step = steps.<Long, Long>chunk("totalsStep", 5)
                .reader(new CountingReader(20))
                .writer(chunk -> { })
                .controlTotals(ControlTotals.balanced()
                        .expectRead(execution -> execution.getJobParameters().getLong("expected")))
                .build();
        return new JobBuilder(TOTALS_JOB, jobRepository).start(step).build();
    }

    @Bean
    ObserverRelay observerRelay() {
        return new ObserverRelay();
    }

    /**
     * Forwards the kit's job callbacks to a runner built by a test (the kit's listeners take their
     * observers when they are created, before a test builds its runner).
     */
    public static class ObserverRelay implements BatchRunObserver {

        public volatile BatchRunObserver delegate;

        @Override
        public void jobStarted(JobExecution execution) {
            if (delegate != null) {
                delegate.jobStarted(execution);
            }
        }

        @Override
        public void jobFinished(JobExecution execution) {
            if (delegate != null) {
                delegate.jobFinished(execution);
            }
        }

        @Override
        public void chunkCommitted(StepExecution execution, long items) {
            if (delegate != null) {
                delegate.chunkCommitted(execution, items);
            }
        }
    }

    /** Reads 1..n, restartable: its position is saved with every chunk. */
    public static class CountingReader implements ItemStreamReader<Long> {

        private final long last;
        private long next = 1;

        CountingReader(long last) {
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

    /**
     * @return the runner tests' extra tables
     */
    public static List<String> tables() {
        return List.of("SLOT_RUNS", "ADHAR_BATCH_EXECUTION_LEASE", "ADHAR_BATCH_DEAD_LETTER", "OPS_LEDGER");
    }
}
