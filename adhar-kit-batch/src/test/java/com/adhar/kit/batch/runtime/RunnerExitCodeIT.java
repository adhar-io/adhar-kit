package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.support.RunnerExitCodeApp;
import com.adhar.kit.batch.support.RunnerJobs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The exit-code contract (design 0025 §5.7) as the kubelet sees it: each case is a separate JVM
 * running {@link RunnerExitCodeApp} through {@code AdharBatchApplication.run}, with the environment
 * the {@code BatchJob} composition sets. The process's exit status and the termination message are
 * what the pod failure policy, the Console and the CLI read; nothing here is mocked.
 */
class RunnerExitCodeIT {

    @TempDir
    Path dir;

    record Result(int exitCode, JsonNode summary, String stdout) {
    }

    private Process start(Map<String, String> env) throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx256m", "-cp", System.getProperty("java.class.path"),
                RunnerExitCodeApp.class.getName());
        builder.environment().keySet().removeIf(name -> name.startsWith("ADHAR_") || name.startsWith("KUBERNETES_"));
        builder.environment().putAll(Map.of("ADHAR_BATCH_JOB", RunnerJobs.REPORT_JOB, "ADHAR_BATCH_NAME", "reporter",
                "ADHAR_BATCH_TRIGGER", "manual", "ADHAR_BATCH_ROLE", "single", "POD_NAME", "reporter-x1",
                "TEST_TERMINATION_LOG", dir.resolve("termination-log").toString()));
        builder.environment().putAll(env);
        builder.redirectErrorStream(true).redirectOutput(dir.resolve("out.log").toFile());
        return builder.start();
    }

    private Result finish(Process process) throws Exception {
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).as("the run ends").isTrue();
        String out = Files.readString(dir.resolve("out.log"), StandardCharsets.UTF_8);
        Path log = dir.resolve("termination-log");
        JsonNode summary = Files.exists(log) ? new ObjectMapper().readTree(Files.readString(log)) : null;
        return new Result(process.exitValue(), summary, out);
    }

    private Result run(Map<String, String> env) throws Exception {
        return finish(start(env));
    }

    @Test
    @DisplayName("0: completed; the summary is in the termination message and on stdout")
    void completed() throws Exception {
        Result result = run(Map.of("ADHAR_BATCH_PARAMS", "{\"businessDate\":\"2026-11-03\"}"));

        assertThat(result.exitCode()).as(result.stdout()).isZero();
        assertThat(result.summary().get("outcome").asText()).isEqualTo("completed");
        assertThat(result.summary().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.stdout()).contains(result.summary().toString());
    }

    @Test
    @DisplayName("2: invalid parameters, before any execution, with the reason")
    void invalid() throws Exception {
        Result result = run(Map.of("ADHAR_BATCH_PARAMS", "{\"businessDate\":\"2026-11-03\",\"region\":\"xx\"}"));

        assertThat(result.exitCode()).as(result.stdout()).isEqualTo(2);
        assertThat(result.summary().get("reason").asText()).isEqualTo("parameters");
        assertThat(result.summary().has("executionId")).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ADHAR_BATCH_ROLE=leader", "ADHAR_BATCH_ROLE=indexed,JOB_COMPLETION_INDEX=one",
            "ADHAR_BATCH_ROLE=manager,ADHAR_BATCH_PARTITION_TRANSPORT=nats", "ADHAR_BATCH_DISTRIBUTION=sharded",
            "ADHAR_BATCH_ROLE=worker,ADHAR_BATCH_PARTITION_GRIDSIZE=four"})
    @DisplayName("2: a role, distribution or partition setting the pod cannot read is an invalid request, never retried")
    void unreadableRunSelector(String settings) throws Exception {
        Map<String, String> env = new HashMap<>(Map.of("ADHAR_BATCH_PARAMS", "{\"businessDate\":\"2026-11-03\"}"));
        for (String setting : settings.split(",")) {
            String[] pair = setting.split("=", 2);
            env.put(pair[0], pair[1]);
        }
        Result result = run(env);

        assertThat(result.exitCode()).as(result.stdout()).isEqualTo(2);
    }

    @Test
    @DisplayName("3: failed, not retryable")
    void notRetryable() throws Exception {
        Result result = run(Map.of("ADHAR_BATCH_PARAMS", "{\"businessDate\":\"2026-11-03\"}", "TEST_FAILURE", "fatal"));

        assertThat(result.exitCode()).as(result.stdout()).isEqualTo(3);
        assertThat(result.summary().get("status").asText()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("5: failed, retryable")
    void retryable() throws Exception {
        Result result = run(Map.of("ADHAR_BATCH_PARAMS", "{\"businessDate\":\"2026-11-03\"}",
                "TEST_FAILURE", "retryable"));

        assertThat(result.exitCode()).as(result.stdout()).isEqualTo(5);
        assertThat(result.summary().get("outcome").asText()).isEqualTo("failed");
    }

    @Test
    @DisplayName("SIGTERM: the execution is recorded STOPPED, the summary written, and the JVM keeps its signal status")
    void sigterm() throws Exception {
        Process process = start(Map.of("ADHAR_BATCH_JOB", RunnerJobs.SLOW_CHUNK_JOB,
                "ADHAR_BATCH_PARAMS", "{\"run\":\"sigterm\"}", "TEST_CHUNK_MILLIS", "1000"));
        await().atMost(Duration.ofSeconds(90)).until(() ->
                Files.readString(dir.resolve("out.log"), StandardCharsets.UTF_8).contains("Job [slowChunkJob] starting"));

        process.destroy();
        Result result = finish(process);

        assertThat(result.exitCode()).as(result.stdout()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        assertThat(result.summary().get("status").asText()).isEqualTo("STOPPED");
        assertThat(result.summary().get("outcome").asText()).isEqualTo("stopped");
        assertThat(result.summary().get("exitCode").asInt()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        List<String> lines = new ArrayList<>(result.stdout().lines().toList());
        assertThat(lines).anyMatch(line -> line.contains("Stopping execution"));
    }
}
