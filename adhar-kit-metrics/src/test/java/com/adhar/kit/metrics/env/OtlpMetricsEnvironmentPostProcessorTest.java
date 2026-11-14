package com.adhar.kit.metrics.env;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OTEL_* → {@code management.otlp.metrics.export.*} mapping of
 * {@link OtlpMetricsEnvironmentPostProcessor}; the push itself is {@link OtlpMetricsPushTest}.
 */
class OtlpMetricsEnvironmentPostProcessorTest {

    private static final String EXPORT = "management.otlp.metrics.export.";

    private static MockEnvironment process(MockEnvironment environment, Map<String, String> env) {
        new OtlpMetricsEnvironmentPostProcessor(env::get).postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    private static MockEnvironment process(Map<String, String> env) {
        return process(new MockEnvironment(), env);
    }

    @Test
    void theBaseEndpointGetsTheMetricsPathAndEnablesThePush() {
        MockEnvironment environment = process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy.adhar-system:4318/"));

        assertThat(environment.getProperty(EXPORT + "url")).isEqualTo("http://alloy.adhar-system:4318/v1/metrics");
        assertThat(environment.getProperty(EXPORT + "enabled")).isEqualTo("true");
    }

    @Test
    void theSignalEndpointIsTakenAsIs() {
        MockEnvironment environment = process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://ignored:4318",
                "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT", "http://collector:4318/custom/metrics"));

        assertThat(environment.getProperty(EXPORT + "url")).isEqualTo("http://collector:4318/custom/metrics");
    }

    @Test
    void noEndpointMeansNoPush() {
        MockEnvironment environment = process(Map.of());

        assertThat(environment.getProperty(EXPORT + "enabled")).isEqualTo("false");
        assertThat(environment.getProperty(EXPORT + "url")).isNull();
    }

    @Test
    void anExporterListWithoutOtlpOrAGrpcProtocolMeansNoPush() {
        assertThat(process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318", "OTEL_METRICS_EXPORTER", "none"))
                .getProperty(EXPORT + "enabled")).isEqualTo("false");
        assertThat(process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318",
                "OTEL_METRICS_EXPORTER", "prometheus, otlp")).getProperty(EXPORT + "enabled")).isEqualTo("true");
        MockEnvironment grpc = process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4317",
                "OTEL_EXPORTER_OTLP_PROTOCOL", "grpc"));
        assertThat(grpc.getProperty(EXPORT + "enabled")).isEqualTo("false");
        assertThat(grpc.getProperty(EXPORT + "url")).isNull();
        assertThat(process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318", "OTEL_EXPORTER_OTLP_PROTOCOL",
                "grpc", "OTEL_EXPORTER_OTLP_METRICS_PROTOCOL", "http/protobuf")).getProperty(EXPORT + "enabled"))
                .isEqualTo("true");
    }

    @Test
    void headersAndIntervalAreMapped() {
        MockEnvironment environment = process(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318",
                "OTEL_EXPORTER_OTLP_HEADERS", "X-Scope-OrgID=team%2Da, Authorization=Bearer abc",
                "OTEL_METRIC_EXPORT_INTERVAL", "15000"));

        assertThat(environment.getProperty(EXPORT + "headers[X-Scope-OrgID]")).isEqualTo("team-a");
        assertThat(environment.getProperty(EXPORT + "headers[Authorization]")).isEqualTo("Bearer abc");
        assertThat(environment.getProperty(EXPORT + "step")).isEqualTo("15000ms");
    }

    @Test
    void theApplicationsOwnSettingsWin() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty(EXPORT + "url", "http://own:4318/v1/metrics"), new HashMap<>());

        assertThat(environment.getProperty(EXPORT + "url")).isEqualTo("http://own:4318/v1/metrics");
        assertThat(environment.getProperty(EXPORT + "enabled")).isNull();

        MockEnvironment off = process(new MockEnvironment().withProperty("adhar.metrics.enabled", "false"),
                Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318"));
        assertThat(off.getPropertySources().contains(OtlpMetricsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }
}
