package com.adhar.kit.batch.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** Loggers that print job parameters at INFO. */
    static final List<String> PARAMETER_LOGGERS = List.of(
            "org.springframework.batch.core.launch.support.TaskExecutorJobLauncher",
            "org.springframework.batch.core.launch.support.TaskExecutorJobOperator",
            "org.springframework.batch.core.launch.support.SimpleJobOperator",
            "org.springframework.batch.core.launch.support.CommandLineJobOperator",
            "org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner");

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
        Map<String, Object> defaults = new LinkedHashMap<>();
        PARAMETER_LOGGERS.forEach(logger -> defaults.put("logging.level." + logger, "WARN"));
        if (environment.getProperty("adhar.batch.scheduler.enabled", Boolean.class, false)) {
            defaults.put("spring.batch.job.enabled", "false");
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
