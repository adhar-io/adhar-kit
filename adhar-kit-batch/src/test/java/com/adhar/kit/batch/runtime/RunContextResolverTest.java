package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.KubernetesLookupException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * {@link RunContextResolver}: the pod's environment as the {@code BatchJob} composition sets it,
 * the owner Job and the pod served by a fake API server.
 */
class RunContextResolverTest {

    static final WireMockServer API = new WireMockServer(options().dynamicPort());

    private final Map<String, String> env = new HashMap<>();
    private final BatchProperties properties = new BatchProperties();

    @BeforeAll
    static void startApi() {
        API.start();
    }

    @AfterAll
    static void stopApi() {
        API.stop();
    }

    @BeforeEach
    void setUp() {
        API.resetAll();
        env.clear();
        env.putAll(Map.of("ADHAR_BATCH_JOB", "importJob", "ADHAR_BATCH_NAME", "importer",
                "ADHAR_BATCH_TRIGGER", "schedule", "ADHAR_BATCH_TIME_ZONE", "Asia/Singapore",
                "ADHAR_BATCH_K8S_JOB", "importer-29470650", "ADHAR_BATCH_ACTOR", "system:serviceaccount:cronjob",
                "POD_NAME", "importer-29470650-x7k2p", "POD_UID", "pod-uid-1", "NODE_NAME", "worker-2"));
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/importer-29470650").willReturn(
                okJson(KubernetesOwnerLookupTest.JOB)));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/importer-29470650-x7k2p").willReturn(
                okJson(KubernetesOwnerLookupTest.POD)));
    }

    private RunContext resolve() {
        return new RunContextResolver(env::get, KubernetesOwnerLookupTest.lookup(API.baseUrl()), properties).resolve();
    }

    @Test
    @DisplayName("a scheduled run: business date from the owner Job's nominal time in the job's zone, digest from the pod")
    void scheduledRun() {
        RunContext context = resolve();

        assertThat(context.jobName()).isEqualTo("importJob");
        assertThat(context.batchJobName()).isEqualTo("importer");
        assertThat(context.trigger()).isEqualTo(RunTrigger.SCHEDULE);
        assertThat(context.role()).isEqualTo(BatchRole.SINGLE);
        assertThat(context.scheduledTime()).contains(OffsetDateTime.parse("2026-11-03T17:30:00Z"));
        assertThat(context.zone()).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(context.businessDate()).as("01:30 on the 4th in Singapore").contains(LocalDate.of(2026, 11, 4));
        assertThat(context.k8sJobUid()).isEqualTo("job-uid-1");
        assertThat(context.attempt()).as("two pods already failed").isEqualTo(3);
        assertThat(context.imageDigest()).isEqualTo("sha256:7f3c1a9d");
        assertThat(context.actor()).isEqualTo("system:serviceaccount:cronjob");
        assertThat(context.podName()).isEqualTo("importer-29470650-x7k2p");
        assertThat(context.podUid()).isEqualTo("pod-uid-1");
        assertThat(context.nodeName()).isEqualTo("worker-2");
        assertThat(context.correlationId()).isEqualTo("job-uid-1");
        assertThat(context.partitionIndex()).isEmpty();
    }

    @Test
    @DisplayName("a scheduled gang member: the business date from its Volcano Job, the attempt from the gang's restarts")
    void scheduledGangMember() {
        // ADHAR_BATCH_K8S_JOB is the pod's volcano.sh/job-name label on a gang (composition batchjob-kubernetes).
        env.putAll(Map.of("ADHAR_BATCH_K8S_JOB", "settlement-29470650", "POD_NAME", "settlement-29470650-worker-2"));
        API.stubFor(get("/apis/batch.volcano.sh/v1alpha1/namespaces/team-a-prod/jobs/settlement-29470650")
                .willReturn(okJson(KubernetesOwnerLookupTest.VOLCANO_JOB)));

        RunContext context = resolve();

        assertThat(context.scheduledTime()).contains(OffsetDateTime.parse("2026-11-04T01:30:00+08:00"));
        assertThat(context.businessDate()).contains(LocalDate.of(2026, 11, 4));
        assertThat(context.k8sJobUid()).isEqualTo("vcjob-uid-1");
        assertThat(context.attempt()).as("one RestartJob so far").isEqualTo(2);
    }

    @Test
    @DisplayName("a manual run has no scheduled time even when its Job has one, and survives an API that fails")
    void manualRun() {
        env.put("ADHAR_BATCH_TRIGGER", "manual");
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/importer-29470650")
                .willReturn(aResponse().withStatus(500)));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/importer-29470650-x7k2p")
                .willReturn(aResponse().withStatus(500)));

        RunContext context = resolve();

        assertThat(context.trigger()).isEqualTo(RunTrigger.MANUAL);
        assertThat(context.scheduledTime()).isEmpty();
        assertThat(context.businessDate()).isEmpty();
        assertThat(context.imageDigest()).isEmpty();
        assertThat(context.attempt()).isEqualTo(1);
        assertThat(context.correlationId()).isEqualTo("pod-uid-1");
    }

    @Test
    @DisplayName("a scheduled run whose owner Job cannot be read fails to start (exit 1, retried), never inventing a date")
    void scheduledRunNeedsTheApi() {
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/importer-29470650")
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::resolve).isInstanceOf(KubernetesLookupException.class).hasMessageContaining("503");
    }

    @Test
    @DisplayName("outside a Job and a cluster: defaults, UTC, a random correlation id")
    void bareProcess() {
        env.clear();
        env.put("HOSTNAME", "laptop");
        properties.setJob("importJob");

        RunContext context = new RunContextResolver(env::get, KubernetesOwnerLookup.unavailable(), properties)
                .resolve();

        assertThat(context.trigger()).isEqualTo(RunTrigger.MANUAL);
        assertThat(context.zone()).isEqualTo(ZoneOffset.UTC);
        assertThat(context.batchJobName()).isEqualTo("importJob");
        assertThat(context.podName()).isEqualTo("laptop");
        assertThat(context.correlationId()).hasSize(36);
    }

    @Test
    @DisplayName("an Indexed Job pod is the manager at index 0 and a worker at any other index")
    void indexedRole() {
        properties.setRole(BatchRole.INDEXED);
        env.put("ADHAR_BATCH_TRIGGER", "manual");
        env.put("JOB_COMPLETION_INDEX", "0");
        assertThat(resolve().role()).isEqualTo(BatchRole.MANAGER);
        env.put("JOB_COMPLETION_INDEX", "3");
        RunContext worker = resolve();
        assertThat(worker.role()).isEqualTo(BatchRole.WORKER);
        assertThat(worker.partitionIndex()).isEqualTo(OptionalInt.of(3));
        assertThat(BatchRole.parse(" Manager ")).isEqualTo(BatchRole.MANAGER);
        assertThat(BatchRole.parse(null)).isEqualTo(BatchRole.SINGLE);
        assertThat(BatchRole.WORKER.label()).isEqualTo("worker");
        assertThatThrownBy(() -> BatchRole.parse("leader")).hasMessageContaining("expected single, manager");
    }

    @Test
    @DisplayName("an unknown trigger or time zone is an invalid request (exit 2), named in the message")
    void invalidEnvironment() {
        env.put("ADHAR_BATCH_TRIGGER", "cron");
        assertThatThrownBy(this::resolve).asInstanceOf(type(ParameterValidationException.class))
                .satisfies(ex -> assertThat(ex.getMessage()).contains("Unknown run trigger 'cron'"));
        env.put("ADHAR_BATCH_TRIGGER", "backfill");
        env.put("ADHAR_BATCH_TIME_ZONE", "Mars/Olympus");
        assertThatThrownBy(this::resolve).isInstanceOf(ParameterValidationException.class)
                .hasMessageContaining("Mars/Olympus");
        assertThat(RunTrigger.parse("Workflow")).isEqualTo(RunTrigger.WORKFLOW);
        assertThat(RunTrigger.EMBEDDED.label()).isEqualTo("embedded");
    }
}
