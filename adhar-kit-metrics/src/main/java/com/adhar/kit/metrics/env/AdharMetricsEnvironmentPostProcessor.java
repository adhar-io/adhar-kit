package com.adhar.kit.metrics.env;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Contributes the kit's observability defaults at the LOWEST precedence, so anything an
 * application sets (properties, env vars, command line) still wins.
 * <ul>
 *   <li>{@code management.metrics.tags.application} / {@code .environment}: applied by Spring
 *       Boot to every meter from the moment the registry exists. (Tagging the registry later,
 *       after meters were registered, left the JVM and persistence meters without them.)</li>
 *   <li>{@code adhar.environment}: the resolved environment (see
 *       {@link DeploymentEnvironmentResolver}), read by the logging and tracing modules.</li>
 *   <li>A latency histogram with SLO buckets for {@code http.server.requests}, and the
 *       {@code health,info,prometheus} actuator endpoints exposed over HTTP.</li>
 * </ul>
 * Switched off entirely with {@code adhar.metrics.enabled=false}.
 */
public class AdharMetricsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Name of the contributed property source. */
    public static final String PROPERTY_SOURCE_NAME = "adharMetricsDefaults";

    /** Property carrying the resolved deployment environment for the other kit modules. */
    public static final String RESOLVED_ENVIRONMENT_PROPERTY = "adhar.environment";

    static final String HTTP_SERVER_SLO = "50ms,100ms,250ms,500ms,1s,2s,5s";

    private final Function<String, String> env;

    public AdharMetricsEnvironmentPostProcessor() {
        this(System::getenv);
    }

    AdharMetricsEnvironmentPostProcessor(Function<String, String> env) {
        this.env = env;
    }

    @Override
    public int getOrder() {
        // After application.properties/yaml and the active profiles are known.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("adhar.metrics.enabled", Boolean.class, true)) {
            return;
        }
        String deploymentEnvironment = DeploymentEnvironmentResolver.resolve(
                environment, env, DeploymentEnvironmentResolver.SERVICE_ACCOUNT_NAMESPACE);

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(RESOLVED_ENVIRONMENT_PROPERTY, deploymentEnvironment);
        defaults.put("management.metrics.tags.application", applicationName(environment));
        defaults.put("management.metrics.tags.environment", deploymentEnvironment);
        defaults.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");
        defaults.put("management.metrics.distribution.slo.http.server.requests", HTTP_SERVER_SLO);
        defaults.put("management.endpoints.web.exposure.include", "health,info,prometheus");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /** {@code spring.application.name}, else {@code OTEL_SERVICE_NAME}. */
    String applicationName(ConfigurableEnvironment environment) {
        String name = environment.getProperty("spring.application.name");
        if (!StringUtils.hasText(name)) {
            name = env.apply("OTEL_SERVICE_NAME");
        }
        return StringUtils.hasText(name) ? name.trim() : "unknown-application";
    }
}
