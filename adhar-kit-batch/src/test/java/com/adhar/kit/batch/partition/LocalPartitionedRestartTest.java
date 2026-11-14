package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.partitioner.SqlKeyRangePartitioner;
import com.adhar.kit.batch.reader.KeysetPagingItemReaderBuilder;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.FailureInjector;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A partitioned restart (KIT-1.6) on the real auto-configuration: four SQL key ranges on two
 * threads, one partition fails mid-range, and the restart re-runs ONLY that partition, from its last
 * commit and over the range it recorded, writing every row exactly once.
 */
class LocalPartitionedRestartTest {

    private static final String JOB = "partitionedImport";

    @Test
    @DisplayName("a restart re-runs only the unfinished partition, from its last commit, and writes nothing twice")
    void restartRunsOnlyTheUnfinishedPartition() throws Exception {
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(TestJobs.class, PartitionedJob.class)
                .properties(BatchTestApplication.h2()).run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TestJobs.seed(jdbc, 40);
            context.getBean(FailureInjector.class).failOn(27, 1);
            JobOperator operator = context.getBean(JobOperator.class);
            Job job = context.getBean(JOB, Job.class);
            JobParameters parameters = new JobParametersBuilder().addString("businessDate", "2026-11-03").toJobParameters();

            JobExecution failed = operator.start(job, parameters);
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(workers(failed)).containsEntry("worker:partition2", BatchStatus.FAILED)
                    .containsEntry("worker:partition0", BatchStatus.COMPLETED).hasSize(4);
            // Partition 2 (ids 21..30) committed 21..25 before it failed on 27; the rest rolled back.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(35);
            assertThat(failed.getStepExecutions().stream().filter(step -> step.getStepName().equals("manager"))
                    .findFirst().orElseThrow().getExecutionContext().containsKey(PartitionPlanSplitter.PLAN))
                    .as("split by the kit: the ranges are planned once").isTrue();

            // Rows that arrive after the first attempt are not this instance's input: the restart
            // reuses the recorded ranges instead of partitioning again.
            jdbc.update("INSERT INTO SOURCE_ITEMS (ID) VALUES (41)");

            JobExecution restarted = operator.start(job, parameters);

            assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(workers(restarted)).containsOnlyKeys("worker:partition2");
            StepExecution resumed = restarted.getStepExecutions().stream()
                    .filter(step -> step.getStepName().equals("worker:partition2")).findFirst().orElseThrow();
            assertThat(resumed.getWriteCount()).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(40);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class)).isEqualTo(40);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS WHERE ID = 41", Integer.class)).isZero();
        }
    }

    private static Map<String, BatchStatus> workers(JobExecution execution) {
        Map<String, BatchStatus> workers = new java.util.TreeMap<>();
        execution.getStepExecutions().stream().filter(step -> step.getStepName().startsWith("worker:"))
                .forEach(step -> workers.put(step.getStepName(), step.getStatus()));
        return workers;
    }

    /** SOURCE_ITEMS → TARGET_ITEMS over four key ranges, five rows per chunk. */
    @Configuration(proxyBeanMethods = false)
    static class PartitionedJob {

        @Bean
        @StepScope
        JdbcPagingItemReader<Long> rangeReader(DataSource dataSource,
                                               @Value("#{stepExecutionContext['minKey']}") Long minKey,
                                               @Value("#{stepExecutionContext['maxKey']}") Long maxKey) {
            return KeysetPagingItemReaderBuilder.of(dataSource, (rs, i) -> rs.getLong("ID"))
                    .from("SOURCE_ITEMS").key("ID").where("ID BETWEEN :minKey AND :maxKey")
                    .parameter("minKey", minKey).parameter("maxKey", maxKey).pageSize(5).name("range").build();
        }

        @Bean
        Step worker(AdharSteps steps, JdbcPagingItemReader<Long> rangeReader, JdbcTemplate jdbc, FailureInjector failures) {
            ItemWriter<Long> writer = chunk -> {
                for (Long id : chunk) {
                    jdbc.update("INSERT INTO TARGET_ITEMS (ID) VALUES (?)", id);
                    failures.check(id);
                }
            };
            return steps.<Long, Long>chunk("worker", 5).reader(rangeReader).writer(writer).build();
        }

        @Bean
        Job partitionedImport(JobRepository jobRepository, DataSource dataSource, Step worker) {
            Step manager = LocalPartitioning.of(4, 2).step("manager", jobRepository,
                    SqlKeyRangePartitioner.of(dataSource, "SOURCE_ITEMS", "ID").build(), worker);
            return new JobBuilder(JOB, jobRepository).start(manager).build();
        }
    }
}
