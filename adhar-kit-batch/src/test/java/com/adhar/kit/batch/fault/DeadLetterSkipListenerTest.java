package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Skip → dead letter, and the skip limit → exit 3, in a real step on a real JobRepository (H2,
 * the kit's tables from the test schema). A dead letter commits with the chunk that skipped it.
 */
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class})
class DeadLetterSkipListenerTest {

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
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ADHAR_BATCH_DEAD_LETTER");
    }

    private static ItemProcessor<String, String> rejecting(String... bad) {
        List<String> rejected = List.of(bad);
        return item -> {
            if (rejected.contains(item)) {
                throw new IllegalArgumentException("invalid record " + item);
            }
            return item;
        };
    }

    private JobExecution run(String jobName, ItemReader<String> reader, ItemProcessor<String, String> processor,
                             FaultTolerance faultTolerance) throws Exception {
        var step = steps.<String, String>chunk("dlqStep", 2).reader(reader).processor(processor)
                .writer(chunk -> { }).faultTolerance(faultTolerance).build();
        var job = new JobBuilder(jobName, jobRepository).start(step).build();
        return jobOperator.start(job, new JobParametersBuilder()
                .addLocalDate("businessDate", LocalDate.of(2026, 11, 3))
                .addString("run", UUID.randomUUID().toString()).toJobParameters());
    }

    private static StepExecution onlyStep(JobExecution execution) {
        return execution.getStepExecutions().iterator().next();
    }

    @Test
    @DisplayName("a skipped item lands in the dead-letter table with its JSON, error, step, execution and business date")
    void skipToDeadLetter() throws Exception {
        JobExecution execution = run("dlqJob", new ListItemReader<>(List.of("a", "bad", "c")), rejecting("bad"),
                FaultTolerance.none().skip(IllegalArgumentException.class, 5).deadLetter());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(ExitCodes.of(execution)).isZero();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ADHAR_BATCH_DEAD_LETTER");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("JOB_EXECUTION_ID")).isEqualTo(execution.getId());
            assertThat(row.get("JOB_NAME")).isEqualTo("dlqJob");
            assertThat(row.get("STEP_NAME")).isEqualTo("dlqStep");
            assertThat(row.get("BUSINESS_DATE").toString()).isEqualTo("2026-11-03");
            assertThat(row.get("PHASE")).isEqualTo("process");
            assertThat(row.get("ITEM_TYPE")).isEqualTo("java.lang.String");
            assertThat(row.get("ITEM")).isEqualTo("\"bad\"");
            assertThat(row.get("ERROR_CLASS")).isEqualTo(IllegalArgumentException.class.getName());
            assertThat(row.get("ERROR_MESSAGE")).isEqualTo("invalid record bad");
        });
        StepExecution step = onlyStep(execution);
        assertThat(step.getExecutionContext().getLong(DeadLetterSkipListener.COUNT_KEY)).isEqualTo(1);
        assertThat(jobRepository.getStepExecution(execution.getId(), step.getId()).getExecutionContext()
                .getLong(DeadLetterSkipListener.COUNT_KEY)).as("persisted with the step").isEqualTo(1);
        assertThat(meterRegistry.get("adhar.batch.items.dead.lettered").tag("job", "dlqJob").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("a read failure is dead-lettered without an item")
    void readSkip() throws Exception {
        Iterator<String> items = List.of("a", "boom", "c").iterator();
        ItemReader<String> reader = () -> {
            if (!items.hasNext()) {
                return null;
            }
            String item = items.next();
            if (item.equals("boom")) {
                throw new IllegalArgumentException("unparseable line 2");
            }
            return item;
        };

        run("dlqReadJob", reader, item -> item, FaultTolerance.none().skip(IllegalArgumentException.class, 5)
                .deadLetter());

        assertThat(jdbc.queryForList("SELECT PHASE, ITEM_TYPE, ITEM FROM ADHAR_BATCH_DEAD_LETTER"))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("PHASE")).isEqualTo("read");
                    assertThat(row.get("ITEM_TYPE")).isNull();
                    assertThat(row.get("ITEM")).isNull();
                });
    }

    @Test
    @DisplayName("going over the skip limit fails the run with exit code 3: retrying the same data cannot help")
    void skipLimitIsNotRetryable() throws Exception {
        JobExecution execution = run("dlqLimitJob", new ListItemReader<>(List.of("a", "x", "y", "z")),
                rejecting("x", "y", "z"), FaultTolerance.none().skip(IllegalArgumentException.class, 1).deadLetter());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(ExitCodes.of(execution)).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
    }

    @Test
    @DisplayName("a non-retryable exception is exit 3; any other failure is exit 5")
    void classification() throws Exception {
        JobExecution nonRetryable = run("dlqNonRetryableJob", new ListItemReader<>(List.of("a")), item -> {
            throw new NonRetryableBatchException("control totals differ");
        }, FaultTolerance.none());
        JobExecution other = run("dlqOtherJob", new ListItemReader<>(List.of("a")), item -> {
            throw new IllegalStateException("downstream unavailable");
        }, FaultTolerance.none());

        assertThat(ExitCodes.of(nonRetryable)).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(ExitCodes.of(other)).isEqualTo(ExitCodes.FAILED_RETRYABLE);
    }

    @Test
    @DisplayName("deadLetter() without a skip is a mistake the build reports")
    void deadLetterNeedsASkip() {
        assertThatThrownBy(() -> steps.<String, String>chunk("noSkip").reader(new ListItemReader<>(List.of("a")))
                .writer(chunk -> { }).faultTolerance(FaultTolerance.transientInfrastructure().deadLetter()).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("skips nothing");
        assertThat(FaultTolerance.none().isDeadLetter()).isFalse();
        assertThat(FaultTolerance.none().skip(IllegalArgumentException.class, 1).deadLetter().maxAttempts(2)
                .isDeadLetter()).as("kept through further changes").isTrue();
    }
}
