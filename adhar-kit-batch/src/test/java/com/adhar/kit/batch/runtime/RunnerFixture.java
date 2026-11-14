package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEvent;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.RunnerJobs;
import com.adhar.kit.batch.support.TestJobs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * A runner as Kubernetes mode builds it, on the real H2 JobRepository of a test context, with
 * WireMock standing in for the API server and the pod's environment in a map.
 */
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class, RunnerJobs.class})
abstract class RunnerFixture {

    static final WireMockServer API = new WireMockServer(options().dynamicPort());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.h2().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    JobRepository jobRepository;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    JobResolver jobResolver;
    @Autowired
    JobParameterSpecRegistry specs;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RunnerJobs.ObserverRelay relay;
    @Autowired
    ObjectProvider<JobOperator> jobOperatorProvider;
    @Autowired
    ObjectProvider<JobParameterSpecRegistry> specsProvider;

    final Map<String, String> env = new HashMap<>();
    final BatchProperties properties = new BatchProperties();
    final List<BatchRunEvent> events = new CopyOnWriteArrayList<>();
    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    final List<Duration> sleeps = new CopyOnWriteArrayList<>();
    Runnable onSleep = () -> { };
    Path terminationLog;
    JdbcExecutionLease lease;
    GracefulStopLifecycle stop;
    BatchExitCodeGenerator exitCodes;
    ScheduledExecutorService heartbeats;

    @BeforeAll
    static void startApi() {
        API.start();
    }

    @AfterAll
    static void stopApi() {
        API.stop();
    }

    @BeforeEach
    void resetFixture() throws Exception {
        API.resetAll();
        RunnerJobs.FAILURE.set("");
        RunnerJobs.CHUNK_MILLIS.set(100);
        for (String table : RunnerJobs.tables()) {
            jdbc.update("DELETE FROM " + table);
        }
        env.clear();
        env.putAll(Map.of("ADHAR_BATCH_NAME", "reporter", "POD_NAME", "reporter-abc12", "POD_UID", "uid-self",
                "NODE_NAME", "worker-1"));
        terminationLog = Files.createTempFile("termination-log", "");
        lease = new JdbcExecutionLease(jdbc, "ADHAR_BATCH_EXECUTION_LEASE");
        stop = new GracefulStopLifecycle(jobOperatorProvider, properties.getShutdown().getTimeout());
        stop.start();
        exitCodes = new BatchExitCodeGenerator();
        heartbeats = AdharBatchRunner.Collaborators.heartbeatThread();
        events.clear();
        stdout.reset();
        sleeps.clear();
    }

    @AfterEach
    void stopHeartbeats() {
        heartbeats.shutdownNow();
        relay.delegate = null;
    }

    KubernetesOwnerLookup lookup() {
        return new KubernetesOwnerLookup(java.net.URI.create(API.baseUrl()), () -> "token", "team-a-prod",
                java.net.http.HttpClient.newHttpClient(), Duration.ofSeconds(5));
    }

    AdharBatchRunner runner() {
        KubernetesOwnerLookup lookup = lookup();
        BatchRunEventPublisher publisher = new BatchRunEventPublisher(event -> events.add((BatchRunEvent) event),
                specsProvider);
        relay.delegate = null;
        AdharBatchRunner.Collaborators with = new AdharBatchRunner.Collaborators(jobRepository, jobOperator,
                jobResolver, specs, new JobParametersResolver(), new RunContextResolver(env::get, lookup, properties),
                new RecoveryPolicy(lease, lookup, properties.getRecovery().getStaleAfter(), Clock.systemUTC()), lease,
                stop, new TerminationSummaryWriter(terminationLog, new PrintStream(stdout, true, StandardCharsets.UTF_8)),
                exitCodes, publisher, heartbeats, null);
        AdharBatchRunner runner = new AdharBatchRunner(() -> with, properties, env::get, Clock.systemUTC(),
                duration -> {
                    sleeps.add(duration);
                    onSleep.run();
                });
        relay.delegate = new com.adhar.kit.batch.listener.BatchRunObserver() {
            @Override
            public void jobStarted(org.springframework.batch.core.job.JobExecution execution) {
                runner.jobStarted(execution);
                publisher.jobStarted(execution);
            }

            @Override
            public void jobFinished(org.springframework.batch.core.job.JobExecution execution) {
                runner.jobFinished(execution);
                publisher.jobFinished(execution);
            }

            @Override
            public void chunkCommitted(org.springframework.batch.core.step.StepExecution execution, long items) {
                runner.chunkCommitted(execution, items);
            }
        };
        return runner;
    }

    RunOutcome run() {
        runner().run(null);
        return exitCodes.outcome().orElseThrow();
    }

    String summary() throws Exception {
        return Files.readString(terminationLog);
    }

    @SuppressWarnings("unchecked")
    <T extends BatchRunEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(event -> (T) event).toList();
    }
}
