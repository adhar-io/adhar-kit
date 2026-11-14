package com.adhar.kit.batch.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Defaults the batch module needs before the application context exists. Every value sits at the
 * LOWEST precedence: anything the application configures wins.
 *
 * <ul>
 *   <li>Spring Batch's launchers and Spring Boot's job runner print every job parameter, and a
 *       stopped or recovered execution's full description, at INFO. Their loggers default to WARN;
 *       the kit's own listeners log runs without parameters at INFO and with masked parameters at
 *       DEBUG instead. Set the logger's level explicitly to get the framework lines back.</li>
 *   <li>With the embedded scheduler enabled, {@code spring.batch.job.enabled} defaults to false:
 *       a service that runs its jobs on a schedule must not also run every job at startup.</li>
 *   <li>With its engine {@code quartz}, the {@code spring.quartz.*} settings the kit owns (design
 *       0025 §5.20.4): {@code job-store-type=jdbc}, {@code jdbc.initialize-schema=never},
 *       {@code overwrite-existing-jobs=true} and {@code wait-for-jobs-to-complete-on-shutdown=false}.
 *       They are defaults only so that an application value is SEEN: the kit's Quartz configuration
 *       then fails the start naming a value that contradicts one.</li>
 * </ul>
 *
 * <p>In Kubernetes mode ({@code adhar.batch.mode=kubernetes}, or {@code auto} with a job named
 * by {@code adhar.batch.job} / {@code ADHAR_BATCH_JOB}, which the platform always sets), the pod
 * runs one job and exits, so these are SET, at the highest precedence, not defaulted (design 0025
 * §5.5): an application.yaml written for local runs must not turn a run pod into a web server, or
 * make Spring Boot start every job besides the one named.</p>
 * <ul>
 *   <li>{@code adhar.batch.mode=kubernetes}, the resolved mode the runner's beans key on;</li>
 *   <li>{@code spring.batch.job.enabled=false}: the kit's runner replaces Boot's job runner;</li>
 *   <li>{@code spring.main.web-application-type=none};</li>
 *   <li>{@code spring.lifecycle.timeout-per-shutdown-phase} = {@code adhar.batch.shutdown.timeout};</li>
 *   <li>{@code spring.main.cloud-platform=none}: no Kubernetes informers in a run-to-completion pod;</li>
 *   <li>{@code adhar.batch.scheduler.enabled=false}: the embedded scheduler is not the platform path.</li>
 * </ul>
 *
 * <p>Nothing is added when {@code adhar.batch.enabled=false}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchRuntimeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** The property source holding the defaults. */
    static final String PROPERTY_SOURCE_NAME = "adharBatchDefaults";

    /** The property source holding what Kubernetes mode sets. */
    static final String KUBERNETES_SOURCE_NAME = "adharBatchKubernetesMode";

    /** The resolved mode: {@code kubernetes} only after this post-processor has decided it. */
    public static final String MODE_PROPERTY = "adhar.batch.mode";

    /** The environment variable naming the job of a Kubernetes run. */
    static final String JOB_VARIABLE = "ADHAR_BATCH_JOB";

    /** Loggers that print job parameters at INFO. */
    static final List<String> PARAMETER_LOGGERS = List.of(
            "org.springframework.batch.core.launch.support.TaskExecutorJobLauncher",
            "org.springframework.batch.core.launch.support.TaskExecutorJobOperator",
            "org.springframework.batch.core.launch.support.SimpleJobOperator",
            "org.springframework.batch.core.launch.support.CommandLineJobOperator",
            "org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner");

    /** The {@code spring.quartz.*} values the engine quartz needs (design 0025 §5.20.4). */
    public static final Map<String, String> QUARTZ_DEFAULTS = Map.of(
            "spring.quartz.job-store-type", "jdbc",
            "spring.quartz.jdbc.initialize-schema", "never",
            "spring.quartz.overwrite-existing-jobs", "true",
            "spring.quartz.wait-for-jobs-to-complete-on-shutdown", "false");

    @Override
    public int getOrder() {
        // After application.properties/yaml and the environment have been loaded.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("adhar.batch.enabled", Boolean.class, true)) {
            return;
        }
        if (isKubernetesMode(environment)) {
            Map<String, Object> kubernetes = new LinkedHashMap<>();
            kubernetes.put(MODE_PROPERTY, "kubernetes");
            kubernetes.put("spring.batch.job.enabled", "false");
            kubernetes.put("spring.main.web-application-type", "none");
            kubernetes.put("spring.lifecycle.timeout-per-shutdown-phase",
                    environment.getProperty("adhar.batch.shutdown.timeout", "90s"));
            kubernetes.put("spring.main.cloud-platform", "none");
            kubernetes.put("adhar.batch.scheduler.enabled", "false");
            environment.getPropertySources().addFirst(new MapPropertySource(KUBERNETES_SOURCE_NAME, kubernetes));
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        PARAMETER_LOGGERS.forEach(logger -> defaults.put("logging.level." + logger, "WARN"));
        if (environment.getProperty("adhar.batch.scheduler.enabled", Boolean.class, false)) {
            defaults.put("spring.batch.job.enabled", "false");
            if ("quartz".equalsIgnoreCase(environment.getProperty("adhar.batch.scheduler.engine", "simple").strip())) {
                defaults.putAll(QUARTZ_DEFAULTS);
            }
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /**
     * @param environment the application's environment
     * @return whether {@code adhar.batch.mode} is {@code kubernetes}, or {@code auto} (the
     *         default) with a job named
     */
    static boolean isKubernetesMode(ConfigurableEnvironment environment) {
        String mode = environment.getProperty(MODE_PROPERTY, "auto").strip().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "kubernetes" -> true;
            case "embedded" -> false;
            case "auto" -> StringUtils.hasText(environment.getProperty("adhar.batch.job"))
                    || StringUtils.hasText(environment.getProperty(JOB_VARIABLE));
            default -> throw new IllegalStateException("adhar.batch.mode must be auto, kubernetes or embedded, was '"
                    + mode + "'");
        };
    }
}
