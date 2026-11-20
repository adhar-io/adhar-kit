package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.runtime.Json;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.ApiAccess;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Writes the in-service schedule report (design 0025 §5.20.8): how the Console and the CLI see a
 * service's schedules without reading its database (INV-6).
 *
 * <ul>
 *   <li><b>Object.</b> {@code ConfigMap <scheduler-name>-batch-schedules} in the service's own
 *       namespace, labelled {@value #LABEL}{@code : "true"}, {@value #APPLICATION_LABEL} and
 *       {@value #MANAGED_BY_LABEL}{@code : adhar-kit}, with one key, {@value #DATA_KEY}, report
 *       version {@value #VERSION}. Every field is optional to a reader.</li>
 *   <li><b>Written</b> after a fire ends and after trigger changes, at most every
 *       {@code report.min-interval}: a request inside that interval is deferred, and requests
 *       meanwhile collapse into that one write of the latest state. Through the JDK
 *       {@code HttpClient} and the pod's ServiceAccount token, as the runner's owner lookup reads
 *       its Job: {@code PUT} the ConfigMap, and on {@code 404} {@code POST} it.</li>
 *   <li><b>Capped</b> at {@value #MAX_BYTES} bytes: the jobs' {@code lastRun} go first, then the
 *       triggers beyond {@value #MAX_TRIGGERS} per job, then whole jobs from the end; {@code truncated}
 *       says so.</li>
 *   <li><b>Advisory.</b> A write that fails (a {@code 403} without the template's Role, an API
 *       server that is down) is logged at DEBUG and counted ({@value SchedulerMetrics#REPORT_FAILURES}),
 *       and never thrown: scheduling does not depend on it, as it does not on the event bus.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class ScheduleReportWriter implements AutoCloseable {

    /** The report's version. */
    public static final int VERSION = 1;

    /** The ConfigMap's key. */
    public static final String DATA_KEY = "schedules.json";

    /** The label the Console and CLI select reports by. */
    public static final String LABEL = "adhar.io/batch-schedules";

    /** The application the report belongs to. */
    public static final String APPLICATION_LABEL = "adhar.io/application";

    /** Who manages the ConfigMap. */
    public static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";

    /** The default name's suffix: {@code <scheduler-name>-batch-schedules}. */
    public static final String NAME_SUFFIX = "-batch-schedules";

    /** The largest report written, in bytes of JSON. */
    public static final int MAX_BYTES = 256 * 1024;

    /** The most triggers per job a capped report keeps. */
    public static final int MAX_TRIGGERS = 50;

    /** The longest ConfigMap name. */
    private static final int MAX_NAME = 253;

    /** The longest label value. */
    private static final int MAX_LABEL_VALUE = 63;

    private static final Pattern CONFIG_MAP_NAME =
            Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ApiAccess api;
    private final String name;
    private final String application;
    private final Duration minInterval;
    private final Supplier<Report> source;
    private final SchedulerMetrics metrics;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile Instant lastWrite;
    private volatile boolean closed;

    /**
     * @param api         the Kubernetes API as the pod reaches it, {@code null} for a writer that writes nothing
     * @param name        the ConfigMap's name
     * @param application the {@value #APPLICATION_LABEL} label's value
     * @param minInterval the least time between two writes
     * @param source      builds the report from the current state
     * @param metrics     counts the failed writes
     * @param clock       the time source
     * @param executor    runs the deferred writes (one thread is enough); shut down by {@link #close()}
     */
    public ScheduleReportWriter(ApiAccess api, String name, String application, Duration minInterval,
                                Supplier<Report> source, SchedulerMetrics metrics, Clock clock,
                                ScheduledExecutorService executor) {
        this.api = api;
        this.name = name;
        this.application = application;
        this.minInterval = minInterval;
        this.source = source;
        this.metrics = metrics;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * @param schedulerName {@code adhar.batch.quartz.scheduler-name}, which defaults to
     *                      {@code spring.application.name} and so may be any text
     * @return {@code <scheduler-name>-batch-schedules} as a valid ConfigMap name (a DNS-1123
     *         subdomain): lower case, every other character a {@code -}. The API server answers
     *         {@code 422} to {@code importService-batch-schedules} on every write, which this
     *         advisory writer would only count.
     */
    public static String defaultName(String schedulerName) {
        String name = schedulerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-").replaceAll("^[^a-z0-9]+", "");
        int max = MAX_NAME - NAME_SUFFIX.length();
        if (name.length() > max) {
            name = name.substring(0, max);
        }
        name = name.replaceAll("[^a-z0-9]+$", "");
        return (name.isEmpty() ? "scheduler" : name) + NAME_SUFFIX;
    }

    /**
     * @param name a name
     * @return whether the API server takes it as a ConfigMap's name (a DNS-1123 subdomain)
     */
    public static boolean isConfigMapName(String name) {
        return name != null && name.length() <= MAX_NAME && CONFIG_MAP_NAME.matcher(name).matches();
    }

    /**
     * @param value a label's value as given
     * @return it as a valid label value (at most 63 of {@code [A-Za-z0-9._-]}, starting and ending
     *         alphanumeric), {@code null} when nothing of it remains: an invalid value would make
     *         the API server refuse the whole ConfigMap
     */
    static String labelValue(String value) {
        if (value == null) {
            return null;
        }
        String label = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^[^A-Za-z0-9]+", "");
        if (label.length() > MAX_LABEL_VALUE) {
            label = label.substring(0, MAX_LABEL_VALUE);
        }
        label = label.replaceAll("[^A-Za-z0-9]+$", "");
        return label.isEmpty() ? null : label;
    }

    /**
     * @return whether it writes at all
     */
    public boolean isEnabled() {
        return api != null;
    }

    /**
     * @return the ConfigMap's name
     */
    public String name() {
        return name;
    }

    /**
     * Asks for a write of the current state: now, or once {@code min-interval} has passed since the
     * last write. Requests while one is pending collapse into it. Never blocks, never throws.
     */
    public void request() {
        if (api == null || closed || !pending.compareAndSet(false, true)) {
            return;
        }
        Instant last = lastWrite;
        long delay = last == null ? 0
                : Math.max(0, minInterval.minus(Duration.between(last, clock.instant())).toMillis());
        try {
            executor.schedule(this::writePending, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            pending.set(false);
        }
    }

    private void writePending() {
        pending.set(false);
        write();
    }

    /**
     * Writes the current state now.
     *
     * @return whether the API server took it
     */
    public boolean write() {
        if (api == null) {
            return false;
        }
        lastWrite = clock.instant();
        try {
            String body = configMap(toJson(capped(source.get())));
            HttpResponse<String> response = send("PUT", itemPath(), body);
            if (response.statusCode() == 404) {
                response = send("POST", collectionPath(), body);
                if (response.statusCode() == 409) {
                    response = send("PUT", itemPath(), body);
                }
            }
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            failed("the API server answered " + response.statusCode()
                    + (response.statusCode() == 403 ? " (the ServiceAccount needs create on configmaps and "
                    + "get/update/patch on " + name + ")" : ""));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failed("interrupted");
        } catch (IOException | RuntimeException ex) {
            failed(ex.toString());
        }
        return false;
    }

    private void failed(String why) {
        metrics.reportFailed();
        log.debug("Could not write the schedule report {} in {}: {}", name, api.namespace(), why);
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api.apiServer() + path)).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + api.token().get())
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return api.client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String collectionPath() {
        return "/api/v1/namespaces/" + encode(api.namespace()) + "/configmaps";
    }

    private String itemPath() {
        return collectionPath() + "/" + encode(name);
    }

    String configMap(String report) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(LABEL, "true");
        String app = labelValue(application);
        if (app != null) {
            labels.put(APPLICATION_LABEL, app);
        }
        labels.put(MANAGED_BY_LABEL, "adhar-kit");
        StringJoiner labelJson = new StringJoiner(",", "{", "}");
        labels.forEach((key, value) -> labelJson.add(Json.quote(key) + ":" + Json.quote(value)));
        return "{\"apiVersion\":\"v1\",\"kind\":\"ConfigMap\",\"metadata\":{\"name\":" + Json.quote(name)
                + ",\"namespace\":" + Json.quote(api.namespace()) + ",\"labels\":" + labelJson + "},\"data\":{"
                + Json.quote(DATA_KEY) + ":" + Json.quote(report) + "}}";
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // The report, version 1
    // ------------------------------------------------------------------

    /**
     * @param report a report
     * @return it, cut to {@value #MAX_BYTES} bytes of JSON: {@code lastRun} first, then the triggers
     *         beyond {@value #MAX_TRIGGERS} per job, then jobs from the end; {@code truncated} set
     *         when anything was cut
     */
    public static Report capped(Report report) {
        if (fits(report)) {
            return report;
        }
        List<Job> withoutRuns = new ArrayList<>();
        report.jobs().forEach(job -> withoutRuns.add(new Job(job.job(), job.name(), job.triggers(), null)));
        Report cut = report.withJobs(withoutRuns);
        if (fits(cut)) {
            return cut;
        }
        List<Job> fewerTriggers = new ArrayList<>();
        cut.jobs().forEach(job -> fewerTriggers.add(new Job(job.job(), job.name(),
                job.triggers().subList(0, Math.min(MAX_TRIGGERS, job.triggers().size())), null)));
        cut = cut.withJobs(fewerTriggers);
        int size = size(cut);
        while (size > MAX_BYTES && !cut.jobs().isEmpty()) {
            // Keep the share of the jobs that fits, at least one fewer each round.
            int jobs = cut.jobs().size();
            int keep = (int) Math.min(jobs - 1L, (long) jobs * MAX_BYTES / size);
            cut = cut.withJobs(cut.jobs().subList(0, keep));
            size = size(cut);
        }
        return cut;
    }

    private static boolean fits(Report report) {
        return size(report) <= MAX_BYTES;
    }

    private static int size(Report report) {
        return toJson(report).getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * @param report a report
     * @return it as {@value #DATA_KEY} holds it: fields in a fixed order, absent ones left out
     */
    public static String toJson(Report report) {
        Fields doc = new Fields();
        doc.number("v", report.v()).string("engine", report.engine()).string("scheduler", report.scheduler())
                .time("updated", report.updated()).bool("truncated", report.truncated());
        StringJoiner instances = new StringJoiner(",", "[", "]");
        report.instances().forEach(instance -> instances.add(new Fields().string("id", instance.id())
                .time("checkin", instance.checkin()).toString()));
        doc.raw("instances", instances.toString());
        StringJoiner jobs = new StringJoiner(",", "[", "]");
        for (Job job : report.jobs()) {
            Fields entry = new Fields().string("job", job.job()).string("name", job.name());
            StringJoiner triggers = new StringJoiner(",", "[", "]");
            for (Trigger trigger : job.triggers()) {
                Fields t = new Fields().string("key", trigger.key()).string("kind", trigger.kind())
                        .string("expression", trigger.expression()).string("every", trigger.every())
                        .time("start", trigger.start()).string("from", trigger.from()).string("to", trigger.to());
                if (trigger.days() != null && !trigger.days().isEmpty()) {
                    StringJoiner days = new StringJoiner(",", "[", "]");
                    trigger.days().forEach(day -> days.add(Json.quote(day)));
                    t.raw("days", days.toString());
                }
                t.string("timeZone", trigger.timeZone()).string("calendar", trigger.calendar())
                        .string("misfire", trigger.misfire()).string("state", trigger.state())
                        .time("previous", trigger.previous()).time("next", trigger.next());
                triggers.add(t.toString());
            }
            entry.raw("triggers", triggers.toString());
            LastRun run = job.lastRun();
            if (run != null) {
                entry.raw("lastRun", new Fields().string("run", run.run()).number("executionId", run.executionId())
                        .string("status", run.status()).string("outcome", run.outcome())
                        .string("trigger", run.trigger()).time("start", run.start()).time("end", run.end()).toString());
            }
            jobs.add(entry.toString());
        }
        doc.raw("jobs", jobs.toString());
        return doc.toString();
    }

    /**
     * The report.
     *
     * @param v         its version, {@value #VERSION}
     * @param engine    {@code quartz}
     * @param scheduler the scheduler's name
     * @param updated   when it was built
     * @param truncated whether the cap cut it
     * @param instances the replicas checked in to the cluster
     * @param jobs      the jobs, by bean name
     */
    public record Report(int v, String engine, String scheduler, Instant updated, boolean truncated,
                         List<Instance> instances, List<Job> jobs) {

        public Report {
            instances = List.copyOf(instances);
            jobs = List.copyOf(jobs);
        }

        Report withJobs(List<Job> cut) {
            return new Report(v, engine, scheduler, updated, true, instances, cut);
        }
    }

    /**
     * A replica.
     *
     * @param id      its Quartz instance id
     * @param checkin its last check-in
     */
    public record Instance(String id, Instant checkin) {
    }

    /**
     * A job.
     *
     * @param job      its bean name
     * @param name     its schedule name (naming v1's {@code <batchjob>})
     * @param triggers its triggers
     * @param lastRun  its last run, {@code null} when it never ran
     */
    public record Job(String job, String name, List<Trigger> triggers, LastRun lastRun) {

        public Job {
            triggers = List.copyOf(triggers);
        }
    }

    /**
     * A trigger, with the fields of its kind under their property names.
     *
     * @param key        {@code <group>.<name>}
     * @param kind       {@code cron}, {@code interval}, {@code daily}; {@code now}, {@code at} or {@code resume} for a runtime trigger
     * @param expression {@code cron}: the Quartz expression
     * @param every      {@code interval}, {@code daily}: an ISO-8601 duration
     * @param start      {@code interval}: the grid's anchor
     * @param from       {@code daily}: the window's first time of day ({@code 09:00})
     * @param to         {@code daily}: its last
     * @param days       {@code daily}: day names, {@code MON} ... {@code SUN}
     * @param timeZone   the trigger's zone
     * @param calendar   its calendar
     * @param misfire    its misfire policy
     * @param state      Quartz's state ({@code NORMAL}, {@code PAUSED}, {@code BLOCKED}, ...)
     * @param previous   its last fire
     * @param next       its next fire
     */
    public record Trigger(String key, String kind, String expression, String every, Instant start, String from,
                          String to, List<String> days, String timeZone, String calendar, String misfire,
                          String state, Instant previous, Instant next) {
    }

    /**
     * A job's last run.
     *
     * @param run         its run name
     * @param executionId its execution
     * @param status      Spring Batch's status
     * @param outcome     {@code completed}, {@code failed}, {@code stopped}, {@code abandoned},
     *                    {@code running} or {@code unknown}
     * @param trigger     the trigger key that started it
     * @param start       when it started
     * @param end         when it ended
     */
    public record LastRun(String run, Long executionId, String status, String outcome, String trigger,
                          Instant start, Instant end) {
    }

    /**
     * A JSON object's fields, in order, absent values left out.
     */
    private static final class Fields {

        private final StringJoiner object = new StringJoiner(",", "{", "}");

        Fields string(String key, String value) {
            if (value != null) {
                object.add(Json.quote(key) + ":" + Json.quote(Json.sanitize(value)));
            }
            return this;
        }

        Fields time(String key, Instant value) {
            return string(key, value == null ? null
                    : DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.SECONDS)));
        }

        Fields number(String key, Number value) {
            if (value != null) {
                object.add(Json.quote(key) + ":" + value);
            }
            return this;
        }

        Fields bool(String key, boolean value) {
            object.add(Json.quote(key) + ":" + value);
            return this;
        }

        Fields raw(String key, String json) {
            object.add(Json.quote(key) + ":" + json);
            return this;
        }

        @Override
        public String toString() {
            return object.toString();
        }
    }
}
