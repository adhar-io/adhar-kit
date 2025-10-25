package com.adhar.adharkit.logging.util;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoggingUtils}.
 */
@ExtendWith(MockitoExtension.class)
class LoggingUtilsTest {

    @Mock
    private AdharLoggingProperties properties;

    @Mock
    private AdharLoggingProperties.MdcLoggingProperties mdcProperties;

    @Mock
    private AdharLoggingProperties.TracingProperties tracingProperties;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    private LoggingUtils loggingUtils;
    private LoggingUtils loggingUtilsWithTracer;

    @BeforeEach
    void setUp() {
        when(properties.getMdc()).thenReturn(mdcProperties);
        when(properties.getTracing()).thenReturn(tracingProperties);
        
        // Default configuration
        when(mdcProperties.isEnabled()).thenReturn(true);
        when(mdcProperties.isIncludeCorrelationId()).thenReturn(true);
        when(mdcProperties.getCorrelationIdField()).thenReturn("correlationId");
        when(mdcProperties.isIncludeUserInfo()).thenReturn(true);
        when(mdcProperties.getUserIdField()).thenReturn("userId");
        
        when(tracingProperties.isEnabled()).thenReturn(true);
        when(tracingProperties.isIncludeTraceId()).thenReturn(true);
        when(tracingProperties.getTraceIdField()).thenReturn("traceId");
        when(tracingProperties.isIncludeSpanId()).thenReturn(true);
        when(tracingProperties.getSpanIdField()).thenReturn("spanId");
        when(tracingProperties.isIncludeParentId()).thenReturn(true);
        when(tracingProperties.getParentIdField()).thenReturn("parentId");
        when(tracingProperties.isIncludeSampled()).thenReturn(true);
        when(tracingProperties.getSampledField()).thenReturn("sampled");
        
        loggingUtils = new LoggingUtils(properties);
        loggingUtilsWithTracer = new LoggingUtils(properties, tracer);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void setCorrelationId_WithProvidedId_ShouldSetMdcValue() {
        // Given
        String correlationId = "test-correlation-id";
        
        // When
        String result = loggingUtils.setCorrelationId(correlationId);
        
        // Then
        assertThat(result).isEqualTo(correlationId);
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    @Test
    void setCorrelationId_WithNullId_ShouldGenerateNewId() {
        // When
        String result = loggingUtils.setCorrelationId(null);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(MDC.get("correlationId")).isEqualTo(result);
    }

    @Test
    void setCorrelationId_WhenDisabled_ShouldNotSetMdcValue() {
        // Given
        when(mdcProperties.isEnabled()).thenReturn(false);
        String correlationId = "test-correlation-id";
        
        // When
        String result = loggingUtils.setCorrelationId(correlationId);
        
        // Then
        assertThat(result).isEqualTo(correlationId);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void getCorrelationId_WhenSet_ShouldReturnValue() {
        // Given
        String correlationId = "test-correlation-id";
        MDC.put("correlationId", correlationId);
        
        // When
        String result = loggingUtils.getCorrelationId();
        
        // Then
        assertThat(result).isEqualTo(correlationId);
    }

    @Test
    void setUserId_ShouldSetMdcValue() {
        // Given
        String userId = "test-user";
        
        // When
        loggingUtils.setUserId(userId);
        
        // Then
        assertThat(MDC.get("userId")).isEqualTo(userId);
    }

    @Test
    void getUserId_WhenSet_ShouldReturnValue() {
        // Given
        String userId = "test-user";
        MDC.put("userId", userId);
        
        // When
        String result = loggingUtils.getUserId();
        
        // Then
        assertThat(result).isEqualTo(userId);
    }

    @Test
    void putMdc_ShouldSetCustomValue() {
        // Given
        String key = "customKey";
        String value = "customValue";
        
        // When
        loggingUtils.putMdc(key, value);
        
        // Then
        assertThat(MDC.get(key)).isEqualTo(value);
    }

    @Test
    void getMdc_WhenSet_ShouldReturnValue() {
        // Given
        String key = "customKey";
        String value = "customValue";
        MDC.put(key, value);
        
        // When
        String result = loggingUtils.getMdc(key);
        
        // Then
        assertThat(result).isEqualTo(value);
    }

    @Test
    void clearMdc_ShouldRemoveAllValues() {
        // Given
        MDC.put("key1", "value1");
        MDC.put("key2", "value2");
        
        // When
        loggingUtils.clearMdc();
        
        // Then
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void setTraceId_WithProvidedId_ShouldSetMdcValue() {
        // Given
        String traceId = "test-trace-id";
        
        // When
        String result = loggingUtilsWithTracer.setTraceId(traceId);
        
        // Then
        assertThat(result).isEqualTo(traceId);
        assertThat(MDC.get("traceId")).isEqualTo(traceId);
    }

    @Test
    void setTraceId_WithNullIdAndTracer_ShouldGetFromCurrentSpan() {
        // Given
        String traceId = "span-trace-id";
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(traceId);
        
        // When
        String result = loggingUtilsWithTracer.setTraceId(null);
        
        // Then
        assertThat(result).isEqualTo(traceId);
        assertThat(MDC.get("traceId")).isEqualTo(traceId);
    }

    @Test
    void setSpanId_WithProvidedId_ShouldSetMdcValue() {
        // Given
        String spanId = "test-span-id";
        
        // When
        String result = loggingUtilsWithTracer.setSpanId(spanId);
        
        // Then
        assertThat(result).isEqualTo(spanId);
        assertThat(MDC.get("spanId")).isEqualTo(spanId);
    }

    @Test
    void setSpanId_WithNullIdAndTracer_ShouldGetFromCurrentSpan() {
        // Given
        String spanId = "current-span-id";
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.spanId()).thenReturn(spanId);
        
        // When
        String result = loggingUtilsWithTracer.setSpanId(null);
        
        // Then
        assertThat(result).isEqualTo(spanId);
        assertThat(MDC.get("spanId")).isEqualTo(spanId);
    }

    @Test
    void setTracingInfo_ShouldSetAllTracingFields() {
        // Given
        String traceId = "test-trace-id";
        String spanId = "test-span-id";
        String parentId = "test-parent-id";
        
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(traceId);
        when(traceContext.spanId()).thenReturn(spanId);
        when(traceContext.parentId()).thenReturn(parentId);
        when(traceContext.sampled()).thenReturn(true);
        
        // When
        loggingUtilsWithTracer.setTracingInfo();
        
        // Then
        assertThat(MDC.get("traceId")).isEqualTo(traceId);
        assertThat(MDC.get("spanId")).isEqualTo(spanId);
        assertThat(MDC.get("parentId")).isEqualTo(parentId);
        assertThat(MDC.get("sampled")).isEqualTo("true");
    }
}