package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.BatchRole;
import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.runtime.RunContext;
import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.runtime.RunTrigger;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PartitionedJobs;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Remote partitioning across contexts (KIT-2.1, KIT-2.2; design 0025 §5.9, §5.10): a manager context
 * and worker contexts, each a real Spring Boot application with the kit's auto-configuration, on one
 * PostgreSQL 18 database, talking only through the JobRepository and the partition channel in it.
 * The manager is started through Spring Batch's operator and each worker through the kit's
 * {@link PartitionWorker}, so a test decides exactly when each one runs, is held, recovered or stopped.
 * The runner's role switch and real signals are {@code RemotePartitioningIT}'s.
 */
@Testcontainers(disabledWithoutDocker = true)
class RemotePartitioningTestContainerTest {

    private static final int ROWS = 80;
    private static final int GRID = 4;

    private String url;
    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final JobParameters parameters = new JobParametersBuilder().addString("run", "kit-2.2").toJobParameters();

    @BeforeEach
    void database() {
        url = PostgresDatabases.createDatabase("remote_partitioning");
    }

    @AfterEach
    void closeContexts() {
        // A held writer would keep its partition's step scope registered for the JVM's lifetime, and
        // the next test's partition with the same id (each test has a fresh database) would find it.
        contexts.forEach(context -> {
            if (context.isActive()) {
                context.getBean(PartitionedJobs.Gate.class).release();
            }
        });
        contexts.forEach(ConfigurableApplicationContext::close);
    }

    private ConfigurableApplicationContext context(BatchRole role, Map<String, Object> extra) {
        return context(role, extra, null);
    }

    /** {@code lookup}: the Kubernetes API a run in a cluster reads; {@code null} outside one. */
    private ConfigurableApplicationContext context(BatchRole role, Map<String, Object> extra,
                                                   KubernetesOwnerLookup lookup) {
        Map<String, Object> properties = BatchTestApplication.postgres(url);
        properties.put("adhar.batch.role", role.label());
        properties.put("adhar.batch.partition.grid-size", String.valueOf(GRID));
        properties.put("adhar.batch.partition.poll-interval", "100ms");
        properties.putAll(extra);
        org.springframework.boot.builder.SpringApplicationBuilder builder = BatchTestApplication
                .builder(PartitionedJobs.class).properties(properties);
        if (lookup != null) {
            builder.initializers(context -> context.getBeanFactory().registerSingleton("kubernetesOwnerLookup", lookup));
        }
        ConfigurableApplicationContext context = builder.run();
        contexts.add(context);
        return context;
    }

    private ConfigurableApplicationContext manager() {
        return context(BatchRole.MANAGER, Map.of());
    }

    private ConfigurableApplicationContext worker(long chunkMillis) {
        return context(BatchRole.WORKER, Map.of("test.chunk-millis", String.valueOf(chunkMillis)));
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

    private CompletableFuture<RunOutcome> serve(ConfigurableApplicationContext worker, AtomicBoolean stopping) {
        return serve(worker, stopping, "importer-worker");
    }

    private CompletableFuture<RunOutcome> serve(ConfigurableApplicationContext worker, AtomicBoolean stopping, String pod) {
        Job job = worker.getBean(PartitionedJobs.JOB, Job.class);
        RunContext context = new RunContext(PartitionedJobs.JOB, "importer", BatchRole.WORKER, RunTrigger.MANUAL, "",
                "", "", pod, "", "", Optional.empty(), ZoneOffset.UTC, Optional.empty(), "", 1,
                OptionalInt.empty(), "test");
        return CompletableFuture.supplyAsync(() -> worker.getBean(PartitionWorker.class)
                .consume(job, parameters, context, stopping::get));
    }

    private static <T> T within(CompletableFuture<T> future, Duration timeout) throws Exception {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(PostgresDatabases.dataSource(url));
    }

    private int targetRows() {
        return jdbc().queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class);
    }

