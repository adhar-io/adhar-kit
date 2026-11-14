package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.support.PartitionedJobs;
import com.adhar.kit.batch.support.PartitionedRunnerApp;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Remote partitioning as the platform runs it (KIT-2.2; design 0025 §4.5, §5.9, §5.10, acceptance
 * A2.2): every pod is its own JVM running {@link PartitionedRunnerApp} through
 * {@code AdharBatchApplication.run}, with the environment the {@code BatchJob} composition sets on a
 * Volcano gang ({@code ADHAR_BATCH_ROLE=manager|worker}) or on the native Indexed Job
 * ({@code indexed} with {@code JOB_COMPLETION_INDEX}), sharing one PostgreSQL 18 database. Pods are
 * killed with real signals; what Volcano or the Job controller would do next is done by the test.
 */
@Testcontainers(disabledWithoutDocker = true)
class RemotePartitioningIT {

    private static final int ROWS = 200;
    private static final int GRID = 4;

    @TempDir
    Path dir;

    private String url;
    private JdbcTemplate jdbc;
    private final List<Process> processes = new ArrayList<>();

    record Pod(String name, Process process, Path log, Path summary) {

        Result finish() throws Exception {
            boolean ended = process.waitFor(150, TimeUnit.SECONDS);
            String out = Files.readString(log, StandardCharsets.UTF_8);
            assertThat(ended).as("%s ends; its log ends with:%n%s%nand its threads are:%n%s", name,
                    out.substring(Math.max(0, out.length() - 6000)), ended ? "" : threads(process)).isTrue();
            JsonNode json = Files.exists(summary) ? new ObjectMapper().readTree(Files.readString(summary)) : null;
            return new Result(process.exitValue(), json, out);
        }
    }

    record Result(int exitCode, JsonNode summary, String out) {
    }

