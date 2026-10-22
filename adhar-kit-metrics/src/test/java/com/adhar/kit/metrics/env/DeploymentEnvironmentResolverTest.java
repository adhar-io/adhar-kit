package com.adhar.kit.metrics.env;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentEnvironmentResolverTest {

    @TempDir
    Path tmp;

    private static Function<String, String> env(Map<String, String> values) {
        return values::get;
    }

    private Path namespaceFile(String namespace) throws IOException {
        return Files.writeString(tmp.resolve("namespace"), namespace + "\n");
    }

    private final Path noFile = Path.of("/nonexistent/namespace");

    @Test
    void theExplicitPropertyWinsOverEverything() throws IOException {
        MockEnvironment environment = new MockEnvironment().withProperty("adhar.metrics.environment", "staging");
        environment.setActiveProfiles("prod");
        assertThat(DeploymentEnvironmentResolver.resolve(environment,
                env(Map.of("ADHAR_ENVIRONMENT", "dev", "POD_NAMESPACE", "test")), namespaceFile("ns")))
                .isEqualTo("staging");
    }

    @Test
    void adharEnvironmentVariableComesNext() {
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("ADHAR_ENVIRONMENT", "prod",
                        "OTEL_RESOURCE_ATTRIBUTES", "deployment.environment.name=dev",
                        "POD_NAMESPACE", "test")), noFile))
                .isEqualTo("prod");
    }

    @Test
    void otelResourceAttributesPreferTheCurrentKeyOverTheDeprecatedOne() {
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES",
                        "service.namespace=adhar,deployment.environment=local,deployment.environment.name=test",
                        "POD_NAMESPACE", "dev")), noFile))
                .isEqualTo("test");
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES", "service.namespace=adhar, deployment.environment=qa")), noFile))
                .isEqualTo("qa");
    }

    @Test
    void thePodNamespaceComesFromTheEnvThenTheServiceAccountFile() throws IOException {
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("POD_NAMESPACE", "dev")), namespaceFile("prod"))).isEqualTo("dev");
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of()), namespaceFile("prod"))).isEqualTo("prod");
    }

    @Test
    void theKubernetesAndDefaultProfilesAreNotEnvironments() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("kubernetes", "default", "perf");
        assertThat(DeploymentEnvironmentResolver.resolve(environment, env(Map.of()), noFile)).isEqualTo("perf");

        MockEnvironment onlyKubernetes = new MockEnvironment();
        onlyKubernetes.setActiveProfiles("kubernetes");
        assertThat(DeploymentEnvironmentResolver.resolve(onlyKubernetes, env(Map.of()), noFile)).isEqualTo("local");
    }

    @Test
    void blankValuesAreSkipped() {
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment().withProperty("adhar.metrics.environment", " "),
                env(Map.of("ADHAR_ENVIRONMENT", "", "OTEL_RESOURCE_ATTRIBUTES", "deployment.environment.name=",
                        "POD_NAMESPACE", "test")), noFile))
                .isEqualTo("test");
    }

    @Test
    void aHardCodedLocalAttributeIsIgnoredInAPod() throws IOException {
        // The base Rollout of older scaffolds: every environment said "local".
        String hardCoded = "service.namespace=adhar,deployment.environment=local";
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES", hardCoded, "POD_NAMESPACE", "orders-prod")), noFile))
                .isEqualTo("orders-prod");
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES", hardCoded)), namespaceFile("orders-test")))
                .isEqualTo("orders-test");
        // ADHAR_ENVIRONMENT, set per overlay, always wins anyway.
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES", hardCoded, "ADHAR_ENVIRONMENT", "dev",
                        "KUBERNETES_SERVICE_HOST", "10.96.0.1")), noFile))
                .isEqualTo("dev");
    }

    @Test
    void aLocalAttributeStillCountsOutsideKubernetes() {
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(),
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment=local")), noFile))
                .isEqualTo("local");
        MockEnvironment withProfile = new MockEnvironment();
        withProfile.setActiveProfiles("dev");
        assertThat(DeploymentEnvironmentResolver.resolve(withProfile,
                env(Map.of("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment=local")), noFile))
                .isEqualTo("local");
    }

    @Test
    void localWhenNothingNamesAnEnvironment() {
        assertThat(DeploymentEnvironmentResolver.resolve(new MockEnvironment(), env(Map.of()), noFile))
                .isEqualTo("local");
    }
}
