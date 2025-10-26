package com.adhar.adharkit.tracing.properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharTracingProperties}.
 */
@SpringBootTest(classes = {AdharTracingProperties.class})
@TestPropertySource(properties = {
    "adhar.tracing.enabled=true",
    "adhar.tracing.open-telemetry.endpoint=http://custom-otel:4317",
    "adhar.tracing.zipkin.base-url=http://custom-zipkin:9411",
    "adhar.tracing.jaeger.grpc-endpoint=http://custom-jaeger:14250",
    "adhar.tracing.sampling.probability=0.5",
    "adhar.tracing.resource.service-name=test-service"
})
class AdharTracingPropertiesTest {

    @Autowired
    private AdharTracingProperties properties;

    @Test
    void testDefaultValues() {
        // Create a new instance to test default values
        AdharTracingProperties defaultProps = new AdharTracingProperties();

        // Test global properties
        assertThat(defaultProps.isEnabled()).isTrue();
        assertThat(defaultProps.getTags()).isNotNull().isEmpty();

        // Test OpenTelemetry properties
        assertThat(defaultProps.getOpenTelemetry()).isNotNull();
        assertThat(defaultProps.getOpenTelemetry().isEnabled()).isTrue();
        assertThat(defaultProps.getOpenTelemetry().getEndpoint()).isEqualTo("http://localhost:4317");
        assertThat(defaultProps.getOpenTelemetry().getTimeout()).isEqualTo("PT30S");
        assertThat(defaultProps.getOpenTelemetry().getInterval()).isEqualTo("PT30S");
        assertThat(defaultProps.getOpenTelemetry().isUseGrpc()).isTrue();
        assertThat(defaultProps.getOpenTelemetry().getCompression()).isEqualTo("gzip");

        // Test Zipkin properties
        assertThat(defaultProps.getZipkin()).isNotNull();
        assertThat(defaultProps.getZipkin().isEnabled()).isFalse();
        assertThat(defaultProps.getZipkin().getBaseUrl()).isEqualTo("http://localhost:9411");
        assertThat(defaultProps.getZipkin().getApiPath()).isEqualTo("/api/v2/spans");
        assertThat(defaultProps.getZipkin().getSenderType()).isEqualTo("http");

        // Test Jaeger properties
        assertThat(defaultProps.getJaeger()).isNotNull();
        assertThat(defaultProps.getJaeger().isEnabled()).isFalse();
        assertThat(defaultProps.getJaeger().getGrpcEndpoint()).isEqualTo("http://localhost:14250");
        assertThat(defaultProps.getJaeger().getHttpEndpoint()).isEqualTo("http://localhost:14268/api/traces");
        assertThat(defaultProps.getJaeger().isUseGrpc()).isTrue();

        // Test Web tracing properties
        assertThat(defaultProps.getWeb()).isNotNull();
        assertThat(defaultProps.getWeb().isEnabled()).isTrue();
        assertThat(defaultProps.getWeb().isTraceHttpClients()).isTrue();
        assertThat(defaultProps.getWeb().isTraceHttpServers()).isTrue();
        assertThat(defaultProps.getWeb().getSkipPatterns()).hasSize(3);
        assertThat(defaultProps.getWeb().isIncludeRequestHeaders()).isFalse();
        assertThat(defaultProps.getWeb().isIncludeResponseHeaders()).isFalse();

        // Test Database tracing properties
        assertThat(defaultProps.getDatabase()).isNotNull();
        assertThat(defaultProps.getDatabase().isEnabled()).isTrue();
        assertThat(defaultProps.getDatabase().isIncludeSqlStatements()).isTrue();
        assertThat(defaultProps.getDatabase().isIncludeSqlParameters()).isFalse();
        assertThat(defaultProps.getDatabase().getMaxSqlLength()).isEqualTo(1000);

        // Test Messaging tracing properties
        assertThat(defaultProps.getMessaging()).isNotNull();
        assertThat(defaultProps.getMessaging().isEnabled()).isTrue();
        assertThat(defaultProps.getMessaging().isTraceKafka()).isTrue();
        assertThat(defaultProps.getMessaging().isTraceRabbitmq()).isTrue();
        assertThat(defaultProps.getMessaging().isTraceRedis()).isTrue();
        assertThat(defaultProps.getMessaging().isIncludePayloads()).isFalse();

        // Test Sampling properties
        assertThat(defaultProps.getSampling()).isNotNull();
        assertThat(defaultProps.getSampling().getProbability()).isEqualTo(0.1);
        assertThat(defaultProps.getSampling().getRateLimit()).isEqualTo(100);
        assertThat(defaultProps.getSampling().getPerService()).isEmpty();
        assertThat(defaultProps.getSampling().getPerOperation()).isEmpty();

        // Test Propagation properties
        assertThat(defaultProps.getPropagation()).isNotNull();
        assertThat(defaultProps.getPropagation().getType()).isEqualTo("tracecontext");
        assertThat(defaultProps.getPropagation().getAdditionalFormats()).isEmpty();
        assertThat(defaultProps.getPropagation().isB3SingleHeader()).isFalse();

        // Test Baggage properties
        assertThat(defaultProps.getBaggage()).isNotNull();
        assertThat(defaultProps.getBaggage().isEnabled()).isTrue();
        assertThat(defaultProps.getBaggage().getRemoteFields()).isEmpty();
        assertThat(defaultProps.getBaggage().getCorrelationFields()).isEmpty();
        assertThat(defaultProps.getBaggage().getMaxEntries()).isEqualTo(100);
        assertThat(defaultProps.getBaggage().getMaxValueLength()).isEqualTo(1000);

        // Test Resource properties
        assertThat(defaultProps.getResource()).isNotNull();
        assertThat(defaultProps.getResource().getServiceName()).isEqualTo("${spring.application.name:unknown-service}");
        assertThat(defaultProps.getResource().getServiceVersion()).isEqualTo("unknown");
        assertThat(defaultProps.getResource().getServiceNamespace()).isEqualTo("default");
        assertThat(defaultProps.getResource().getAttributes()).isEmpty();
        assertThat(defaultProps.getResource().isAutoDetect()).isTrue();
    }

