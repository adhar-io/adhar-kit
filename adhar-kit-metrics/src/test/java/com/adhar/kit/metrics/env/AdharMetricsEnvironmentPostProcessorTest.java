package com.adhar.kit.metrics.env;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdharMetricsEnvironmentPostProcessorTest {

    private static MockEnvironment process(MockEnvironment environment, Map<String, String> env) {
        new AdharMetricsEnvironmentPostProcessor(env::get).postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    void contributesTheDashboardDefaults() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty("spring.application.name", "orders"), Map.of("POD_NAMESPACE", "dev"));

        assertThat(environment.getProperty("management.metrics.tags.application")).isEqualTo("orders");
        assertThat(environment.getProperty("management.metrics.tags.environment")).isEqualTo("dev");
        assertThat(environment.getProperty("adhar.environment")).isEqualTo("dev");
        assertThat(environment.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests")).isEqualTo("true");
        assertThat(environment.getProperty("management.metrics.distribution.slo.http.server.requests"))
                .isEqualTo("50ms,100ms,250ms,500ms,1s,2s,5s");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
    }

    @Test
    void neverTagsNamespaceOrPod() {
        MockEnvironment environment = process(new MockEnvironment(), Map.of("POD_NAMESPACE", "dev", "POD_NAME", "p-1"));
        assertThat(environment.getProperty("management.metrics.tags.namespace")).isNull();
        assertThat(environment.getProperty("management.metrics.tags.pod")).isNull();
    }

    @Test
    void theApplicationNameFallsBackToOtelServiceName() {
        MockEnvironment environment = process(new MockEnvironment(), Map.of("OTEL_SERVICE_NAME", "payments"));
        assertThat(environment.getProperty("management.metrics.tags.application")).isEqualTo("payments");
    }

    @Test
    void applicationSettingsWin() {
        MockEnvironment environment = process(new MockEnvironment()
                        .withProperty("management.endpoints.web.exposure.include", "*")
                        .withProperty("management.metrics.distribution.percentiles-histogram.http.server.requests", "false"),
                Map.of());
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("*");
        assertThat(environment.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests")).isEqualTo("false");
    }

    @Test
    void doesNothingWhenMetricsAreDisabled() {
        MockEnvironment environment = process(new MockEnvironment().withProperty("adhar.metrics.enabled", "false"),
                Map.of("POD_NAMESPACE", "dev"));
        assertThat(environment.getPropertySources().contains(AdharMetricsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }
}
