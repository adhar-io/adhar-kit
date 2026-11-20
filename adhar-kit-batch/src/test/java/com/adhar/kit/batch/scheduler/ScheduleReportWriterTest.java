package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.runtime.Json;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.ApiAccess;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Instance;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Job;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.LastRun;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Report;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Trigger;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KIT-Q.7 (design 0025 §5.20.8): the schedule report against a fake API server. Created, then
 * updated; capped at 256 KiB with {@code truncated}; a 403 (no Role) or a dead API server counted
 * and never thrown; writes at most every {@code min-interval}.
 */
class ScheduleReportWriterTest {

    static final WireMockServer API = new WireMockServer(options().dynamicPort());

    private static final String ITEM = "/api/v1/namespaces/team-a/configmaps/importer-batch-schedules";
    private static final String COLLECTION = "/api/v1/namespaces/team-a/configmaps";
    private static final Instant NOW = Instant.parse("2026-11-14T09:00:05Z");

    /** The report of design 0025 §5.20.8, as the Console and CLI read it. */
    private static final String SPEC_EXAMPLE = """
            {"v":1,"engine":"quartz","scheduler":"importer","updated":"2026-11-14T09:00:05Z","truncated":false,\
            "instances":[{"id":"importer-6c9f7d-x2k4p1731574800123","checkin":"2026-11-14T09:00:01Z"}],\
            "jobs":[{"job":"importJob","name":"import-job",\
            "triggers":[{"key":"adhar-batch-config.import-job.nightly","kind":"cron","expression":"0 30 1 ? * *",\
            "timeZone":"Asia/Singapore","calendar":"sg-business","misfire":"latest",\
            "state":"NORMAL","previous":"2026-11-13T17:30:00Z","next":"2026-11-16T17:30:00Z"}],\
            "lastRun":{"run":"import-job-09932cb3","executionId":97,"status":"COMPLETED","outcome":"completed",\
            "trigger":"adhar-batch-config.import-job.nightly","start":"2026-11-13T17:30:00Z","end":"2026-11-13T17:41:12Z"}}]}""";

    private final CountingMetrics metrics = new CountingMetrics();

    @BeforeAll
    static void start() {
        API.start();
    }

    @AfterAll
    static void stop() {
        API.stop();
    }

    @BeforeEach
    void reset() {
        API.resetAll();
    }

    private static Report specExample() {
        Trigger nightly = new Trigger("adhar-batch-config.import-job.nightly", "cron", "0 30 1 ? * *", null, null, null,
                null, null, "Asia/Singapore", "sg-business", "latest", "NORMAL",
                Instant.parse("2026-11-13T17:30:00Z"), Instant.parse("2026-11-16T17:30:00Z"));
        LastRun lastRun = new LastRun("import-job-09932cb3", 97L, "COMPLETED", "completed",
                "adhar-batch-config.import-job.nightly", Instant.parse("2026-11-13T17:30:00Z"),
                Instant.parse("2026-11-13T17:41:12.400Z"));
        return new Report(1, "quartz", "importer", NOW, false,
                List.of(new Instance("importer-6c9f7d-x2k4p1731574800123", Instant.parse("2026-11-14T09:00:01Z"))),
                List.of(new Job("importJob", "import-job", List.of(nightly), lastRun)));
    }

    private ScheduleReportWriter writer(Supplier<Report> source, ScheduledExecutorService executor) {
        ApiAccess api = new ApiAccess(URI.create(API.baseUrl()), () -> "sa-token", "team-a", HttpClient.newHttpClient());
        return new ScheduleReportWriter(api, "importer-batch-schedules", "importer", Duration.ofSeconds(10), source,
                metrics, Clock.fixed(NOW, ZoneOffset.UTC), executor);
    }

    private ScheduleReportWriter writer(Supplier<Report> source) {
        return writer(source, mock(ScheduledExecutorService.class));
    }

    private static String reportIn(String configMapJson) {
        return (String) Json.at(Json.parse(configMapJson), "data", ScheduleReportWriter.DATA_KEY);
    }