    @Test
    void testCustomValues() {
        // Test that properties are correctly loaded from application.properties
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getOpenTelemetry().getEndpoint()).isEqualTo("http://custom-otel:4317");
        assertThat(properties.getZipkin().getBaseUrl()).isEqualTo("http://custom-zipkin:9411");
        assertThat(properties.getJaeger().getGrpcEndpoint()).isEqualTo("http://custom-jaeger:14250");
        assertThat(properties.getSampling().getProbability()).isEqualTo(0.5);
        assertThat(properties.getResource().getServiceName()).isEqualTo("test-service");
    }

    @Test
    void testSetters() {
        // Test that setters work correctly
        AdharTracingProperties props = new AdharTracingProperties();

        // Test global properties
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();

        Map<String, String> tags = new HashMap<>();
        tags.put("env", "test");
        tags.put("version", "1.0");
        props.setTags(tags);
        assertThat(props.getTags()).isEqualTo(tags);

        // Test OpenTelemetry properties
        props.getOpenTelemetry().setEnabled(false);
        assertThat(props.getOpenTelemetry().isEnabled()).isFalse();

        props.getOpenTelemetry().setEndpoint("http://test:4317");
        assertThat(props.getOpenTelemetry().getEndpoint()).isEqualTo("http://test:4317");

        props.getOpenTelemetry().setTimeout("PT10S");
        assertThat(props.getOpenTelemetry().getTimeout()).isEqualTo("PT10S");

        props.getOpenTelemetry().setUseGrpc(false);
        assertThat(props.getOpenTelemetry().isUseGrpc()).isFalse();

        // Test Zipkin properties
        props.getZipkin().setEnabled(true);
        assertThat(props.getZipkin().isEnabled()).isTrue();

        props.getZipkin().setBaseUrl("http://test-zipkin:9411");
        assertThat(props.getZipkin().getBaseUrl()).isEqualTo("http://test-zipkin:9411");

        props.getZipkin().setSenderType("kafka");
        assertThat(props.getZipkin().getSenderType()).isEqualTo("kafka");

        // Test Jaeger properties
        props.getJaeger().setEnabled(true);
        assertThat(props.getJaeger().isEnabled()).isTrue();

        props.getJaeger().setGrpcEndpoint("http://test-jaeger:14250");
        assertThat(props.getJaeger().getGrpcEndpoint()).isEqualTo("http://test-jaeger:14250");

        // Test Sampling properties
        props.getSampling().setProbability(0.8);
        assertThat(props.getSampling().getProbability()).isEqualTo(0.8);

        props.getSampling().setRateLimit(200);
        assertThat(props.getSampling().getRateLimit()).isEqualTo(200);

        // Test Propagation properties
        props.getPropagation().setType("b3");
        assertThat(props.getPropagation().getType()).isEqualTo("b3");

        props.getPropagation().setB3SingleHeader(true);
        assertThat(props.getPropagation().isB3SingleHeader()).isTrue();

        // Test Resource properties
        props.getResource().setServiceName("custom-service");
        assertThat(props.getResource().getServiceName()).isEqualTo("custom-service");

        props.getResource().setServiceVersion("2.0.0");
        assertThat(props.getResource().getServiceVersion()).isEqualTo("2.0.0");
    }

    @Test
    void testNestedPropertyConfiguration() {
        AdharTracingProperties props = new AdharTracingProperties();

        // Test OpenTelemetry headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("Custom-Header", "value");
        props.getOpenTelemetry().setHeaders(headers);
        assertThat(props.getOpenTelemetry().getHeaders()).isEqualTo(headers);

        // Test per-service sampling
        Map<String, Double> perService = new HashMap<>();
        perService.put("user-service", 1.0);
        perService.put("payment-service", 0.5);
        props.getSampling().setPerService(perService);
        assertThat(props.getSampling().getPerService()).isEqualTo(perService);

        // Test per-operation sampling
        Map<String, Double> perOperation = new HashMap<>();
        perOperation.put("user.login", 1.0);
        perOperation.put("user.list", 0.1);
        props.getSampling().setPerOperation(perOperation);
        assertThat(props.getSampling().getPerOperation()).isEqualTo(perOperation);

        // Test additional propagation formats
        String[] additionalFormats = {"b3", "jaeger"};
        props.getPropagation().setAdditionalFormats(additionalFormats);
        assertThat(props.getPropagation().getAdditionalFormats()).isEqualTo(additionalFormats);

        // Test baggage fields
        String[] remoteFields = {"user-id", "tenant-id"};
        props.getBaggage().setRemoteFields(remoteFields);
        assertThat(props.getBaggage().getRemoteFields()).isEqualTo(remoteFields);

        String[] correlationFields = {"correlation-id", "request-id"};
        props.getBaggage().setCorrelationFields(correlationFields);
        assertThat(props.getBaggage().getCorrelationFields()).isEqualTo(correlationFields);

        // Test resource attributes
        Map<String, String> attributes = new HashMap<>();
        attributes.put("deployment.environment", "staging");
        attributes.put("service.instance.id", "instance-123");
        props.getResource().setAttributes(attributes);
        assertThat(props.getResource().getAttributes()).isEqualTo(attributes);

        // Test controller skip patterns
        String[] skipPatterns = {"/health", "/metrics", "/actuator/**"};
        props.getWeb().setSkipPatterns(skipPatterns);
        assertThat(props.getWeb().getSkipPatterns()).isEqualTo(skipPatterns);
    }

    @Test
    void testDatabaseTracingConfiguration() {
        AdharTracingProperties props = new AdharTracingProperties();

        props.getDatabase().setEnabled(false);
        assertThat(props.getDatabase().isEnabled()).isFalse();

        props.getDatabase().setIncludeSqlStatements(false);
        assertThat(props.getDatabase().isIncludeSqlStatements()).isFalse();

        props.getDatabase().setIncludeSqlParameters(true);
        assertThat(props.getDatabase().isIncludeSqlParameters()).isTrue();

        props.getDatabase().setMaxSqlLength(500);
        assertThat(props.getDatabase().getMaxSqlLength()).isEqualTo(500);

        props.getDatabase().setTraceConnectionPool(false);
        assertThat(props.getDatabase().isTraceConnectionPool()).isFalse();
    }

    @Test
    void testMessagingTracingConfiguration() {
        AdharTracingProperties props = new AdharTracingProperties();

        props.getMessaging().setEnabled(false);
        assertThat(props.getMessaging().isEnabled()).isFalse();

        props.getMessaging().setTraceKafka(false);
        assertThat(props.getMessaging().isTraceKafka()).isFalse();

        props.getMessaging().setTraceRabbitmq(false);
        assertThat(props.getMessaging().isTraceRabbitmq()).isFalse();

        props.getMessaging().setTraceRedis(false);
        assertThat(props.getMessaging().isTraceRedis()).isFalse();

        props.getMessaging().setIncludePayloads(true);
        assertThat(props.getMessaging().isIncludePayloads()).isTrue();

        props.getMessaging().setMaxPayloadSize(2048);
        assertThat(props.getMessaging().getMaxPayloadSize()).isEqualTo(2048);
    }
}
