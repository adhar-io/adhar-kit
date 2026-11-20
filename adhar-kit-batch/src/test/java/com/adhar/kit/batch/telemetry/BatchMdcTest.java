package com.adhar.kit.batch.telemetry;

import com.adhar.kit.batch.partition.LocalPartitioning;
import com.adhar.kit.batch.partitioner.AdharRangePartitioner;
import com.adhar.kit.batch.runtime.AdharBatchRunner;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BatchMdc} on the real auto-configuration: the run is in the MDC of the job's thread and of
 * every partition thread while it runs, and the threads' MDC is restored afterwards.
 */
class BatchMdcTest {

    @Test
    @DisplayName("job, instance, execution, business date and Kubernetes Job are the job-level keys")
    void jobContext() {
        JobExecution execution = new JobExecution(97L, new JobInstance(42L, "importJob"),
                new JobParametersBuilder().addLocalDate("businessDate", LocalDate.of(2026, 11, 3))
                        .addString(AdharBatchRunner.K8S_JOB, "importer-29123", false).toJobParameters());

        assertThat(BatchMdc.jobContext(execution)).containsExactly(
                Map.entry(BatchMdc.JOB, "importJob"), Map.entry(BatchMdc.INSTANCE, "42"),
                Map.entry(BatchMdc.EXECUTION, "97"), Map.entry(BatchMdc.BUSINESS_DATE, "2026-11-03"),
                Map.entry(BatchMdc.K8S_JOB, "importer-29123"));
    }

    @Test
    @DisplayName("an in-service run adds its name and trigger; a run with no trigger key names what started it")
    void inServiceRunKeys() {
        JobExecution quartz = new JobExecution(5L, new JobInstance(4L, "importJob"), new JobParametersBuilder()
                .addString(BatchScheduler.RUN_PARAMETER, "import-job-09932cb3", false)
                .addString(BatchScheduler.TRIGGER_KEY_PARAMETER, "adhar-batch-config.import-job.nightly", false)
                .addString(BatchScheduler.TRIGGER_PARAMETER, BatchScheduler.EMBEDDED_TRIGGER, false)
                .toJobParameters());
        JobExecution simple = new JobExecution(6L, new JobInstance(4L, "importJob"), new JobParametersBuilder()
                .addString(BatchScheduler.TRIGGER_PARAMETER, BatchScheduler.EMBEDDED_TRIGGER, false).toJobParameters());

        assertThat(BatchMdc.jobContext(quartz)).containsEntry(BatchMdc.RUN, "import-job-09932cb3")
                .containsEntry(BatchMdc.TRIGGER, "adhar-batch-config.import-job.nightly");
        assertThat(BatchMdc.jobContext(simple)).doesNotContainKey(BatchMdc.RUN)
                .containsEntry(BatchMdc.TRIGGER, BatchScheduler.EMBEDDED_TRIGGER);
    }

    @Test
    @DisplayName("every step thread, partition threads included, logs with the run's keys; the MDC is restored after")
    void stepAndPartitionThreadsCarryTheKeys() throws Exception {
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(TestJobs.class)
                .properties(BatchTestApplication.h2()).run()) {
            JobRepository jobRepository = context.getBean(JobRepository.class);
            AdharSteps steps = context.getBean(AdharSteps.class);
            Map<String, Map<String, String>> seen = new ConcurrentHashMap<>();
            Step first = steps.tasklet("first", (contribution, chunk) -> {
                seen.put("first", MDC.getCopyOfContextMap());
                return RepeatStatus.FINISHED;
            });
            Step worker = steps.tasklet("worker", (contribution, chunk) -> {
                seen.put(chunk.getStepContext().getStepName(), MDC.getCopyOfContextMap());
                return RepeatStatus.FINISHED;
            });
            Step partitioned = LocalPartitioning.of(2, 2).step("manager", jobRepository, new AdharRangePartitioner(1, 2), worker);
            MDC.put("caller", "kept");

            JobExecution execution = context.getBean(JobOperator.class).start(
                    new JobBuilder("mdcJob", jobRepository).start(first).next(partitioned).build(),
                    new JobParametersBuilder().addLocalDate("businessDate", LocalDate.of(2026, 11, 3)).toJobParameters());

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(seen.get("first")).containsEntry(BatchMdc.JOB, "mdcJob").containsEntry(BatchMdc.STEP, "first")
                    .containsEntry(BatchMdc.EXECUTION, String.valueOf(execution.getId()))
                    .containsEntry(BatchMdc.BUSINESS_DATE, "2026-11-03").containsEntry("caller", "kept")
                    .doesNotContainKey(BatchMdc.PARTITION);
            assertThat(seen.get("worker:partition1")).containsEntry(BatchMdc.JOB, "mdcJob")
                    .containsEntry(BatchMdc.STEP, "worker").containsEntry(BatchMdc.PARTITION, "partition1")
                    .containsEntry(BatchMdc.INSTANCE, String.valueOf(execution.getJobInstance().getInstanceId()));
            assertThat(MDC.getCopyOfContextMap()).containsExactly(Map.entry("caller", "kept"));
        } finally {
            MDC.clear();
        }
    }
}
