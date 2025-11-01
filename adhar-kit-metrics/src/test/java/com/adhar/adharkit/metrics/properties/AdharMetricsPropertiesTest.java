package com.adhar.adharkit.metrics.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharMetricsProperties}.
 */
class AdharMetricsPropertiesTest {

    @Test
    void testDefaultValues() {
        // Create a new instance to test default values
        AdharMetricsProperties defaultProps = new AdharMetricsProperties();

        // Test global properties
        assertThat(defaultProps.isEnabled()).isTrue();
        assertThat(defaultProps.getCommonTags()).isNotNull().isEmpty();

        // Test Prometheus properties
        assertThat(defaultProps.getPrometheus()).isNotNull();
        assertThat(defaultProps.getPrometheus().isEnabled()).isTrue();
        assertThat(defaultProps.getPrometheus().getEndpoint()).isEqualTo("/actuator/prometheus");
        assertThat(defaultProps.getPrometheus().isDescriptions()).isTrue();

        // Test OpenTelemetry properties
        assertThat(defaultProps.getOpenTelemetry()).isNotNull();
        assertThat(defaultProps.getOpenTelemetry().isEnabled()).isFalse();
        assertThat(defaultProps.getOpenTelemetry().getEndpoint()).isEqualTo("http://localhost:4318/v1/metrics");

        // Test JVM metrics properties
        assertThat(defaultProps.getJvm()).isNotNull();
        assertThat(defaultProps.getJvm().isEnabled()).isTrue();

        // Test Kubernetes properties
        assertThat(defaultProps.getKubernetes()).isNotNull();
        assertThat(defaultProps.getKubernetes().isEnabled()).isFalse();
    }

    @Test
    void testSetters() {
        // Test that setters work correctly
        AdharMetricsProperties props = new AdharMetricsProperties();

        // Test global properties
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();

        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }
}

