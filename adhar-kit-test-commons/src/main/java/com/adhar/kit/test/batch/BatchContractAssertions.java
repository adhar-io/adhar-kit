package com.adhar.kit.test.batch;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import org.springframework.context.ApplicationContext;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The contract between a batch application's code and its {@code deploy/base/batchjob.yaml}
 * (design 0025 §5.19, §9.1), checked in the application's own build so a mismatch fails app-ci
 * instead of a run in production:
 * <ul>
 *   <li>the BatchJob declares exactly the parameters {@code @AdharBatchJob} declares, with the same
 *       type, identity, requirement, default, pattern, allowed values, source and sensitivity (the
 *       Console's form and the CLI check against the manifest; the runner validates against the code);</li>
 *   <li>its memory is at least {@value #JVM_FLOOR}, below which a Spring Batch JVM does not start
 *       reliably (design 0025 §14);</li>
 *   <li>its {@code terminationGracePeriodSeconds} exceeds {@code adhar.batch.shutdown.timeout}, or a
 *       drained pod is killed before it can record STOPPED.</li>
 * </ul>
 *
 * <pre>{@code
 * @AdharBatchTest
 * class BatchJobContractTest {
 *     @Test
 *     void theManifestMatchesTheCode(@Autowired ApplicationContext context) {
 *         BatchContractAssertions.assertContract(context, BatchContractAssertions.DEFAULT_MANIFEST);
 *     }
 * }
 * }</pre>
 *
 * Every difference is reported at once.
 *
 * @since 0.1.0
 */
public final class BatchContractAssertions {

    /** Where the template keeps the manifest, relative to the project root (the build's working directory). */
    public static final Path DEFAULT_MANIFEST = Path.of("deploy/base/batchjob.yaml");

    /** The smallest memory a run pod may have. */
    public static final String JVM_FLOOR = "1Gi";

    /** The XRD's defaults, used when the manifest leaves a field out. */
    static final String DEFAULT_MEMORY = "1536Mi";
    static final int DEFAULT_GRACE_SECONDS = 120;

    private static final Pattern QUANTITY = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(Ki|Mi|Gi|Ti|k|M|G|T)?$");

    private BatchContractAssertions() {
    }

    /**
     * One BatchJob of the manifest.
     *
     * @param name       {@code metadata.name}
     * @param job        {@code spec.parameters.job}, the Spring Batch job's bean name
     * @param parameters {@code spec.parameters.parameters}
     * @param memory     {@code spec.parameters.resources.memory}, or the XRD's default
     * @param grace      {@code spec.parameters.timeouts.terminationGracePeriodSeconds}, or the XRD's default
     */
    public record BatchJobManifest(String name, String job, List<Map<String, Object>> parameters, String memory,
                                   int grace) {
    }

    /**
     * Every check, with the context's specs and {@code adhar.batch.shutdown.timeout}, for each BatchJob
     * in the manifest.
     *
     * @param context  the batch application's context
     * @param manifest the BatchJob manifest
     */
    public static void assertContract(ApplicationContext context, Path manifest) {
        JobParameterSpecRegistry registry = context.getBean(JobParameterSpecRegistry.class);
        Duration shutdown = context.getBean(BatchProperties.class).getShutdown().getTimeout();
        List<String> problems = new ArrayList<>();
        for (BatchJobManifest job : read(manifest)) {
            JobParameterSpec spec = registry.find(job.job()).orElse(null);
            if (spec == null) {
                problems.add(job.name() + ": the application defines no job '" + job.job() + "' (spec.parameters.job)");
                continue;
            }
            problems.addAll(parameterProblems(spec, job));
            problems.addAll(resourceProblems(job, shutdown));
        }
        fail(manifest, problems);
    }

    /**
     * @param spec     the job's declared parameters
     * @param manifest the BatchJob manifest, holding a BatchJob of that job
     */
    public static void assertParametersMatch(JobParameterSpec spec, Path manifest) {
        fail(manifest, parameterProblems(spec, job(manifest, spec.jobName())));
    }

    /**
     * @param manifest        the BatchJob manifest
     * @param jobName         the job's bean name
     * @param shutdownTimeout {@code adhar.batch.shutdown.timeout}
     */
    public static void assertResources(Path manifest, String jobName, Duration shutdownTimeout) {
        fail(manifest, resourceProblems(job(manifest, jobName), shutdownTimeout));
    }

    /**
     * @param manifest a file of one or more YAML documents
     * @return its BatchJobs
     */
    @SuppressWarnings("unchecked")
    public static List<BatchJobManifest> read(Path manifest) {
        String text;
        try {
            text = Files.readString(manifest);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read the BatchJob manifest " + manifest.toAbsolutePath(), ex);
        }
        List<BatchJobManifest> jobs = new ArrayList<>();
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(options), new Representer(new DumperOptions()), new DumperOptions(),
                options, new NoTimestamps());
        for (Object document : yaml.loadAll(text)) {
            if (!(document instanceof Map<?, ?> object) || !"BatchJob".equals(object.get("kind"))) {
                continue;
            }
            Map<String, Object> metadata = map(object.get("metadata"));
            Map<String, Object> parameters = map(map(object.get("spec")).get("parameters"));
            Object declared = parameters.get("parameters");
            List<Map<String, Object>> params = new ArrayList<>();
            if (declared instanceof List<?> list) {
                list.forEach(entry -> params.add(map(entry)));
            }
            Object memory = map(parameters.get("resources")).get("memory");
            Object grace = map(parameters.get("timeouts")).get("terminationGracePeriodSeconds");
            jobs.add(new BatchJobManifest(String.valueOf(metadata.get("name")), String.valueOf(parameters.get("job")),
                    params, memory == null ? DEFAULT_MEMORY : String.valueOf(memory),
                    grace instanceof Number number ? number.intValue() : DEFAULT_GRACE_SECONDS));
        }
        if (jobs.isEmpty()) {
            throw new AssertionError(manifest + " holds no BatchJob");
        }
        return jobs;
    }

    /**
     * @param quantity a Kubernetes memory quantity ({@code 1536Mi}, {@code 2G})
     * @return its bytes
     */
    public static long bytes(String quantity) {
        Matcher matcher = QUANTITY.matcher(quantity.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'" + quantity + "' is not a memory quantity");
        }
        BigDecimal value = new BigDecimal(matcher.group(1));
        long unit = switch (Objects.requireNonNullElse(matcher.group(2), "")) {
            case "Ki" -> 1L << 10;
            case "Mi" -> 1L << 20;
            case "Gi" -> 1L << 30;
            case "Ti" -> 1L << 40;
            case "k" -> 1_000L;
            case "M" -> 1_000_000L;
            case "G" -> 1_000_000_000L;
            case "T" -> 1_000_000_000_000L;
            default -> 1L;
        };
        return value.multiply(BigDecimal.valueOf(unit)).longValue();
    }

    static List<String> parameterProblems(JobParameterSpec spec, BatchJobManifest job) {
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Object>> declared = new LinkedHashMap<>();
        job.parameters().forEach(param -> declared.put(String.valueOf(param.get("name")), param));
        for (JobParameterSpec.Param param : spec.parameters()) {
            Map<String, Object> entry = declared.remove(param.name());
            if (entry == null) {
                problems.add(job.name() + ": parameter '" + param.name() + "' is declared in @AdharBatchJob but not in the manifest");
                continue;
            }
            compare(problems, job, param.name(), "type", param.type().name().toLowerCase(Locale.ROOT),
                    text(entry.getOrDefault("type", "string")));
            compare(problems, job, param.name(), "identifying", String.valueOf(param.identifying()),
                    text(entry.getOrDefault("identifying", true)));
            compare(problems, job, param.name(), "required", String.valueOf(param.required()),
                    text(entry.getOrDefault("required", false)));
            compare(problems, job, param.name(), "default", param.defaultValue(), text(entry.get("default")));
            compare(problems, job, param.name(), "pattern", param.pattern(), text(entry.get("pattern")));
            compare(problems, job, param.name(), "allowed", String.valueOf(param.allowed()),
                    String.valueOf(entry.get("allowed") instanceof List<?> list ? list.stream().map(BatchContractAssertions::text).toList() : List.of()));
            compare(problems, job, param.name(), "source", param.source().name().toLowerCase(Locale.ROOT),
                    text(entry.getOrDefault("source", "input")));
            compare(problems, job, param.name(), "sensitive", String.valueOf(param.sensitive()),
                    text(entry.getOrDefault("sensitive", false)));
        }
        declared.keySet().forEach(name -> problems.add(job.name() + ": parameter '" + name
                + "' is in the manifest but not declared in @AdharBatchJob (the runner would refuse it)"));
        return problems;
    }

    static List<String> resourceProblems(BatchJobManifest job, Duration shutdownTimeout) {
        List<String> problems = new ArrayList<>();
        long memory;
        try {
            memory = bytes(job.memory());
        } catch (IllegalArgumentException ex) {
            problems.add(job.name() + ": resources.memory " + ex.getMessage());
            return problems;
        }
        if (memory < bytes(JVM_FLOOR)) {
            problems.add(job.name() + ": resources.memory " + job.memory() + " is below the JVM floor of " + JVM_FLOOR
                    + ": a Spring Batch JVM does not start reliably with less");
        }
        if (job.grace() <= shutdownTimeout.toSeconds()) {
            problems.add(job.name() + ": timeouts.terminationGracePeriodSeconds " + job.grace()
                    + " must exceed adhar.batch.shutdown.timeout (" + shutdownTimeout.toSeconds()
                    + "s), or a drained pod is killed before it records STOPPED");
        }
        return problems;
    }

    private static void compare(List<String> problems, BatchJobManifest job, String param, String field,
                                String code, String manifest) {
        if (!Objects.equals(blankToNull(code), blankToNull(manifest))) {
            problems.add(job.name() + ": parameter '" + param + "' " + field + " is " + manifest
                    + " in the manifest but " + code + " in @AdharBatchJob");
        }
    }

    private static BatchJobManifest job(Path manifest, String jobName) {
        List<BatchJobManifest> jobs = read(manifest);
        return jobs.stream().filter(job -> job.job().equals(jobName)).findFirst().orElseThrow(() ->
                new AssertionError(manifest + " holds no BatchJob of job '" + jobName + "'; it holds "
                        + jobs.stream().map(BatchJobManifest::job).toList()));
    }

    private static void fail(Path manifest, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new AssertionError("The BatchJob manifest " + manifest + " does not match the application:\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * YAML 1.1 reads an unquoted {@code 2026-11-03} as a timestamp; the manifest's defaults are
     * strings to the API server and to the runner, so they are compared as written.
     */
    private static final class NoTimestamps extends Resolver {

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, BOOL, "yYnNtTfFoO");
            addImplicitResolver(Tag.INT, INT, "-+0123456789");
            addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
            addImplicitResolver(Tag.MERGE, MERGE, "<");
            addImplicitResolver(Tag.NULL, NULL, "~nN\0");
            addImplicitResolver(Tag.NULL, EMPTY, null);
        }
    }
}
