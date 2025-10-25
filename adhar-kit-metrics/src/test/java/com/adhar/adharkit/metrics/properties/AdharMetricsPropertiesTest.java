package com.adhar.adharkit.metrics.properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharMetricsProperties}.
 */
@SpringBootTest(classes = {AdharMetricsProperties.class})
@TestPropertySource(properties = {
    "adhar.metrics.enabled=true",
    "adhar.metrics.prometheus.endpoint=/custom/prometheus",
    "adhar.metrics.open-telemetry.endpoint=http://custom-endpoint:4318/v1/metrics"
})
class AdharMetricsPropertiesTest {

    @Autowired
    private AdharMetricsProperties properties;

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

        // Test Web metrics properties
        assertThat(defaultProps.getWeb()).isNotNull();
        assertThat(defaultProps.getWeb().isEnabled()).isTrue();
        assertThat(defaultProps.getWeb().isRecordRequestSize()).isTrue();
        assertThat(defaultProps.getWeb().isRecordResponseSize()).isTrue();
        assertThat(defaultProps.getWeb().getMaxUriTags()).isEqualTo(100);

        // Test JVM metrics properties
        assertThat(defaultProps.getJvm()).isNotNull();
        assertThat(defaultProps.getJvm().isEnabled()).isTrue();
        assertThat(defaultProps.getJvm().isMemory()).isTrue();
        assertThat(defaultProps.getJvm().isGc()).isTrue();
        assertThat(defaultProps.getJvm().isThreads()).isTrue();
        assertThat(defaultProps.getJvm().isClassLoader()).isTrue();

        // Test System metrics properties
        assertThat(defaultProps.getSystem()).isNotNull();
        assertThat(defaultProps.getSystem().isEnabled()).isTrue();
        assertThat(defaultProps.getSystem().isProcessor()).isTrue();
        assertThat(defaultProps.getSystem().isFileDescriptor()).isTrue();
        assertThat(defaultProps.getSystem().isUptime()).isTrue();
        assertThat(defaultProps.getSystem().isDiskSpace()).isTrue();

        // Test Application metrics properties
        assertThat(defaultProps.getApplication()).isNotNull();
        assertThat(defaultProps.getApplication().isEnabled()).isTrue();
        assertThat(defaultProps.getApplication().isMethodTiming()).isTrue();
        assertThat(defaultProps.getApplication().isExceptionCounting()).isTrue();
        assertThat(defaultProps.getApplication().isCache()).isTrue();
        assertThat(defaultProps.getApplication().isDatabase()).isTrue();

        // Test Kubernetes properties
        assertThat(defaultProps.getKubernetes()).isNotNull();
        assertThat(defaultProps.getKubernetes().isEnabled()).isFalse();
        assertThat(defaultProps.getKubernetes().isIncludePodInfo()).isTrue();
        assertThat(defaultProps.getKubernetes().isIncludeNamespace()).isTrue();
        assertThat(defaultProps.getKubernetes().isIncludeNodeInfo()).isTrue();
    }

    @Test
    void testCustomValues() {
        // Test that properties are correctly loaded from application.properties
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPrometheus().getEndpoint()).isEqualTo("/custom/prometheus");
        assertThat(properties.getOpenTelemetry().getEndpoint()).isEqualTo("http://custom-endpoint:4318/v1/metrics");
    }

    @Test
    void testSetters() {
        // Test that setters work correctly
        AdharMetricsProperties props = new AdharMetricsProperties();
        
        // Test global properties
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
        
        Map<String, String> commonTags = new HashMap<>();
        commonTags.put("key1", "value1");
        commonTags.put("key2", "value2");
        props.setCommonTags(commonTags);
        assertThat(props.getCommonTags()).isEqualTo(commonTags);
        
        // Test Prometheus properties
        props.getPrometheus().setEnabled(false);
        assertThat(props.getPrometheus().isEnabled()).isFalse();
        
        props.getPrometheus().setEndpoint("/custom/path");
        assertThat(props.getPrometheus().getEndpoint()).isEqualTo("/custom/path");

        props.getPrometheus().setDescriptions(false);
        assertThat(props.getPrometheus().isDescriptions()).isFalse();

        // Test OpenTelemetry properties
        props.getOpenTelemetry().setEnabled(true);
        assertThat(props.getOpenTelemetry().isEnabled()).isTrue();

        props.getOpenTelemetry().setEndpoint("http://custom:4318/v1/metrics");
        assertThat(props.getOpenTelemetry().getEndpoint()).isEqualTo("http://custom:4318/v1/metrics");

        // Test Web properties
        props.getWeb().setEnabled(false);
        assertThat(props.getWeb().isEnabled()).isFalse();

        props.getWeb().setMaxUriTags(200);
        assertThat(props.getWeb().getMaxUriTags()).isEqualTo(200);

        // Test JVM properties
        props.getJvm().setEnabled(false);
        assertThat(props.getJvm().isEnabled()).isFalse();

        // Test Kubernetes properties
        props.getKubernetes().setEnabled(true);
        assertThat(props.getKubernetes().isEnabled()).isTrue();

        props.getKubernetes().setIncludePodInfo(false);
        assertThat(props.getKubernetes().isIncludePodInfo()).isFalse();
    }

    @Test
    void testNestedPropertyConfiguration() {
        AdharMetricsProperties props = new AdharMetricsProperties();

        // Test Prometheus config map
        Map<String, String> prometheusConfig = new HashMap<>();
        prometheusConfig.put("step", "PT30S");
        prometheusConfig.put("timeout", "PT10S");
        props.getPrometheus().setConfig(prometheusConfig);
        assertThat(props.getPrometheus().getConfig()).isEqualTo(prometheusConfig);

        // Test OpenTelemetry resource attributes
        Map<String, String> resourceAttributes = new HashMap<>();
        resourceAttributes.put("service.name", "test-service");
        resourceAttributes.put("service.version", "1.0.0");
        props.getOpenTelemetry().setResourceAttributes(resourceAttributes);
        assertThat(props.getOpenTelemetry().getResourceAttributes()).isEqualTo(resourceAttributes);

        // Test web ignore patterns
        String[] ignorePatterns = {"/health", "/metrics", "/actuator/**"};
        props.getWeb().setIgnorePatterns(ignorePatterns);
        assertThat(props.getWeb().getIgnorePatterns()).isEqualTo(ignorePatterns);

        // Test Kubernetes custom labels
        String[] customLabels = {"app", "version", "environment"};
        props.getKubernetes().setCustomLabels(customLabels);
        assertThat(props.getKubernetes().getCustomLabels()).isEqualTo(customLabels);
    }
}