    /** A thread dump of a pod that did not end, for the failure message. */
    private static String threads(Process process) {
        try {
            Process jcmd = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "jcmd").toString(),
                    String.valueOf(process.pid()), "Thread.print").redirectErrorStream(true).start();
            String dump = new String(jcmd.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            jcmd.waitFor(30, TimeUnit.SECONDS);
            return dump;
        } catch (IOException | InterruptedException ex) {
            return "(no thread dump: " + ex + ")";
        }
    }

    @BeforeEach
    void database() {
        url = PostgresDatabases.createDatabase("remote_partitioning_it");
        DataSource dataSource = PostgresDatabases.dataSource(url);
        new ResourceDatabasePopulator(new ClassPathResource("adhar-batch-test/business-schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        TestJobs.seed(jdbc, ROWS);
    }

    @AfterEach
    void killLeftovers() {
        processes.forEach(Process::destroyForcibly);
    }

    private Pod start(String pod, String uid, Map<String, String> role, String run) throws IOException {
        Path log = dir.resolve(pod + "-" + uid + ".log");
        Path summary = dir.resolve(pod + "-" + uid + ".termination-log");
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx256m", "-cp", System.getProperty("java.class.path"),
                PartitionedRunnerApp.class.getName());
        builder.environment().keySet().removeIf(name -> name.startsWith("ADHAR_") || name.startsWith("KUBERNETES_")
                || name.startsWith("SPRING_") || name.equals("JOB_COMPLETION_INDEX"));
        Map<String, String> env = new HashMap<>();
        env.put("ADHAR_BATCH_JOB", PartitionedJobs.JOB);
        env.put("ADHAR_BATCH_NAME", "importer");
        env.put("ADHAR_BATCH_TRIGGER", "manual");
        env.put("ADHAR_BATCH_PARAMS", "{\"run\":\"" + run + "\"}");
        env.put("ADHAR_BATCH_PARTITION_TRANSPORT", "jdbc");
        env.put("ADHAR_BATCH_PARTITION_GRIDSIZE", String.valueOf(GRID));
        env.put("ADHAR_BATCH_PARTITION_TIMEOUT", "120s");
        env.put("ADHAR_BATCH_PARTITION_POLLINTERVAL", "200ms");
        env.put("ADHAR_BATCH_SHUTDOWN_TIMEOUT", "5s");
        env.put("POD_NAME", pod);
        env.put("POD_UID", uid);
        env.put("SPRING_DATASOURCE_URL", url);
        env.put("SPRING_DATASOURCE_USERNAME", PostgresDatabases.username());
        env.put("SPRING_DATASOURCE_PASSWORD", PostgresDatabases.password());
        env.put("TEST_CHUNK_MILLIS", "100");
        env.put("TEST_TERMINATION_LOG", summary.toString());
        env.putAll(role);
        builder.environment().putAll(env);
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        processes.add(process);
        return new Pod(pod, process, log, summary);
    }

    private static Map<String, String> role(String role) {
        return Map.of("ADHAR_BATCH_ROLE", role);
    }

    private static Map<String, String> indexed(int index) {
        return Map.of("ADHAR_BATCH_ROLE", "indexed", "JOB_COMPLETION_INDEX", String.valueOf(index));
    }

    /** Partitions of an execution by name, with their status. */
    private Map<String, String> partitions(long execution) {
        Map<String, String> partitions = new TreeMap<>();
        jdbc.query("select step_name, status from batch.batch_step_execution where job_execution_id = ? "
                        + "and step_name like 'importStep:%'", rs -> {
            partitions.put(rs.getString(1), rs.getString(2));
        }, execution);
        return partitions;
    }

    private List<Long> executions() {
        return jdbc.queryForList("select job_execution_id from batch.batch_job_execution order by job_execution_id",
                Long.class);
    }

    /** Partitions that are running and have committed at least two chunks. */
    private int partitionsMidway() {
        return jdbc.queryForObject("select count(*) from batch.batch_step_execution where step_name like "
                + "'importStep:%' and status = 'STARTED' and commit_count >= 2", Integer.class);
    }

    private void awaitBothWorkersMidPartition() {
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofMillis(50)).until(() -> {
            try {
                return partitionsMidway() >= 2;
            } catch (RuntimeException ex) {
                return false; // the schema is still being migrated
            }
        });
    }

    private int targetRows() {
        return jdbc.queryForObject("select count(*) from TARGET_ITEMS", Integer.class);
    }

    private int distinctTargetRows() {
        return jdbc.queryForObject("select count(distinct ID) from TARGET_ITEMS", Integer.class);
    }

    private static Set<String> notCompleted(Map<String, String> partitions) {
        Set<String> names = new TreeSet<>();
        partitions.forEach((name, status) -> {
            if (!status.equals("COMPLETED")) {
                names.add(name);
            }
        });
        return names;
    }

    @Test
    @DisplayName("A2.2: a worker killed mid-partition, then the gang restarted (RestartJob): only unfinished partitions re-run, every row once")
    void killedWorkerAndGangRestart() throws Exception {
        Pod manager = start("importer-manager-0", "uid-1", role("manager"), "a22");
        Pod worker0 = start("importer-worker-0", "uid-1", role("worker"), "a22");
        Pod worker1 = start("importer-worker-1", "uid-1", role("worker"), "a22");
        awaitBothWorkersMidPartition();

        // The node under worker-0 dies: SIGKILL, mid-partition, between two commits.
        worker0.process().destroyForcibly();
        assertThat(worker0.process().waitFor(30, TimeUnit.SECONDS)).isTrue();
        // Volcano's PodFailed -> RestartJob: every other pod of the gang is deleted (SIGTERM, grace).
        manager.process().destroy();
        worker1.process().destroy();
        Result stoppedManager = manager.finish();
        Result stoppedWorker = worker1.finish();
        assertThat(stoppedManager.exitCode()).as(stoppedManager.out()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        assertThat(stoppedWorker.exitCode()).as(stoppedWorker.out()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        assertThat(stoppedWorker.summary().get("outcome").asText()).isEqualTo("stopped");
        long first = executions().getFirst();
        Map<String, String> firstPartitions = partitions(first);
        assertThat(firstPartitions).hasSize(GRID);
        assertThat(targetRows()).as("the killed worker's committed chunks stay; its open chunk rolled back")
                .isLessThan(ROWS);

        // The new gang: the same pod names, new uids. The manager recovers attempt 1 and restarts it.
        Pod manager2 = start("importer-manager-0", "uid-2", role("manager"), "a22");
        Pod worker0b = start("importer-worker-0", "uid-2", role("worker"), "a22");
        Pod worker1b = start("importer-worker-1", "uid-2", role("worker"), "a22");
        Result completed = manager2.finish();
        assertThat(completed.exitCode()).as(completed.out()).isZero();
        assertThat(completed.summary().get("outcome").asText()).as(completed.out()).isEqualTo("completed");
        Result w0 = worker0b.finish();
        Result w1 = worker1b.finish();
        assertThat(w0.exitCode()).as(w0.out()).isZero();
        assertThat(w1.exitCode()).as(w1.out()).isZero();
        List<Long> executions = executions();
        assertThat(executions).hasSize(2);
        Map<String, String> recovered = partitions(first);
        assertThat(recovered.values()).as("attempt 1 recovered: nothing of it still runs")
                .doesNotContain("STARTED", "STARTING", "STOPPING");
        Map<String, String> rerun = partitions(executions.getLast());
        assertThat(rerun.keySet()).as("only the partitions attempt 1 did not complete re-ran")
                .isEqualTo(notCompleted(recovered)).isNotEmpty();
        assertThat(rerun.values()).containsOnly("COMPLETED");
        assertThat(targetRows()).isEqualTo(ROWS);
        assertThat(distinctTargetRows()).isEqualTo(ROWS);
    }

    @Test
    @DisplayName("native Indexed Job: SIGTERM on a worker stops only its partition, the manager fails retryable, and its retry completes with the surviving worker")
    void workerOnlyStopOnTheIndexedJob() throws Exception {
        Pod manager = start("importer-x7k2-0", "uid-1", indexed(0), "indexed");
        Pod worker1 = start("importer-x7k2-1", "uid-1", indexed(1), "indexed");
        Pod worker2 = start("importer-x7k2-2", "uid-1", indexed(2), "indexed");
        awaitBothWorkersMidPartition();

        // An eviction or a drain of worker 1: SIGTERM. It stops its own partition at the next chunk boundary.
        worker1.process().destroy();
        Result stopped = worker1.finish();
        assertThat(stopped.exitCode()).as(stopped.out()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        assertThat(stopped.summary().get("outcome").asText()).isEqualTo("stopped");
        assertThat(stopped.out()).contains("the other partitions carry on");

        // Worker 2 finishes every other partition; the manager cannot complete the run and fails it,
        // retryable (exit 5: the Job's backoffLimit retries index 0), never as an operator's stop (exit 4).
        Result failed = manager.finish();
        assertThat(failed.exitCode()).as(failed.out()).isEqualTo(5);
        assertThat(failed.summary().get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.summary().get("exitDescription").asText()).contains("stopped by their workers");
        long first = executions().getFirst();
        Map<String, String> firstPartitions = partitions(first);
        assertThat(firstPartitions.values()).containsOnly("COMPLETED", "STOPPED");
        assertThat(firstPartitions.values().stream().filter("STOPPED"::equals)).hasSize(1);
        assertThat(worker2.process().isAlive()).as("a worker does not leave because an execution failed").isTrue();

        // The Job controller retries index 0 and replaces index 1; index 2 is still serving.
        Pod retried = start("importer-x7k2-0", "uid-2", indexed(0), "indexed");
        Pod replaced = start("importer-x7k2-1", "uid-2", indexed(1), "indexed");
        Result completed = retried.finish();
        Result survivor = worker2.finish();
        Result replacement = replaced.finish();

        assertThat(completed.exitCode()).as(completed.out()).isZero();
        assertThat(survivor.exitCode()).as(survivor.out()).isZero();
        assertThat(replacement.exitCode()).as(replacement.out()).isZero();
        Map<String, String> rerun = partitions(executions().getLast());
        assertThat(rerun.keySet()).isEqualTo(notCompleted(firstPartitions));
        assertThat(targetRows()).isEqualTo(ROWS);
        assertThat(distinctTargetRows()).isEqualTo(ROWS);
    }
}