    @Test
    @DisplayName("report v1 is exactly the document of design 0025 §5.20.8: fields in order, absent ones left out")
    void theReportIsVersionOne() {
        assertThat(ScheduleReportWriter.toJson(specExample())).isEqualTo(SPEC_EXAMPLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("the first write creates the ConfigMap (PUT 404, then POST), later ones update it, with the pod's token")
    void createsThenUpdates() {
        API.stubFor(put(urlEqualTo(ITEM)).inScenario("cm").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(404)).willSetStateTo("created"));
        API.stubFor(post(urlEqualTo(COLLECTION)).willReturn(aResponse().withStatus(201)));
        API.stubFor(put(urlEqualTo(ITEM)).inScenario("cm").whenScenarioStateIs("created")
                .willReturn(aResponse().withStatus(200)));
        ScheduleReportWriter writer = writer(ScheduleReportWriterTest::specExample);

        assertThat(writer.write()).isTrue();
        assertThat(writer.write()).isTrue();

        API.verify(1, postRequestedFor(urlEqualTo(COLLECTION)).withHeader("Authorization", equalTo("Bearer sa-token")));
        API.verify(2, putRequestedFor(urlEqualTo(ITEM)).withHeader("Content-Type", equalTo("application/json")));
        String body = API.findAll(postRequestedFor(urlEqualTo(COLLECTION))).get(0).getBodyAsString();
        Object configMap = Json.parse(body);
        assertThat(Json.at(configMap, "kind")).isEqualTo("ConfigMap");
        assertThat(Json.at(configMap, "metadata", "name")).isEqualTo("importer-batch-schedules");
        assertThat(Json.at(configMap, "metadata", "namespace")).isEqualTo("team-a");
        assertThat((Map<String, Object>) Json.at(configMap, "metadata", "labels")).containsExactly(
                Map.entry("adhar.io/batch-schedules", (Object) "true"), Map.entry("adhar.io/application", (Object) "importer"),
                Map.entry("app.kubernetes.io/managed-by", (Object) "adhar-kit"));
        assertThat(reportIn(body)).isEqualTo(SPEC_EXAMPLE);
        assertThat(metrics.reportFailures).isZero();
    }

    @Test
    @DisplayName("a create that races another replica's (409) updates instead")
    void aRacingCreateUpdates() {
        API.stubFor(put(urlEqualTo(ITEM)).inScenario("race").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(404)).willSetStateTo("exists"));
        API.stubFor(post(urlEqualTo(COLLECTION)).willReturn(aResponse().withStatus(409)));
        API.stubFor(put(urlEqualTo(ITEM)).inScenario("race").whenScenarioStateIs("exists")
                .willReturn(aResponse().withStatus(200)));

        assertThat(writer(ScheduleReportWriterTest::specExample).write()).isTrue();
        API.verify(2, putRequestedFor(urlEqualTo(ITEM)));
    }

    @Test
    @DisplayName("a 403 (the ServiceAccount has no Role) is counted and never thrown; nothing else is tried")
    void aForbiddenWriteIsCounted() {
        API.stubFor(put(urlEqualTo(ITEM)).willReturn(aResponse().withStatus(403)));

        assertThat(writer(ScheduleReportWriterTest::specExample).write()).isFalse();

        assertThat(metrics.reportFailures).isEqualTo(1);
        API.verify(0, postRequestedFor(urlEqualTo(COLLECTION)));
    }

    @Test
    @DisplayName("an API server that is down, or a report that cannot be built, is counted and never thrown")
    void failuresAreCounted() {
        ApiAccess down = new ApiAccess(URI.create("http://127.0.0.1:1"), () -> "t", "team-a", HttpClient.newHttpClient());
        ScheduleReportWriter unreachable = new ScheduleReportWriter(down, "x", "importer", Duration.ofSeconds(10),
                ScheduleReportWriterTest::specExample, metrics, Clock.systemUTC(), mock(ScheduledExecutorService.class));
        ScheduleReportWriter broken = writer(() -> {
            throw new IllegalStateException("the store is down");
        });

        assertThat(unreachable.write()).isFalse();
        assertThat(broken.write()).isFalse();
        assertThat(metrics.reportFailures).isEqualTo(2);
    }

    @Test
    @DisplayName("writes come at most every min-interval: a request is deferred, and requests meanwhile collapse into it")
    void requestsAreRateLimited() {
        API.stubFor(put(urlEqualTo(ITEM)).willReturn(aResponse().withStatus(200)));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        List<Runnable> tasks = new ArrayList<>();
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(call -> {
            tasks.add(call.getArgument(0));
            return null;
        });
        ScheduleReportWriter writer = writer(ScheduleReportWriterTest::specExample, executor);

        writer.request();
        writer.request();
        verify(executor, times(1)).schedule(any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));

        tasks.get(0).run();
        API.verify(1, putRequestedFor(urlEqualTo(ITEM)));
        writer.request();
        verify(executor).schedule(any(Runnable.class), eq(10_000L), eq(TimeUnit.MILLISECONDS));

