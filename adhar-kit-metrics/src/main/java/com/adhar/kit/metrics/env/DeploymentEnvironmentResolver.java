package com.adhar.kit.metrics.env;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Resolves the name of the environment an application is deployed to ({@code dev},
 * {@code test}, {@code prod}, ...) — the value of the {@code environment} metric tag, the
 * {@code env} log field and the {@code deployment.environment.name} trace resource attribute.
 * <p>
 * The active profiles are NOT the environment: Spring Cloud Kubernetes adds a
 * {@code kubernetes} profile, which made every pod report {@code environment="kubernetes"}.
 * The first source that has a value wins:
 * <ol>
 *   <li>{@code adhar.metrics.environment} (explicit property)</li>
 *   <li>the {@code ADHAR_ENVIRONMENT} environment variable</li>
 *   <li>{@code deployment.environment.name} or {@code deployment.environment} in
 *       {@code OTEL_RESOURCE_ATTRIBUTES} - except {@code local} inside a pod: the base Rollout of
 *       services scaffolded before environments were set per overlay hard-coded
 *       {@code deployment.environment=local}, which made dev, test and prod all report
 *       {@code local}. In a pod that value is skipped and the namespace answers instead.</li>
 *   <li>the pod namespace: {@code POD_NAMESPACE}, else the service-account namespace file</li>
 *   <li>the first active profile that is not {@code kubernetes} or {@code default}</li>
 *   <li>{@code local}</li>
 * </ol>
 */
public final class DeploymentEnvironmentResolver {

    /** Explicit override property. */
    public static final String ENVIRONMENT_PROPERTY = "adhar.metrics.environment";

    /** Environment variable consulted after the explicit property. */
    public static final String ENVIRONMENT_VARIABLE = "ADHAR_ENVIRONMENT";

    /** The value used when nothing else names an environment. */
    public static final String DEFAULT_ENVIRONMENT = "local";

    static final Path SERVICE_ACCOUNT_NAMESPACE =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private static final Set<String> NON_ENVIRONMENT_PROFILES = Set.of("kubernetes", "default");

    private DeploymentEnvironmentResolver() {
    }

    /**
     * Resolves the environment from the Spring {@link Environment}, the process environment
     * and the service-account namespace file.
     */
    public static String resolve(Environment environment) {
        return resolve(environment, System::getenv, SERVICE_ACCOUNT_NAMESPACE);
    }

    static String resolve(Environment environment, Function<String, String> env, Path namespaceFile) {
        String namespace = firstText(env.apply("POD_NAMESPACE"), readNamespace(namespaceFile)).orElse(null);
        boolean inKubernetes = namespace != null || StringUtils.hasText(env.apply("KUBERNETES_SERVICE_HOST"));
        String fromAttributes = fromResourceAttributes(env.apply("OTEL_RESOURCE_ATTRIBUTES"));
        if (inKubernetes && fromAttributes != null && DEFAULT_ENVIRONMENT.equalsIgnoreCase(fromAttributes.trim())) {
            fromAttributes = null;
        }
        return firstText(
                environment.getProperty(ENVIRONMENT_PROPERTY),
                env.apply(ENVIRONMENT_VARIABLE),
                fromAttributes,
                namespace,
                firstEnvironmentProfile(environment))
                .orElse(DEFAULT_ENVIRONMENT);
    }

    /**
     * Reads {@code deployment.environment.name} (current semantic conventions), else the
     * deprecated {@code deployment.environment}, from an {@code OTEL_RESOURCE_ATTRIBUTES} value.
     */
    static String fromResourceAttributes(String attributes) {
        if (!StringUtils.hasText(attributes)) {
            return null;
        }
        String name = null;
        String legacy = null;
        for (String pair : attributes.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if ("deployment.environment.name".equals(key)) {
                name = value;
            } else if ("deployment.environment".equals(key)) {
                legacy = value;
            }
        }
        return StringUtils.hasText(name) ? name : legacy;
    }

    private static String readNamespace(Path namespaceFile) {
        try {
            if (namespaceFile != null && Files.isReadable(namespaceFile)) {
                return Files.readString(namespaceFile, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException | RuntimeException e) {
            // Not in a pod, or not readable: fall through to the next source.
        }
        return null;
    }

    private static String firstEnvironmentProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(profile -> !NON_ENVIRONMENT_PROFILES.contains(profile.toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private static Optional<String> firstText(String... candidates) {
        return Arrays.stream(candidates).filter(StringUtils::hasText).map(String::trim).findFirst();
    }
}
