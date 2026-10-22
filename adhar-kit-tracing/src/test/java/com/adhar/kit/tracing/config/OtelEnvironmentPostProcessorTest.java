package com.adhar.kit.tracing.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtelEnvironmentPostProcessorTest {

    /** Never present: keeps the tests hermetic when they run inside a pod (CI). */
    private static final Path NO_NAMESPACE_FILE = Path.of("/nonexistent/namespace");

    private static Map<String, Object> map(Map<String, String> env) {
        return new OtelEnvironmentPostProcessor(env::get, NO_NAMESPACE_FILE).map();
    }

    @Test
    void theTemplatesCollectorSettingsBecomeAnHttpExporterToTheTracesUrl() {
        Map<String, Object> properties = map(Map.of(
                "OTEL_SERVICE_NAME", "transaction-service",
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy.adhar-system.svc.cluster.local:4318",
                "OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf",
                "OTEL_TRACES_EXPORTER", "otlp"));

        assertThat(properties)
                .containsEntry("adhar.tracing.open-telemetry.endpoint",
                        "http://alloy.adhar-system.svc.cluster.local:4318/v1/traces")
                .containsEntry("adhar.tracing.open-telemetry.use-grpc", "false")
                .containsEntry("management.opentelemetry.tracing.export.otlp.endpoint",
                        "http://alloy.adhar-system.svc.cluster.local:4318/v1/traces")
                .containsEntry("management.opentelemetry.tracing.export.otlp.transport", "http")
                .containsEntry("adhar.tracing.resource.service-name", "transaction-service")
                .doesNotContainKey("adhar.tracing.open-telemetry.enabled");
    }

    @Test
    void httpProtobufIsTheDefaultProtocolAndATrailingSlashIsDropped() {
        assertThat(map(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://tempo:4318/")))
                .containsEntry("adhar.tracing.open-telemetry.endpoint", "http://tempo:4318/v1/traces")
                .containsEntry("adhar.tracing.open-telemetry.use-grpc", "false");
    }

    @Test
    void grpcUsesTheCollectorAddressAsIs() {
        assertThat(map(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4317",
                "OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")))
                .containsEntry("adhar.tracing.open-telemetry.endpoint", "http://alloy:4317")
                .containsEntry("adhar.tracing.open-telemetry.use-grpc", "true")
                .containsEntry("management.opentelemetry.tracing.export.otlp.transport", "grpc");
    }

    @Test
    void theTracesSpecificEndpointAndProtocolWin() {
        assertThat(map(Map.of(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4317",
                "OTEL_EXPORTER_OTLP_PROTOCOL", "grpc",
                "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://tempo:4318/v1/traces",
                "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", "http/protobuf")))
                .containsEntry("adhar.tracing.open-telemetry.endpoint", "http://tempo:4318/v1/traces")
                .containsEntry("adhar.tracing.open-telemetry.use-grpc", "false");
    }

    @Test
    void noEndpointMeansTheKitDefaultsStand() {
        assertThat(map(Map.of())).doesNotContainKeys("adhar.tracing.open-telemetry.endpoint",
                "adhar.tracing.open-telemetry.use-grpc", "adhar.tracing.sampling.probability");
    }

    @Test
    void tracesExporterNoneSwitchesExportOff() {
        assertThat(map(Map.of("OTEL_TRACES_EXPORTER", "none")))
                .containsEntry("adhar.tracing.open-telemetry.enabled", "false")
                .containsEntry("management.tracing.export.otlp.enabled", "false");
    }

    @Test
    void resourceAttributesAreMappedAndTheEnvironmentIsFilledIn() {
        Map<String, Object> properties = map(Map.of(
                "OTEL_SERVICE_NAME", "orders",
                "OTEL_RESOURCE_ATTRIBUTES", "service.name=ignored,service.namespace=adhar,team=pay%20ments"));
        assertThat(properties)
                .containsEntry("adhar.tracing.resource.attributes[service.namespace]", "adhar")
                .containsEntry("adhar.tracing.resource.attributes[team]", "pay ments")
                .containsEntry("management.opentelemetry.resource-attributes[service.namespace]", "adhar")
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment.name]", "${adhar.environment:}")
                .doesNotContainKey("adhar.tracing.resource.attributes[service.name]");

        assertThat(map(Map.of("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment.name=prod")))
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment.name]", "prod");
    }

    @Test
    void aHardCodedLocalEnvironmentIsReplacedInAPod(@TempDir Path tmp) throws IOException {
        String hardCoded = "service.namespace=adhar,deployment.environment=local";
        Map<String, Object> inPod = map(Map.of("OTEL_RESOURCE_ATTRIBUTES", hardCoded,
                "KUBERNETES_SERVICE_HOST", "10.96.0.1"));
        assertThat(inPod)
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment]", "${adhar.environment:}")
                .containsEntry("management.opentelemetry.resource-attributes[deployment.environment]",
                        "${adhar.environment:}")
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment.name]", "${adhar.environment:}");

        assertThat(map(Map.of("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment.name=local",
                "POD_NAMESPACE", "orders-dev")))
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment.name]", "${adhar.environment:}");

        // The pod is detected from the service-account file too.
        Path namespaceFile = Files.writeString(tmp.resolve("namespace"), "orders-prod");
        assertThat(new OtelEnvironmentPostProcessor(Map.of("OTEL_RESOURCE_ATTRIBUTES", hardCoded)::get,
                namespaceFile).map())
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment]", "${adhar.environment:}");
    }

    @Test
    void aLocalEnvironmentIsKeptOutsideKubernetesAndOtherValuesAlways() {
        assertThat(map(Map.of("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment=local")))
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment]", "local");
        assertThat(map(Map.of("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment=prod",
                "KUBERNETES_SERVICE_HOST", "10.96.0.1")))
                .containsEntry("adhar.tracing.resource.attributes[deployment.environment]", "prod");
    }

    @Test
    void clientObservationsBootLeavesOffAreSwitchedOn() {
        assertThat(map(Map.of()))
                .containsEntry("spring.kafka.template.observation-enabled", "true")
                .containsEntry("spring.kafka.listener.observation-enabled", "true")
                .containsEntry("spring.rabbitmq.template.observation-enabled", "true")
                .containsEntry("spring.rabbitmq.listener.simple.observation-enabled", "true")
                .containsEntry("jdbc.includes", "connection,query")
                .containsEntry("jdbc.datasource-proxy.include-parameter-values", "false");

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.kafka.listener.observation-enabled", "false");
        new OtelEnvironmentPostProcessor(Map.<String, String>of()::get, NO_NAMESPACE_FILE)
                .postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("spring.kafka.listener.observation-enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.kafka.template.observation-enabled")).isEqualTo("true");
    }

    @Test
    void anExplicitEndpointDecidesTheProtocol() {
        Map<String, String> rollout = Map.of(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy.adhar-system.svc.cluster.local:4318",
                "OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf");

        // The gateway scaffold: its own endpoint on the gRPC port, the Rollout's HTTP protocol.
        MockEnvironment gateway = new MockEnvironment().withProperty("adhar.tracing.open-telemetry.endpoint",
                "${ADHAR_TRACING_ENDPOINT:http://alloy.adhar-system.svc.cluster.local:4317}");
        new OtelEnvironmentPostProcessor(rollout::get, NO_NAMESPACE_FILE)
                .postProcessEnvironment(gateway, new SpringApplication());
        assertThat(gateway.getProperty("adhar.tracing.open-telemetry.endpoint"))
                .isEqualTo("http://alloy.adhar-system.svc.cluster.local:4317");
        assertThat(gateway.getProperty("adhar.tracing.open-telemetry.use-grpc")).isEqualTo("true");

        MockEnvironment http = new MockEnvironment()
                .withProperty("adhar.tracing.open-telemetry.endpoint", "http://collector:4318/v1/traces");
        new OtelEnvironmentPostProcessor(Map.of("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc",
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4317")::get, NO_NAMESPACE_FILE)
                .postProcessEnvironment(http, new SpringApplication());
        assertThat(http.getProperty("adhar.tracing.open-telemetry.use-grpc")).isEqualTo("false");

        // An explicit use-grpc is never second-guessed.
        MockEnvironment pinned = new MockEnvironment()
                .withProperty("adhar.tracing.open-telemetry.endpoint", "http://alloy:4317")
                .withProperty("adhar.tracing.open-telemetry.use-grpc", "false");
        new OtelEnvironmentPostProcessor(rollout::get, NO_NAMESPACE_FILE)
                .postProcessEnvironment(pinned, new SpringApplication());
        assertThat(pinned.getProperty("adhar.tracing.open-telemetry.use-grpc")).isEqualTo("false");

        // An endpoint that implies nothing keeps the kit's default (no mapped value).
        MockEnvironment custom = new MockEnvironment()
                .withProperty("adhar.tracing.open-telemetry.endpoint", "https://otel.example.com");
        new OtelEnvironmentPostProcessor(rollout::get, NO_NAMESPACE_FILE)
                .postProcessEnvironment(custom, new SpringApplication());
        assertThat(custom.getProperty("adhar.tracing.open-telemetry.use-grpc")).isNull();
    }

    @Test
    void samplingDefaultsToEveryTraceAndFollowsTheSamplerArg() {
        assertThat(map(Map.of())).containsEntry("management.tracing.sampling.probability", "1.0");
        assertThat(map(Map.of("OTEL_TRACES_SAMPLER_ARG", "0.25")))
                .containsEntry("adhar.tracing.sampling.probability", "0.25")
                .containsEntry("management.tracing.sampling.probability", "0.25");
        // Not a ratio (e.g. a jaeger_remote sampler's arguments): ignored rather than failing startup.
        assertThat(map(Map.of("OTEL_TRACES_SAMPLER_ARG", "endpoint=http://jaeger:14250")))
                .doesNotContainKey("adhar.tracing.sampling.probability")
                .containsEntry("management.tracing.sampling.probability", "1.0");
    }

    @Test
    void applicationPropertiesWinOverTheMappedValues() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("adhar.tracing.open-telemetry.endpoint", "http://explicit:4318/v1/traces");
        new OtelEnvironmentPostProcessor(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318")::get,
                NO_NAMESPACE_FILE)
                .postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("adhar.tracing.open-telemetry.endpoint"))
                .isEqualTo("http://explicit:4318/v1/traces");
        assertThat(environment.getProperty("adhar.tracing.open-telemetry.use-grpc")).isEqualTo("false");
    }
}