        writer.close();
        verify(executor).shutdownNow();
        writer.request();
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    @DisplayName("a writer without API access writes nothing; a request on a shut-down executor is dropped")
    void disabledWriters() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduleReportWriter disabled = new ScheduleReportWriter(null, "x", "a", Duration.ZERO,
                ScheduleReportWriterTest::specExample, metrics, Clock.systemUTC(), executor);
        disabled.request();

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.write()).isFalse();
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());

        ScheduledExecutorService rejecting = mock(ScheduledExecutorService.class);
        when(rejecting.schedule(any(Runnable.class), anyLong(), any()))
                .thenThrow(new java.util.concurrent.RejectedExecutionException("shut down"));
        ScheduleReportWriter writer = writer(ScheduleReportWriterTest::specExample, rejecting);
        writer.request();
        writer.request();
        verify(rejecting, times(2)).schedule(any(Runnable.class), anyLong(), any());
        assertThat(writer.name()).isEqualTo("importer-batch-schedules");
        assertThat(writer.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("over 256 KiB: lastRun goes first; the report is marked truncated")
    void capDropsLastRunFirst() {
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            List<Trigger> triggers = new ArrayList<>();
            for (int t = 0; t < (i == 0 ? 60 : 1); t++) {
                triggers.add(trigger(i, t));
            }
            jobs.add(new Job("job" + i, "job-" + i, triggers,
                    new LastRun("r".repeat(3_000), (long) i, "COMPLETED", "completed", null, null, null)));
        }
        Report report = report(jobs);

        Report capped = ScheduleReportWriter.capped(report);

        assertThat(size(report)).isGreaterThan(ScheduleReportWriter.MAX_BYTES);
        assertThat(size(capped)).isLessThanOrEqualTo(ScheduleReportWriter.MAX_BYTES);
        assertThat(capped.truncated()).isTrue();
        assertThat(capped.jobs()).hasSize(100).allSatisfy(job -> assertThat(job.lastRun()).isNull());
        assertThat(capped.jobs().get(0).triggers()).as("dropping lastRun was enough: no trigger is cut").hasSize(60);
        assertThat(ScheduleReportWriter.toJson(capped)).contains("\"truncated\":true");
    }

    @Test
    @DisplayName("over 256 KiB without lastRun: triggers beyond 50 per job go, then jobs from the end")
    void capThenDropsTriggersThenJobs() {
        List<Job> manyTriggers = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            List<Trigger> triggers = new ArrayList<>();
            for (int t = 0; t < 120; t++) {
                triggers.add(trigger(i, t));
            }
            manyTriggers.add(new Job("job" + i, "job-" + i, triggers, null));
        }
        Report fewer = ScheduleReportWriter.capped(report(manyTriggers));

        assertThat(fewer.truncated()).isTrue();
        assertThat(fewer.jobs()).hasSize(15).allSatisfy(job -> assertThat(job.triggers()).hasSize(50));
        assertThat(size(fewer)).isLessThanOrEqualTo(ScheduleReportWriter.MAX_BYTES);

        List<Job> manyJobs = new ArrayList<>(manyTriggers);
        for (int i = 15; i < 200; i++) {
            manyJobs.add(manyTriggers.get(i % 15));
        }
        Report cut = ScheduleReportWriter.capped(report(manyJobs));

        assertThat(cut.truncated()).isTrue();
        assertThat(cut.jobs()).hasSizeLessThan(200).isNotEmpty();
        assertThat(cut.jobs().get(0).job()).isEqualTo("job0");
        assertThat(size(cut)).isLessThanOrEqualTo(ScheduleReportWriter.MAX_BYTES);
        assertThat(ScheduleReportWriter.capped(specExample())).as("a report that fits is untouched").isEqualTo(specExample());
    }

    @Test
    @DisplayName("each kind's fields under their property names; lone surrogates are made text")
    void triggerFieldsOfEachKind() {
        Trigger interval = new Trigger("adhar-batch-config.poll.every", "interval", null, "PT5M", Instant.EPOCH, null,
                null, null, "UTC", null, "all", "NORMAL", null, null);
        Trigger daily = new Trigger("adhar-batch-config.poll.office", "daily", null, "PT15M", null, "09:00", "17:00",
                List.of("MON", "TUE"), "UTC", "weekdays", "skip", "PAUSED", null, null);
        Job job = new Job("pollJob", "poll\uD800", List.of(interval, daily), null);

        String json = ScheduleReportWriter.toJson(report(List.of(job)));

        assertThat(json).contains("{\"key\":\"adhar-batch-config.poll.every\",\"kind\":\"interval\",\"every\":\"PT5M\","
                + "\"start\":\"1970-01-01T00:00:00Z\",\"timeZone\":\"UTC\",\"misfire\":\"all\",\"state\":\"NORMAL\"}");
        assertThat(json).contains("\"every\":\"PT15M\",\"from\":\"09:00\",\"to\":\"17:00\",\"days\":[\"MON\",\"TUE\"]");
        assertThat(json).contains("\"name\":\"poll�\"");
        assertThat(Json.parse(json)).isInstanceOf(Map.class);
    }

    private static Trigger trigger(int job, int index) {
        return new Trigger("adhar-batch-config.job-" + job + ".trigger-" + index, "cron", "0 0/5 * ? * *", null, null,
                null, null, null, "Asia/Singapore", null, "latest", "NORMAL", NOW, NOW.plusSeconds(300));
    }

    private static Report report(List<Job> jobs) {
        return new Report(1, "quartz", "importer", NOW, false, List.of(), jobs);
    }

    private static int size(Report report) {
        return ScheduleReportWriter.toJson(report).getBytes(StandardCharsets.UTF_8).length;
    }

    /** Counts the failed writes. */
    static final class CountingMetrics implements SchedulerMetrics {

        volatile int reportFailures;

        @Override
        public void fired(String job, String trigger, SlotOutcome outcome) {
        }

        @Override
        public void nextFire(String job, String trigger, Instant next) {
        }

        @Override
        public void instances(int count) {
        }

        @Override
        public void reportFailed() {
            reportFailures++;
        }

        @Override
        public void calendarFailed(String calendar) {
        }
    }
}
