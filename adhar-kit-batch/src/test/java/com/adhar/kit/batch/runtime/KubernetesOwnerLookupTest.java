package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.KubernetesLookupException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.OwnerJob;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.Pod;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KubernetesOwnerLookup} against a fake API server: the requests it makes (path, bearer
 * token) and what it reads from the answers.
 */
class KubernetesOwnerLookupTest {

    static final WireMockServer API = new WireMockServer(options().dynamicPort());

    static final String JOB = """
            {"metadata":{"name":"importer-29470650","uid":"job-uid-1","annotations":{
              "batch.kubernetes.io/cronjob-scheduled-timestamp":"2026-11-03T17:30:00Z"}},
             "status":{"failed":2,"active":1}}""";

    static final String POD = """
            {"metadata":{"name":"importer-29470650-x7k2p","uid":"pod-uid-1"},
             "status":{"phase":"Running","containerStatuses":[
               {"name":"istio-proxy","imageID":"docker.io/istio/proxyv2@sha256:0000"},
               {"name":"batch","imageID":"harbor/library/importer@sha256:7f3c1a9d"}]}}""";

    private KubernetesOwnerLookup lookup;

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
        lookup = lookup(API.baseUrl());
    }

    static KubernetesOwnerLookup lookup(String baseUrl) {
        return new KubernetesOwnerLookup(URI.create(baseUrl), () -> "sa-token", "team-a-prod",
                HttpClient.newHttpClient(), Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("the owner Job: its uid, its CronJob's nominal time and its failed pods, read with the SA token")
    void ownerJob() {
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/importer-29470650").willReturn(okJson(JOB)));

        Optional<OwnerJob> job = lookup.job("importer-29470650");

        assertThat(job).contains(new OwnerJob("job-uid-1", Optional.of(OffsetDateTime.parse("2026-11-03T17:30:00Z")),
                2));
        API.verify(getRequestedFor(urlEqualTo("/apis/batch/v1/namespaces/team-a-prod/jobs/importer-29470650"))
                .withHeader("Authorization", equalTo("Bearer sa-token")));
    }

    @Test
    @DisplayName("a Job no CronJob created has no scheduled time; a malformed annotation is no time either")
    void unscheduledJob() {
        API.stubFor(get(urlEqualTo("/apis/batch/v1/namespaces/team-a-prod/jobs/manual"))
                .willReturn(okJson("{\"metadata\":{\"uid\":\"u\"}}")));
        API.stubFor(get(urlEqualTo("/apis/batch/v1/namespaces/team-a-prod/jobs/odd")).willReturn(okJson(
                "{\"metadata\":{\"uid\":\"u\",\"annotations\":{\"" + KubernetesOwnerLookup.SCHEDULED_TIMESTAMP
                        + "\":\"yesterday\"}}}")));

        assertThat(lookup.job("manual")).contains(new OwnerJob("u", Optional.empty(), 0));
        assertThat(lookup.job("odd")).contains(new OwnerJob("u", Optional.empty(), 0));
    }

    @Test
    @DisplayName("a pod: the run container's digest, its phase, whether it is terminating")
    void pod() {
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/importer-29470650-x7k2p").willReturn(okJson(POD)));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/leaving").willReturn(okJson(
                "{\"metadata\":{\"uid\":\"u2\",\"deletionTimestamp\":\"2026-11-03T17:31:00Z\"},"
                        + "\"status\":{\"phase\":\"Running\"}}")));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/done").willReturn(okJson(
                "{\"metadata\":{\"uid\":\"u3\"},\"status\":{\"phase\":\"Failed\",\"containerStatuses\":"
                        + "[{\"name\":\"main\",\"imageID\":\"importer:latest\"}]}}")));

        assertThat(lookup.pod("importer-29470650-x7k2p")).contains(new Pod("pod-uid-1", "Running", false,
                "sha256:7f3c1a9d"));
        assertThat(lookup.pod("leaving")).get().satisfies(pod -> {
            assertThat(pod.terminating()).isTrue();
            assertThat(pod.isTerminal()).isFalse();
            assertThat(pod.imageDigest()).isEmpty();
        });
        assertThat(lookup.pod("done")).get().satisfies(pod -> {
            assertThat(pod.isTerminal()).isTrue();
            assertThat(pod.imageDigest()).as("a tag is no digest").isEmpty();
        });
    }

    @Test
    @DisplayName("404 is 'not there'; any other answer, or no answer, is an error that names the cause")
    void errors() {
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/gone").willReturn(aResponse().withStatus(404)));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/secret").willReturn(aResponse().withStatus(403)));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/garbled").willReturn(okJson("{not json")));

        assertThat(lookup.pod("gone")).isEmpty();
        assertThatThrownBy(() -> lookup.pod("secret")).isInstanceOf(KubernetesLookupException.class)
                .hasMessageContaining("403").hasMessageContaining("needs get on jobs and pods");
        assertThatThrownBy(() -> lookup.pod("garbled")).isInstanceOf(KubernetesLookupException.class)
                .hasMessageContaining("Invalid JSON");
        assertThatThrownBy(() -> lookup("http://127.0.0.1:1").job("x")).isInstanceOf(KubernetesLookupException.class)
                .hasMessageContaining("failed");
    }

    @Test
    @DisplayName("outside a cluster nothing is asked and everything is unknown")
    void unavailable(@TempDir Path serviceAccount) throws Exception {
        assertThat(KubernetesOwnerLookup.unavailable().isAvailable()).isFalse();
        assertThat(KubernetesOwnerLookup.unavailable().job("x")).isEmpty();
        assertThat(KubernetesOwnerLookup.unavailable().pod("x")).isEmpty();
        assertThat(KubernetesOwnerLookup.inCluster(Map.<String, String>of()::get, serviceAccount).isAvailable())
                .as("no KUBERNETES_SERVICE_HOST").isFalse();
        assertThat(KubernetesOwnerLookup.inCluster(Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1")::get,
                serviceAccount).isAvailable()).as("no token").isFalse();

        Files.writeString(serviceAccount.resolve("token"), "sa-token\n");
        Files.writeString(serviceAccount.resolve("namespace"), "team-a-prod");
        assertThat(KubernetesOwnerLookup.inCluster(Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1",
                "KUBERNETES_SERVICE_PORT", "6443")::get, serviceAccount).isAvailable()).isTrue();
    }

    @Test
    @DisplayName("in a cluster it uses the mounted namespace, token and CA")
    void inCluster(@TempDir Path serviceAccount) throws Exception {
        Files.writeString(serviceAccount.resolve("token"), "mounted-token\n");
        Files.writeString(serviceAccount.resolve("namespace"), "team-a-prod\n");
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/j").willReturn(okJson("{\"metadata\":{}}")));
        URI base = URI.create(API.baseUrl());
        KubernetesOwnerLookup inCluster = KubernetesOwnerLookup.inCluster(Map.of("KUBERNETES_SERVICE_HOST",
                base.getHost(), "KUBERNETES_SERVICE_PORT", String.valueOf(base.getPort()))::get, serviceAccount);

        assertThat(inCluster.isAvailable()).isTrue();
        assertThatThrownBy(() -> inCluster.job("j")).as("https against a plain-HTTP server")
                .isInstanceOf(KubernetesLookupException.class);
        Files.writeString(serviceAccount.resolve("ca.crt"), "not a certificate");
        assertThatThrownBy(() -> KubernetesOwnerLookup.inCluster(Map.of("KUBERNETES_SERVICE_HOST", "h")::get,
                serviceAccount)).isInstanceOf(KubernetesLookupException.class).hasMessageContaining("cluster CA");
    }
}
