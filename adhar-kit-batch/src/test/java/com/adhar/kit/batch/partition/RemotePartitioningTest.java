package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.runtime.BatchRole;
import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.runtime.RunContext;
import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.runtime.RunTrigger;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PartitionedJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

/**
 * Remote partitioning without a database server (design 0025 §5.9, §5.10): a manager context and a
 * worker context, each a real Spring Boot application with the kit's auto-configuration, on one
 * in-memory H2 database, talking only through the JobRepository and the {@code jdbc} partition
 * channel in it. What {@link RemotePartitioning#manager} builds in each role, the manager's clear
 * and fence ({@link ManagerPartitionGuard}) and the channel's groups ({@link JdbcPartitionTransport}).
 * PostgreSQL's claim ({@code DELETE ... RETURNING ... SKIP LOCKED}), two workers and the zombie
 * fence of F19 are {@link RemotePartitioningTestContainerTest}'s.
 */
class RemotePartitioningTest {

    private static final int ROWS = 40;
    private static final int GRID = 4;

    private final String url = "jdbc:h2:mem:partition-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final JobParameters parameters = new JobParametersBuilder().addString("run", "h2").toJobParameters();

    @AfterEach
    void closeContexts() {
        contexts.forEach(ConfigurableApplicationContext::close);
    }

    /** The first context of a test creates the schema; the others find it. */
    private ConfigurableApplicationContext context(BatchRole role, Map<String, Object> extra) {
        Map<String, Object> properties = BatchTestApplication.h2();
        properties.put("spring.datasource.url", url);
        properties.put("spring.sql.init.schema-locations", "classpath:adhar-batch-test/h2-schema.sql,"
                + "classpath:org/springframework/integration/jdbc/schema-h2.sql");
        if (!contexts.isEmpty()) {
            properties.put("spring.sql.init.mode", "never");
            properties.put("spring.batch.jdbc.initialize-schema", "never");
        }
        if (role != null) {
            properties.put("adhar.batch.role", role.label());
        }
        properties.put("adhar.batch.partition.grid-size", String.valueOf(GRID));
        properties.put("adhar.batch.partition.poll-interval", "100ms");
        properties.putAll(extra);
        ConfigurableApplicationContext context = BatchTestApplication.builder(PartitionedJobs.class)
                .properties(properties).run();
        contexts.add(context);
        return context;
    }

