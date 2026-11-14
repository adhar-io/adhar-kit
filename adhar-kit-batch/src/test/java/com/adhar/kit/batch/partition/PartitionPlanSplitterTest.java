package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.partitioner.SqlKeyRangePartitioner;
import com.adhar.kit.batch.reader.KeysetPagingItemReaderBuilder;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.FailureInjector;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PartitionPlanSplitter} on the real auto-configuration (H2): a manager stopped part-way
 * through recording its partitions, exactly between the two statements Spring Batch saves a
 * partition with (the step execution with an empty context, then the range), and the restart that
 * follows. Before the plan, each of these restarts COMPLETED with rows missing (or duplicated).
 * The same on PostgreSQL across contexts: {@code RemotePartitioningTestContainerTest#halfWrittenSplit}.
 */
class PartitionPlanSplitterTest {

    private static final String JOB = "plannedImport";
    private static final int ROWS = 40;

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private JobOperator operator;
    private JobRepository jobRepository;
    private Job job;
    private final JobParameters parameters = new JobParametersBuilder().addString("businessDate", "2026-11-14")
            .toJobParameters();

    @BeforeEach
    void start() {
        context = BatchTestApplication.builder(TestJobs.class, PlannedJob.class).properties(BatchTestApplication.h2())
                .run();
        jdbc = context.getBean(JdbcTemplate.class);
        TestJobs.seed(jdbc, ROWS);
        operator = context.getBean(JobOperator.class);
        jobRepository = context.getBean(JobRepository.class);
        job = context.getBean(JOB, Job.class);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    @DisplayName("a first split cut between a partition and its range: the restart runs every planned range, every row once")
    void firstSplitCutShort() throws Exception {
        // partition0 is recorded with its range, partition1 without it, partition2 and 3 not at all.
        context.getBean(Cut.class).atRangeWrite(2);

        JobExecution first = operator.start(job, parameters);

        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(recorded(first)).containsOnlyKeys("worker:partition0", "worker:partition1");
        assertThat(contextOf(first, "worker:partition1").isEmpty()).as("the range was never written").isTrue();
        assertThat(rows()).isZero();
        assertThat(context.getBean(Cut.class).planWasDurable()).as("the plan is saved before any partition").isTrue();

        // The runner recovers an execution that ended with a step still running (RecoveryPolicy).
        operator.recover(jobRepository.getJobExecution(first.getId()));
        jdbc.update("INSERT INTO SOURCE_ITEMS (ID) VALUES (41)");
        JobExecution restarted = operator.start(job, parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recorded(restarted)).hasSize(4).allSatisfy((name, status) -> assertThat(status)
                .isEqualTo(BatchStatus.COMPLETED));
        assertExactlyOnce();
        assertThat(context.getBean(CountingPartitioner.class).calls).as("a restart never partitions again").hasValue(1);
    }

    @Test
    @DisplayName("a restart cut while it copies a partition's last commit: the next one resumes from that commit, not from the start")
    void restartCopyCutShort() throws Exception {
        context.getBean(FailureInjector.class).failOn(27, 1);
        JobExecution first = operator.start(job, parameters);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(rows()).as("partition2 committed 21..25 before it failed on 27").isEqualTo(35);

        // Only partition2 is restarted, and its copy of the last commit is never written.
        context.getBean(Cut.class).atRangeWrite(1);
        JobExecution second = operator.start(job, parameters);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(contextOf(second, "worker:partition2").isEmpty()).isTrue();
        operator.recover(jobRepository.getJobExecution(second.getId()));

        JobExecution third = operator.start(job, parameters);

        assertThat(third.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recorded(third)).containsOnlyKeys("worker:partition2");
        assertThat(stepOf(third, "worker:partition2").getWriteCount()).as("26..30 only").isEqualTo(5);
        assertExactlyOnce();
    }

    @Test
    @DisplayName("a restart cut while the manager copies its own context: the plan comes from the manager's last record that has it")
    void managerCopyCutShort() throws Exception {
        context.getBean(FailureInjector.class).failOn(27, 1);
        JobExecution first = operator.start(job, parameters);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        // A second attempt that died after Spring Batch saved its manager step with an empty context.
        JobExecution second = jobRepository.createJobExecution(first.getJobInstance(), parameters,
                new ExecutionContext());
        StepExecution manager = jobRepository.createStepExecution("manager", second);
        manager.setStatus(BatchStatus.FAILED);
        manager.setEndTime(LocalDateTime.now());
        jobRepository.update(manager);
        second.setStatus(BatchStatus.FAILED);
        second.setEndTime(LocalDateTime.now());
        jobRepository.update(second);

        JobExecution third = operator.start(job, parameters);

        assertThat(third.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recorded(third)).containsOnlyKeys("worker:partition2");
        assertExactlyOnce();
        assertThat(context.getBean(CountingPartitioner.class).calls).as("the plan was found, not made again").hasValue(1);
        assertThat(stepOf(third, "manager").getExecutionContext().containsKey(PartitionPlanSplitter.PLAN))
                .as("and recorded on the live attempt").isTrue();
    }

    @Test
    @DisplayName("an uninterrupted partitioned run records its plan and splits exactly as Spring Batch does")
    void uninterrupted() throws Exception {
        JobExecution execution = operator.start(job, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertExactlyOnce();
        Object plan = stepOf(execution, "manager").getExecutionContext().get(PartitionPlanSplitter.PLAN);
        assertThat(plan).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsOnlyKeys("partition0", "partition1", "partition2", "partition3");
        assertThat(String.valueOf(contextOf(execution, "worker:partition0").get(SqlKeyRangePartitioner.MIN_KEY)))
                .isEqualTo("1");
        assertThat(stepOf(execution, "manager").getExecutionContext().containsKey(PartitionPlanSplitter.GRID_SIZE))
                .as("still a partition manager to the termination summary").isTrue();
    }

    private void assertExactlyOnce() {
        assertThat(rows()).isEqualTo(ROWS);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class)).isEqualTo(ROWS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS WHERE ID = 41", Integer.class))
                .as("rows that arrive later are not this instance's input").isZero();
    }

    private int rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class);
    }

    private Map<String, BatchStatus> recorded(JobExecution execution) {
        Map<String, BatchStatus> partitions = new TreeMap<>();
        jobRepository.getJobExecution(execution.getId()).getStepExecutions().stream()
                .filter(step -> step.getStepName().startsWith("worker:"))
                .forEach(step -> partitions.put(step.getStepName(), step.getStatus()));
        return partitions;
    }

    private StepExecution stepOf(JobExecution execution, String name) {
        return jobRepository.getJobExecution(execution.getId()).getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(name)).findFirst().orElseThrow();
    }

    private ExecutionContext contextOf(JobExecution execution, String name) {
        return stepOf(execution, name).getExecutionContext();
    }

    /**
     * Stops the splitter's JobRepository right before the Nth partition context write of one attempt,
     * the state a manager hard-killed there leaves (the write is the second of the two statements).
     */
    static class Cut {

        private final AtomicInteger remaining = new AtomicInteger();
        private volatile Boolean planDurable;

        void atRangeWrite(int nth) {
            remaining.set(nth);
            planDurable = null;
        }

        /** Whether the manager's plan was in the database when the cut came: a real kill keeps nothing else. */
        Boolean planWasDurable() {
            return planDurable;
        }

        JobRepository around(JobRepository repository) {
            return (JobRepository) Proxy.newProxyInstance(JobRepository.class.getClassLoader(),
                    new Class<?>[]{JobRepository.class}, (proxy, method, args) -> {
                        if (method.getName().equals("updateExecutionContext") && args[0] instanceof StepExecution step
                                && step.getStepName().startsWith("worker:") && remaining.get() > 0
                                && remaining.decrementAndGet() == 0) {
                            planDurable = repository.getJobExecution(step.getJobExecutionId()).getStepExecutions().stream()
                                    .filter(recorded -> recorded.getStepName().equals("manager")).findFirst()
                                    .map(manager -> manager.getExecutionContext().containsKey(PartitionPlanSplitter.PLAN))
                                    .orElse(false);
                            throw new IllegalStateException("manager killed before the context of " + step.getStepName()
                                    + " was written");
                        }
                        try {
                            return method.invoke(repository, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    });
        }
    }

    /** The partitioner, counting how often it is asked. */
    static class CountingPartitioner implements Partitioner {

        final AtomicInteger calls = new AtomicInteger();
        private final Partitioner delegate;

        CountingPartitioner(Partitioner delegate) {
            this.delegate = delegate;
        }

        @Override
        public Map<String, ExecutionContext> partition(int gridSize) {
            calls.incrementAndGet();
            return delegate.partition(gridSize);
        }
    }

    /** SOURCE_ITEMS → TARGET_ITEMS over four key ranges on two threads, LocalPartitioning's step with a cuttable splitter. */
    @Configuration(proxyBeanMethods = false)
    static class PlannedJob {

        @Bean
        Cut cut() {
            return new Cut();
        }

        @Bean
        CountingPartitioner countingPartitioner(DataSource dataSource) {
            return new CountingPartitioner(SqlKeyRangePartitioner.of(dataSource, "SOURCE_ITEMS", "ID").build());
        }

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
        Job plannedImport(JobRepository jobRepository, CountingPartitioner partitioner, Step worker, Cut cut) {
            Step manager = new StepBuilder("manager", jobRepository)
                    .partitioner(worker.getName(), partitioner)
                    .splitter(new PartitionPlanSplitter(cut.around(jobRepository), worker.getName(), partitioner, false))
                    .step(worker)
                    .partitionHandler(LocalPartitioning.of(4, 2).handler(worker))
                    .build();
            return new JobBuilder(JOB, jobRepository).start(manager).build();
        }
    }
}
