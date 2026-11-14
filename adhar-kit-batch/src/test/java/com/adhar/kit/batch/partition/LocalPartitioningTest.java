package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.partitioner.AdharRangePartitioner;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LocalPartitioning}: the preset runs every partition, on named threads, never more than
 * {@code threads} at once.
 */
class LocalPartitioningTest {

    @Test
    @DisplayName("grid size and threads are validated")
    void validation() {
        assertThatThrownBy(() -> LocalPartitioning.of(0, 1)).hasMessageContaining("gridSize");
        assertThatThrownBy(() -> LocalPartitioning.of(4, 0)).hasMessageContaining("threads");
        assertThatThrownBy(() -> LocalPartitioning.of(2, 3)).hasMessageContaining("threads");
        assertThat(LocalPartitioning.of(8, 4)).isEqualTo(new LocalPartitioning(8, 4));
    }

    @Test
    @DisplayName("every partition runs, at most `threads` at once, on the kit's worker threads")
    void runsEveryPartitionWithBoundedConcurrency() throws Exception {
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(TestJobs.class)
                .properties(BatchTestApplication.h2()).run()) {
            JobRepository jobRepository = context.getBean(JobRepository.class);
            AtomicInteger running = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            Set<String> threads = ConcurrentHashMap.newKeySet();
            Step worker = context.getBean(AdharSteps.class).tasklet("worker", (contribution, chunk) -> {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                threads.add(Thread.currentThread().getName());
                Thread.sleep(150);
                running.decrementAndGet();
                return RepeatStatus.FINISHED;
            });
            LocalPartitioning partitioning = LocalPartitioning.of(6, 2);
            Step manager = partitioning.step("manager", jobRepository, new AdharRangePartitioner(1, 60), worker);

            JobExecution execution = context.getBean(JobOperator.class).start(
                    new JobBuilder("localPartitioned", jobRepository).start(manager).build(),
                    new JobParametersBuilder().addString("run", "1").toJobParameters());

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                    .contains("manager", "worker:partition0", "worker:partition5").hasSize(7);
            assertThat(peak.get()).isEqualTo(2);
            assertThat(threads).allSatisfy(name -> assertThat(name).startsWith(LocalPartitioning.THREAD_PREFIX));
            assertThat(ReflectionTestUtils.getField(partitioning.handler(worker), "gridSize")).isEqualTo(6);
        }
    }
}