    private CompletableFuture<JobExecution> start(ConfigurableApplicationContext manager) {
        JobOperator operator = manager.getBean(JobOperator.class);
        Job job = manager.getBean(PartitionedJobs.JOB, Job.class);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operator.start(job, parameters);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private CompletableFuture<RunOutcome> serve(ConfigurableApplicationContext worker) {
        Job job = worker.getBean(PartitionedJobs.JOB, Job.class);
        RunContext context = new RunContext(PartitionedJobs.JOB, "importer", BatchRole.WORKER, RunTrigger.MANUAL, "",
                "", "", "importer-worker", "", "", Optional.empty(), ZoneOffset.UTC, Optional.empty(), "", 1,
                OptionalInt.empty(), "test");
        AtomicBoolean never = new AtomicBoolean();
        return CompletableFuture.supplyAsync(() -> worker.getBean(PartitionWorker.class)
                .consume(job, parameters, context, never::get));
    }

    private static <T> T within(CompletableFuture<T> future, Duration timeout) throws Exception {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Map<String, BatchStatus> partitions(JobExecution execution) {
        Map<String, BatchStatus> partitions = new TreeMap<>();
        execution.getStepExecutions().stream().filter(step -> step.getStepName().startsWith(PartitionedJobs.WORKER_STEP + ":"))
                .forEach(step -> partitions.put(step.getStepName(), step.getStatus()));
        return partitions;
    }

    private static int targetRows(ConfigurableApplicationContext context, String column) {
        return context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(" + column + ") FROM TARGET_ITEMS",
                Integer.class);
    }

    @Test
    @DisplayName("a manager sends one request per partition through the channel and a worker runs them all, once")
    void managerAndWorker() throws Exception {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER, Map.of());
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        ConfigurableApplicationContext worker = context(BatchRole.WORKER, Map.of());

        // The role switch: the manager has no worker step, a worker no manager machinery.
        assertThat(manager.containsBean(PartitionedJobs.WORKER_STEP)).isFalse();
        assertThat(worker.containsBean(PartitionedJobs.WORKER_STEP)).isTrue();
        assertThat(manager.getBean(RemotePartitioning.class).role()).isEqualTo(BatchRole.MANAGER);
        assertThat(worker.getBean(RemotePartitioning.class).role()).isEqualTo(BatchRole.WORKER);

        CompletableFuture<RunOutcome> served = serve(worker);
        JobExecution execution = within(start(manager), Duration.ofMinutes(2));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(partitions(execution)).hasSize(GRID).allSatisfy((name, status) ->
                assertThat(status).isEqualTo(BatchStatus.COMPLETED));
        assertThat(targetRows(manager, "*")).isEqualTo(ROWS);
        assertThat(targetRows(manager, "DISTINCT ID")).isEqualTo(ROWS);
        RunOutcome outcome = within(served, Duration.ofSeconds(30));
        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.COMPLETED);
        assertThat(outcome.message()).as("the worker took the partitions").doesNotContain("served 0 partition");
        assertThat(manager.getBean(JdbcPartitionTransport.class).pending(JdbcPartitionTransport.group(execution)))
                .isZero();
    }

    @Test
    @DisplayName("F19, F20: a manager that gives up fences its partitions and leaves its requests; the restart clears them first")
    void theManagerFencesAndClears() throws Exception {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER, Map.of("adhar.batch.partition.timeout", "1s"));
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        // Attempt 1 has no workers: the manager gives up after its timeout and fails the run, retryable.
        JobExecution failed = within(start(manager), Duration.ofMinutes(1));
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(ExitCodes.of(failed)).isEqualTo(ExitCodes.FAILED_RETRYABLE);
        JobExecution recorded = manager.getBean(JobRepository.class).getJobExecution(failed.getId());
        assertThat(partitions(recorded)).hasSize(GRID)
                .allSatisfy((name, status) -> assertThat(status).as("fenced by the manager").isEqualTo(BatchStatus.FAILED));
        assertThat(recorded.getStepExecutions().stream().filter(step -> step.getStepName().contains(":")))
                .allSatisfy(step -> {
                    assertThat(step.getExitStatus().getExitDescription()).contains("Fenced by the manager");
                    assertThat(step.getEndTime()).isNotNull();
                });

        // Attempt 1's requests were never claimed: they are still in the run's group, and stale.
        JdbcPartitionTransport transport = manager.getBean(JdbcPartitionTransport.class);
        String group = JdbcPartitionTransport.group(failed);
        assertThat(transport.pending(group)).isEqualTo(GRID);
        assertThat(transport.pending(JdbcPartitionTransport.group(PartitionedJobs.JOB,
                new JobParametersBuilder().addString("run", "another").toJobParameters()))).isZero();
        Object claimed = transport.channel(group).receive(0).getPayload();
        assertThat(claimed).isInstanceOf(StepExecutionRequest.class);
        assertThat(manager.getBean(StaleRequestGuard.class).admit((StepExecutionRequest) claimed).admitted())
                .as("a worker discards a request of a failed attempt").isFalse();
        assertThat(transport.pending(group)).isEqualTo(GRID - 1);

        // Attempt 2: the manager clears the group before it sends its own requests.
        ConfigurableApplicationContext restarted = context(BatchRole.MANAGER, Map.of());
        CompletableFuture<JobExecution> second = start(restarted);
        await().atMost(Duration.ofSeconds(30)).until(() -> transport.pending(group) == GRID);
        CompletableFuture<RunOutcome> served = serve(context(BatchRole.WORKER, Map.of()));
        JobExecution completed = within(second, Duration.ofMinutes(2));

        assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(targetRows(manager, "*")).isEqualTo(ROWS);
        assertThat(targetRows(manager, "DISTINCT ID")).isEqualTo(ROWS);
        assertThat(within(served, Duration.ofSeconds(30)).exitCode()).isZero();
        assertThat(transport.pending(group)).isZero();
    }

    @Test
    @DisplayName("role single runs the same partitions in this process")
    void singleRole() throws Exception {
        ConfigurableApplicationContext single = context(null, Map.of());
        TestJobs.seed(single.getBean(JdbcTemplate.class), ROWS);
        assertThat(single.getBean(RemotePartitioning.class).role()).isEqualTo(BatchRole.SINGLE);

        JobExecution execution = within(start(single), Duration.ofMinutes(2));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(partitions(execution)).hasSize(GRID);
        assertThat(targetRows(single, "DISTINCT ID")).isEqualTo(ROWS);
        assertThat(single.getBean(JdbcPartitionTransport.class).pending(JdbcPartitionTransport.group(execution)))
                .as("nothing travels through the channel").isZero();
    }

    @Test
    @DisplayName("a worker that is asked to run the job refuses: it serves partitions and never runs the manager")
    void aWorkerNeverRunsTheManager() throws Exception {
        ConfigurableApplicationContext worker = context(BatchRole.WORKER, Map.of());
        TestJobs.seed(worker.getBean(JdbcTemplate.class), ROWS);

        JobExecution execution = within(start(worker), Duration.ofMinutes(1));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions()).anySatisfy(failure -> assertThat(failure)
                .hasMessageContaining("Step [" + PartitionedJobs.MANAGER_STEP + "] is the manager of a remote-partitioned job")
                .hasMessageContaining("this pod is a worker"));
        assertThat(targetRows(worker, "*")).isZero();
    }

    @Test
    @DisplayName("the channel carries the requests of known partitions only")
    void theChannelCarriesPartitionRequestsOnly() {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER, Map.of());
        JdbcPartitionTransport transport = manager.getBean(JdbcPartitionTransport.class);

        assertThatThrownBy(() -> transport.requests().send(new org.springframework.messaging.support.GenericMessage<>("hello")))
                .isInstanceOf(org.springframework.messaging.MessagingException.class)
                .hasMessageContaining("StepExecutionRequests only").hasMessageContaining("java.lang.String");
        assertThatThrownBy(() -> transport.requests().send(new org.springframework.messaging.support.GenericMessage<>(
                new StepExecutionRequest(PartitionedJobs.WORKER_STEP, 987_654L))))
                .isInstanceOf(org.springframework.messaging.MessagingException.class)
                .hasMessageContaining("No step execution 987654");
    }

    @Test
    @DisplayName("the jdbc transport is refused on a database that is neither PostgreSQL nor H2, at its first use")
    void anotherDatabaseIsRefused() {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER, Map.of());
        javax.sql.DataSource dataSource = manager.getBean(javax.sql.DataSource.class);
        JobRepository repository = manager.getBean(JobRepository.class);
        JdbcPartitionTransport oracle = new JdbcPartitionTransport(dataSource,
                new com.adhar.kit.batch.repository.BatchRepositoryLayout(false, "Oracle", "", "BATCH_"), repository);
        JdbcPartitionTransport unknown = new JdbcPartitionTransport(dataSource, null, repository);

        assertThatThrownBy(() -> oracle.pending("adhar-batch:j:k")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("needs the JobRepository on PostgreSQL").hasMessageContaining("'Oracle'");
        assertThatThrownBy(() -> unknown.clear("adhar-batch:j:k")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was ''");
    }

    @Test
    @DisplayName("a manager without the transport, and role single without the worker step, fail naming what is missing")
    void whatAManagerStepNeeds() {
        StaticListableBeanFactory none = new StaticListableBeanFactory();
        JobRepository repository = mock(JobRepository.class);
        BatchProperties.Partition settings = new BatchProperties().getPartition();
        RemotePartitioning manager = new RemotePartitioning(repository, none.getBeanProvider(JdbcPartitionTransport.class),
                settings, BatchRole.MANAGER, none, none.getBeanProvider(KubernetesOwnerLookup.class));
        RemotePartitioning single = new RemotePartitioning(repository, none.getBeanProvider(JdbcPartitionTransport.class),
                settings, BatchRole.SINGLE, none, none.getBeanProvider(KubernetesOwnerLookup.class));

        assertThatThrownBy(() -> manager.manager("importManager", "importStep", gridSize -> Map.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Role manager needs the jdbc partition transport");
        assertThatThrownBy(() -> single.manager("importManager", "importStep", gridSize -> Map.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no step named [importStep]")
                .hasMessageContaining("@ConditionalOnBatchRole(WORKER)");
    }

    @Test
    @DisplayName("the manager's guard: nothing is fenced when the step completed, and a partition that keeps moving is left to the restart")
    void theGuardAroundTheManagerStep() throws Exception {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER, Map.of("adhar.batch.partition.timeout", "1s"));
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        JobExecution failed = within(start(manager), Duration.ofMinutes(1));
        JobRepository repository = manager.getBean(JobRepository.class);
        JdbcPartitionTransport transport = manager.getBean(JdbcPartitionTransport.class);
        StepExecution managerStep = repository.getJobExecution(failed.getId()).getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(PartitionedJobs.MANAGER_STEP)).findFirst().orElseThrow();
        // One partition is running again, as after a restart's request reached a worker.
        StepExecution partition = repository.getJobExecution(failed.getId()).getStepExecutions().stream()
                .filter(step -> step.getStepName().endsWith(":partition0")).findFirst().orElseThrow();
        partition.setStatus(BatchStatus.STARTED);
        repository.update(partition);
        AtomicBoolean watching = new AtomicBoolean();
        WorkerLivenessWatch watch = new WorkerLivenessWatch(repository, KubernetesOwnerLookup.unavailable(),
                PartitionedJobs.WORKER_STEP, Duration.ofMinutes(5)) {
            @Override
            public AutoCloseable start(StepExecution step) {
                watching.set(true);
                return () -> {
                    watching.set(false);
                    throw new IllegalStateException("the watch would not stop");
                };
            }
        };
        ManagerPartitionGuard guard = new ManagerPartitionGuard(transport, repository, PartitionedJobs.WORKER_STEP, watch);

        guard.beforeStep(managerStep);
        assertThat(transport.pending(JdbcPartitionTransport.group(failed))).as("cleared before the step sends").isZero();
        assertThat(watching).isTrue();

        managerStep.setStatus(BatchStatus.COMPLETED);
        assertThat(guard.afterStep(managerStep)).as("the step keeps its own exit status").isNull();
        assertThat(watching).as("a watch that fails to stop does not fail the step").isFalse();
        assertThat(partitions(repository.getJobExecution(failed.getId()))).containsEntry(
                PartitionedJobs.WORKER_STEP + ":partition0", BatchStatus.STARTED);

        managerStep.setStatus(BatchStatus.STOPPED);
        guard.afterStep(managerStep);
        StepExecution fenced = repository.getJobExecution(failed.getId()).getStepExecutions().stream()
                .filter(step -> step.getStepName().endsWith(":partition0")).findFirst().orElseThrow();
        assertThat(fenced.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(fenced.getExitStatus().getExitDescription()).contains("the manager step ended STOPPED");
        assertThat(repository.getJobExecution(failed.getId()).getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(PartitionedJobs.MANAGER_STEP)).findFirst().orElseThrow()
                .getStatus()).as("only partitions are fenced").isEqualTo(BatchStatus.FAILED);
    }
}
