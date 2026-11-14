package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.OptimisticLockingFailureException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The manager's watch over its workers (design 0025 §5.10), one rule at a time, against a fake API
 * server: a running partition whose recorded owner pod is gone, replaced or ended is failed; anything
 * the watch cannot be sure of is left to the manager's timeout.
 */
class WorkerLivenessWatchTest {

    static final WireMockServer API = new WireMockServer(options().dynamicPort());

    private final JobRepository repository = mock(JobRepository.class);
    private final JobInstance instance = new JobInstance(1L, "importJob");
    private JobExecution execution;
    private WorkerLivenessWatch watch;

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
        execution = new JobExecution(7L, instance, new JobParameters());
        execution.setStatus(BatchStatus.STARTED);
        when(repository.getJobExecution(7L)).thenReturn(execution);
        watch = new WorkerLivenessWatch(repository, lookup(), "importStep", Duration.ofMillis(50));
    }

    private static KubernetesOwnerLookup lookup() {
        return new KubernetesOwnerLookup(URI.create(API.baseUrl()), () -> "sa-token", "team-a-prod",
                HttpClient.newHttpClient(), Duration.ofSeconds(5));
    }

    private StepExecution partition(long id, BatchStatus status, String pod, String uid) {
        StepExecution partition = new StepExecution(id, "importStep:partition" + id, execution);
        partition.setStatus(status);
        if (pod != null) {
            new PartitionOwner(id, pod, uid).writeTo(partition);
        }
        execution.addStepExecution(partition);
        return partition;
    }

    private static void pod(String name, String uid, String phase, boolean terminating) {
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/" + name).willReturn(okJson("{\"metadata\":{\"uid\":\""
                + uid + "\"" + (terminating ? ",\"deletionTimestamp\":\"2026-11-14T10:00:00Z\"" : "")
                + "},\"status\":{\"phase\":\"" + phase + "\"}}")));
    }

    @Test
    @DisplayName("a running partition whose worker pod is gone, replaced or ended is failed, with the reason")
    void orphanedPartitionsAreFailed() {
        StepExecution gone = partition(1, BatchStatus.STARTED, "importer-1-abcde", "");
        StepExecution replaced = partition(2, BatchStatus.STARTED, "settlement-worker-0", "uid-old");
        StepExecution ended = partition(3, BatchStatus.STARTING, "importer-3-fghij", "uid-3");
        pod("settlement-worker-0", "uid-new", "Running", false);
        pod("importer-3-fghij", "uid-3", "Failed", false);

        assertThat(watch.check(7L)).isEqualTo(3);

        assertThat(gone.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(gone.getEndTime()).isNotNull();
        assertThat(gone.getExitStatus().getExitDescription()).contains("worker pod importer-1-abcde is gone");
        assertThat(replaced.getExitStatus().getExitDescription()).contains("was replaced");
        assertThat(ended.getExitStatus().getExitDescription()).contains("has ended (Failed)");
        verify(repository).update(gone);
        verify(repository).update(replaced);
        verify(repository).update(ended);
    }

    @Test
    @DisplayName("a live, terminating or unclaimed partition, a finished one, or an earlier attempt's owner record is left alone")
    void nothingUncertainIsTouched() {
        StepExecution live = partition(1, BatchStatus.STARTED, "importer-1-abcde", "uid-1");
        StepExecution stopping = partition(2, BatchStatus.STARTED, "importer-2-abcde", "uid-2");
        StepExecution unclaimed = partition(3, BatchStatus.STARTING, null, null);
        StepExecution finished = partition(4, BatchStatus.FAILED, "importer-gone", "");
        StepExecution copied = partition(5, BatchStatus.STARTING, null, null);
        // A restart copied attempt 1's context, owner included, into this new step execution.
        new PartitionOwner(41L, "importer-gone", "").writeTo(copied);
        pod("importer-1-abcde", "uid-1", "Running", false);
        pod("importer-2-abcde", "uid-2", "Running", true);

        assertThat(watch.check(7L)).isZero();

        assertThat(live.getStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(stopping.getStatus()).as("its worker is stopping it").isEqualTo(BatchStatus.STARTED);
        assertThat(unclaimed.getStatus()).isEqualTo(BatchStatus.STARTING);
        assertThat(copied.getStatus()).isEqualTo(BatchStatus.STARTING);
        verify(repository, never()).update(any(StepExecution.class));
        assertThat(finished.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("an API that cannot be read, or a partition that committed meanwhile, decides nothing")
    void unsureMeansNothing() {
        StepExecution unreadable = partition(1, BatchStatus.STARTED, "importer-1-abcde", "");
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/importer-1-abcde").willReturn(aResponse().withStatus(403)));
        assertThat(watch.check(7L)).isZero();
        assertThat(unreadable.getStatus()).isEqualTo(BatchStatus.STARTED);

        API.resetAll();
        doThrow(new OptimisticLockingFailureException("moved")).when(repository).update(any(StepExecution.class));
        assertThat(watch.check(7L)).as("gone by the API, but its commit moved the version: alive").isZero();
    }

    @Test
    @DisplayName("started with the manager step, it checks on its own until closed; outside a cluster it never runs")
    void lifecycle() throws Exception {
        StepExecution manager = new StepExecution(99L, "importManager", execution);
        StepExecution orphan = partition(1, BatchStatus.STARTED, "importer-1-abcde", "");

        try (AutoCloseable running = watch.start(manager)) {
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10))
                    .until(() -> orphan.getStatus() == BatchStatus.FAILED);
        }

        StepExecution later = partition(2, BatchStatus.STARTED, "importer-2-abcde", "");
        Thread.sleep(200);
        assertThat(later.getStatus()).as("closed").isEqualTo(BatchStatus.STARTED);
        try (AutoCloseable none = new WorkerLivenessWatch(repository, KubernetesOwnerLookup.unavailable(), "importStep",
                Duration.ofMillis(10)).start(manager)) {
            Thread.sleep(100);
        }
        assertThat(later.getStatus()).isEqualTo(BatchStatus.STARTED);
    }
}