    private int distinctTargetRows() {
        return jdbc().queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class);
    }

    private static Map<String, BatchStatus> partitions(JobExecution execution) {
        Map<String, BatchStatus> partitions = new TreeMap<>();
        execution.getStepExecutions().stream().filter(step -> step.getStepName().startsWith(PartitionedJobs.WORKER_STEP + ":"))
                .forEach(step -> partitions.put(step.getStepName(), step.getStatus()));
        return partitions;
    }

    @Test
    @DisplayName("a manager and two workers run every partition once, in the workers, and the workers end with the run")
    void managerAndWorkers() throws Exception {
        ConfigurableApplicationContext manager = manager();
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        ConfigurableApplicationContext first = worker(20);
        ConfigurableApplicationContext second = worker(20);

        // The role switch: the manager has no worker step, a worker no manager machinery.
        assertThat(manager.containsBean(PartitionedJobs.WORKER_STEP)).isFalse();
        assertThat(first.containsBean(PartitionedJobs.WORKER_STEP)).isTrue();
        assertThat(first.getBean(RemotePartitioning.class).role()).isEqualTo(BatchRole.WORKER);

        AtomicBoolean never = new AtomicBoolean();
        CompletableFuture<RunOutcome> one = serve(first, never);
        CompletableFuture<RunOutcome> two = serve(second, never);
        JobExecution execution = within(start(manager), Duration.ofMinutes(2));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // The manager's own view of its partitions is the repository's (the termination summary and the
        // run events read it), not the STARTING copies it created before the workers ran them.
        assertThat(partitions(execution)).hasSize(GRID).allSatisfy((name, status) ->
                assertThat(status).isEqualTo(BatchStatus.COMPLETED));
        assertThat(execution.getStepExecutions().stream().filter(step -> step.getStepName().contains(":"))
                .mapToLong(step -> step.getWriteCount()).sum()).isEqualTo(ROWS);
        assertThat(targetRows()).isEqualTo(ROWS);
        assertThat(distinctTargetRows()).isEqualTo(ROWS);
        RunOutcome a = within(one, Duration.ofSeconds(30));
        RunOutcome b = within(two, Duration.ofSeconds(30));
        assertThat(List.of(a, b)).allSatisfy(outcome -> {
            assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
            assertThat(outcome.exitCode()).isEqualTo(ExitCodes.COMPLETED);
        });
        assertThat(a.message() + " " + b.message()).as("both workers took partitions")
                .doesNotContain("served 0 partition");
        assertThat(manager.getBean(JdbcPartitionTransport.class).pending(JdbcPartitionTransport.group(execution)))
                .isZero();
    }

    @Test
    @DisplayName("F20: the manager clears what a failed attempt left in the channel, and a worker discards a stale request")
    void staleRequests() throws Exception {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER,
                Map.of("adhar.batch.partition.timeout", "1s"));
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        // Attempt 1 has no workers: the manager gives up after its timeout and fails the run, retryable.
        JobExecution failed = within(start(manager), Duration.ofMinutes(1));
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(ExitCodes.of(failed)).isEqualTo(ExitCodes.FAILED_RETRYABLE);
        assertThat(partitions(manager.getBean(JobRepository.class).getJobExecution(failed.getId()))).hasSize(GRID)
                .allSatisfy((name, status) -> assertThat(status).as("fenced by the manager").isEqualTo(BatchStatus.FAILED));

        // Attempt 1's requests were never claimed: they are still in the channel, and stale.
        JdbcPartitionTransport transport = manager.getBean(JdbcPartitionTransport.class);
        String group = JdbcPartitionTransport.group(failed);
        assertThat(transport.pending(group)).isEqualTo(GRID);
        Object claimed = transport.channel(group).receive(0).getPayload();
        assertThat(claimed).isInstanceOf(StepExecutionRequest.class);
        StaleRequestGuard.Verdict verdict = manager.getBean(StaleRequestGuard.class).admit((StepExecutionRequest) claimed);
        assertThat(verdict.admitted()).as("a worker discards a request of a failed attempt").isFalse();
        assertThat(verdict.reason()).contains("FAILED");
        assertThat(transport.pending(group)).isEqualTo(GRID - 1);

        // Attempt 2: the manager clears the group before it sends its own requests.
        ConfigurableApplicationContext restarted = manager();
        CompletableFuture<JobExecution> second = start(restarted);
        // The stale requests are still there until the restart reaches its partition step: wait for the
        // count to settle on the grid. Had they not been cleared, it would settle above it.
        await().atMost(Duration.ofSeconds(30)).during(Duration.ofSeconds(1))
                .until(() -> transport.pending(group) == GRID);
        assertThat(transport.pending(group)).as("the restart's own requests only").isEqualTo(GRID);
        CompletableFuture<RunOutcome> served = serve(worker(0), new AtomicBoolean());
        JobExecution completed = within(second, Duration.ofMinutes(2));

        assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(targetRows()).isEqualTo(ROWS);
        assertThat(distinctTargetRows()).isEqualTo(ROWS);
        assertThat(within(served, Duration.ofSeconds(30)).exitCode()).isZero();
    }

    @Test
    @DisplayName("a partition recorded without its range (the manager killed mid-split) runs its planned range on restart")
    void halfWrittenSplit() throws Exception {
        ConfigurableApplicationContext manager = context(BatchRole.MANAGER, Map.of("adhar.batch.partition.timeout", "1s"));
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        JobExecution failed = within(start(manager), Duration.ofMinutes(1));
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        // What a manager killed between Spring Batch's two statements leaves: the partition's step
        // execution saved with the empty context, its range never written.
        java.io.ByteArrayOutputStream empty = new java.io.ByteArrayOutputStream();
        new org.springframework.batch.core.repository.dao.DefaultExecutionContextSerializer().serialize(Map.of(), empty);
        int blanked = jdbc().update("UPDATE batch.batch_step_execution_context SET short_context = ?, "
                + "serialized_context = NULL WHERE step_execution_id = (SELECT step_execution_id FROM "
                + "batch.batch_step_execution WHERE step_name = ? AND job_execution_id = ?)",
                empty.toString(java.nio.charset.StandardCharsets.ISO_8859_1), PartitionedJobs.WORKER_STEP + ":partition1",
                failed.getId());
        assertThat(blanked).isOne();

        ConfigurableApplicationContext restarted = manager();
        CompletableFuture<RunOutcome> served = serve(worker(0), new AtomicBoolean());
        JobExecution completed = within(start(restarted), Duration.ofMinutes(2));

        assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(targetRows()).as("before the plan: COMPLETED with partition1's 20 rows missing").isEqualTo(ROWS);
        assertThat(distinctTargetRows()).isEqualTo(ROWS);
        assertThat(within(served, Duration.ofSeconds(30)).exitCode()).isZero();
    }

    @Test
    @DisplayName("a worker killed mid-partition: the manager fails that partition at once, not at its timeout, and a restart finishes it")
    void deadWorker() throws Exception {
        WireMockServer api = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        api.start();
        try {
            StubMapping aAlive = api.stubFor(WireMock.get("/api/v1/namespaces/batch-test/pods/importer-worker-a")
                    .willReturn(WireMock.okJson("{\"metadata\":{\"uid\":\"\"},\"status\":{\"phase\":\"Running\"}}")));
            api.stubFor(WireMock.get("/api/v1/namespaces/batch-test/pods/importer-worker-b")
                    .willReturn(WireMock.okJson("{\"metadata\":{\"uid\":\"\"},\"status\":{\"phase\":\"Running\"}}")));
            KubernetesOwnerLookup lookup = new KubernetesOwnerLookup(java.net.URI.create(api.baseUrl()), () -> "token",
                    "batch-test", java.net.http.HttpClient.newHttpClient(), Duration.ofSeconds(5));
            ConfigurableApplicationContext manager = context(BatchRole.MANAGER,
                    Map.of("adhar.batch.partition.timeout", "5m"), lookup);
            TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
            ConfigurableApplicationContext a = worker(0);
            PartitionedJobs.Gate gate = a.getBean(PartitionedJobs.Gate.class);
            gate.holdAt(33);
            serve(a, new AtomicBoolean(), "importer-worker-a");
            CompletableFuture<JobExecution> attempt1 = start(manager);
            assertThat(gate.awaitHeld(Duration.ofMinutes(1))).as("worker a is inside its second partition").isTrue();
            CompletableFuture<RunOutcome> b = serve(worker(0), new AtomicBoolean(), "importer-worker-b");

            // Worker a's pod is killed (SIGKILL, OOM): the API no longer has it, its JVM never commits.
            api.removeStub(aAlive);
            long killedAt = System.nanoTime();
            JobExecution first = within(attempt1, Duration.ofMinutes(1));

            assertThat(Duration.ofNanos(System.nanoTime() - killedAt)).as("not the 5 minute timeout")
                    .isLessThan(Duration.ofSeconds(30));
            assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(ExitCodes.of(first)).isEqualTo(ExitCodes.FAILED_RETRYABLE);
            JobExecution recorded = manager.getBean(JobRepository.class).getJobExecution(first.getId());
            assertThat(recorded.getStepExecutions().stream().filter(step -> step.getStatus() == BatchStatus.FAILED
                    && step.getStepName().startsWith(PartitionedJobs.WORKER_STEP + ":")))
                    .as("only worker a's partition; worker b finished the rest").singleElement()
                    .satisfies(step -> assertThat(step.getExitStatus().getExitDescription())
                            .contains("worker pod importer-worker-a is gone"));
            await("the watch ends with the manager step").atMost(Duration.ofSeconds(10)).until(() -> Thread
                    .getAllStackTraces().keySet().stream().noneMatch(thread -> thread.getName().equals("batch-worker-watch")));

            // The 'dead' JVM wakes up: its chunk's commit is fenced. The restart finishes the partition.
            gate.release();
            JobExecution completed = within(start(manager()), Duration.ofMinutes(2));

            assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(targetRows()).isEqualTo(ROWS);
            assertThat(distinctTargetRows()).isEqualTo(ROWS);
            assertThat(within(b, Duration.ofSeconds(30)).exitCode()).isZero();
        } finally {
            api.stop();
        }
    }

    @Test
    @DisplayName("F19: a zombie worker of a recovered attempt is fenced - its commit fails and its writes roll back")
    void zombieAfterRecover() throws Exception {
        zombieScenario(false);
    }

    @Test
    @DisplayName("a manager that times out fences the partitions still running, so their slow worker commits nothing more")
    void zombieAfterManagerTimeout() throws Exception {
        zombieScenario(true);
    }

    /**
     * Attempt 1's only worker is held inside a chunk, with that chunk's rows written but not committed.
     * Attempt 1 then ends behind its back (recovered, as a restarted manager recovers a dead one, or
     * timed out), attempt 2 runs the unfinished partitions to completion with a healthy worker, and
     * only then is the held worker let go: its commit must fail, or the rows it holds would be written
     * a second time.
     */
    private void zombieScenario(boolean managerTimesOut) throws Exception {
        ConfigurableApplicationContext manager = managerTimesOut
                ? context(BatchRole.MANAGER, Map.of("adhar.batch.partition.timeout", "3s")) : manager();
        TestJobs.seed(manager.getBean(JdbcTemplate.class), ROWS);
        ConfigurableApplicationContext zombie = worker(0);
        PartitionedJobs.Gate gate = zombie.getBean(PartitionedJobs.Gate.class);
        // Ranges are 1-20, 21-40, ...: id 33 is the third chunk of the second partition the worker runs.
        gate.holdAt(33);
        CompletableFuture<RunOutcome> zombieRun = serve(zombie, new AtomicBoolean());
        CompletableFuture<JobExecution> attempt1 = start(manager);
        assertThat(gate.awaitHeld(Duration.ofMinutes(1))).as("the worker is held mid-partition").isTrue();
        int committedBefore = targetRows() - 3; // the held chunk's 31..33 are written but uncommitted...
        JobRepository repository = manager.getBean(JobRepository.class);

        JobExecution first;
        if (managerTimesOut) {
            first = within(attempt1, Duration.ofMinutes(1));
            assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        } else {
            // The manager pod dies: its context, and with it its connections, are gone, so it writes
            // nothing more and attempt 1 stays STARTED until a restarted manager recovers it.
            contexts.remove(manager);
            manager.close();
            within(attempt1.handle((execution, failure) -> execution), Duration.ofMinutes(1));
            ConfigurableApplicationContext recovering = manager();
            repository = recovering.getBean(JobRepository.class);
            JobRepository recoveringRepository = recovering.getBean(JobRepository.class);
            JobExecution running = recoveringRepository.getJobExecution(recoveringRepository
                    .getLastJobExecution(PartitionedJobs.JOB, parameters).getId());
            assertThat(running.getStatus()).isEqualTo(BatchStatus.STARTED);
            recovering.getBean(JobOperator.class).recover(running);
            first = repository.getJobExecution(running.getId());
        }
        Map<String, BatchStatus> attempt1Partitions = partitions(repository.getJobExecution(first.getId()));
        assertThat(attempt1Partitions).hasSize(GRID).doesNotContainValue(BatchStatus.STARTED)
                .doesNotContainValue(BatchStatus.STARTING);

        // Attempt 2 runs what attempt 1 did not complete, with a healthy worker, to the end.
        ConfigurableApplicationContext restarted = manager();
        CompletableFuture<RunOutcome> healthy = serve(worker(0), new AtomicBoolean());
        JobExecution second = within(start(restarted), Duration.ofMinutes(2));
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(partitions(second).keySet()).as("only the partitions attempt 1 did not complete re-ran")
                .isEqualTo(attempt1Partitions.entrySet().stream().filter(entry -> entry.getValue() != BatchStatus.COMPLETED)
                        .map(Map.Entry::getKey).collect(Collectors.toSet()));
        assertThat(targetRows()).isEqualTo(ROWS);

        // The zombie wakes up and tries to commit the chunk it held.
        gate.release();
        await().atMost(Duration.ofSeconds(30)).until(() -> zombieRun.isDone());
        assertThat(targetRows()).as("the zombie's chunk rolled back").isEqualTo(ROWS);
        assertThat(distinctTargetRows()).isEqualTo(ROWS);
        assertThat(zombieRun.get().exitCode()).isZero();
        within(healthy, Duration.ofSeconds(30));
    }
}
