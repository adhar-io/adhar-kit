package com.adhar.adharkit.logging.util;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AdharLogger functionality.
 */
@ExtendWith(MockitoExtension.class)
class AdharLoggerTest {

    @Mock
    private AdharLoggingProperties properties;

    @Mock
    private AdharLoggingProperties.Mdc mdcProperties;

    @Mock
    private AdharLoggingProperties.Tracing tracingProperties;

    @Mock
    private AdharLoggingProperties.Masking maskingProperties;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    private ObjectMapper objectMapper;
    private AdharLogger adharLogger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Setup mock properties
        when(properties.getMdc()).thenReturn(mdcProperties);
        when(properties.getTracing()).thenReturn(tracingProperties);
        when(properties.getMasking()).thenReturn(maskingProperties);

        // Default MDC settings
        when(mdcProperties.isEnabled()).thenReturn(true);
        when(mdcProperties.isIncludeCorrelationId()).thenReturn(true);
        when(mdcProperties.isIncludeUserInfo()).thenReturn(true);
        when(mdcProperties.getCorrelationIdField()).thenReturn("correlationId");
        when(mdcProperties.getUserIdField()).thenReturn("userId");

        // Default tracing settings
        when(tracingProperties.isEnabled()).thenReturn(true);
        when(tracingProperties.isIncludeTraceId()).thenReturn(true);
        when(tracingProperties.isIncludeSpanId()).thenReturn(true);
        when(tracingProperties.isIncludeParentId()).thenReturn(true);
        when(tracingProperties.getTraceIdField()).thenReturn("traceId");
        when(tracingProperties.getSpanIdField()).thenReturn("spanId");
        when(tracingProperties.getParentIdField()).thenReturn("parentId");

        // Default masking settings
        when(maskingProperties.isEnabled()).thenReturn(true);

        adharLogger = new AdharLogger(properties, tracer, objectMapper);

        // Clear MDC before each test
        MDC.clear();
    }

    // ==================== Constructor Tests ====================

    @Test
    void constructor_WithNullProperties_ThrowsException() {
        assertThrows(NullPointerException.class,
            () -> new AdharLogger(null, tracer, objectMapper));
    }

    @Test
    void constructor_WithNullObjectMapper_UsesDefault() {
        AdharLogger logger = new AdharLogger(properties, tracer, null);
        assertNotNull(logger);
        assertNotNull(logger.getObjectMapper());
    }

    @Test
    void constructor_WithoutTracer_CreatesSuccessfully() {
        AdharLogger logger = new AdharLogger(properties);
        assertNotNull(logger);
        assertNotNull(logger.getObjectMapper());
    }

    // ==================== Correlation ID Tests ====================

    @Test
    void ensureCorrelationId_WithExistingId_ReturnsExistingId() {
        String existingId = "existing-123";
        String result = adharLogger.ensureCorrelationId(existingId);

        assertThat(result).isEqualTo(existingId);
        assertThat(MDC.get("correlationId")).isEqualTo(existingId);
    }

    @Test
    void ensureCorrelationId_WithNullId_GeneratesNewId() {
        String result = adharLogger.ensureCorrelationId(null);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(32); // UUID without dashes
        assertThat(MDC.get("correlationId")).isEqualTo(result);
    }

    @Test
    void ensureCorrelationId_WhenMdcDisabled_ReturnsInputWithoutSettingMdc() {
        when(mdcProperties.isEnabled()).thenReturn(false);
        String inputId = "test-id";

        String result = adharLogger.ensureCorrelationId(inputId);

        assertThat(result).isEqualTo(inputId);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void getCorrelationId_WithExistingId_ReturnsId() {
        String correlationId = "test-correlation-id";
        MDC.put("correlationId", correlationId);

        String result = adharLogger.getCorrelationId();

        assertThat(result).isEqualTo(correlationId);
    }

    @Test
    void getCorrelationId_WhenMdcDisabled_ReturnsNull() {
        when(mdcProperties.isEnabled()).thenReturn(false);
        MDC.put("correlationId", "test-id");

        String result = adharLogger.getCorrelationId();

        assertThat(result).isNull();
    }

    // ==================== User ID Tests ====================

    @Test
    void setUserId_WithValidId_SetsMdcValue() {
        String userId = "user123";

        adharLogger.setUserId(userId);

        assertThat(MDC.get("userId")).isEqualTo(userId);
    }

    @Test
    void setUserId_WithNullId_DoesNotSetMdc() {
        adharLogger.setUserId(null);

        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void setUserId_WhenMdcDisabled_DoesNotSetMdc() {
        when(mdcProperties.isEnabled()).thenReturn(false);

        adharLogger.setUserId("user123");

        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void getUserId_WithExistingId_ReturnsId() {
        String userId = "user123";
        MDC.put("userId", userId);

        String result = adharLogger.getUserId();

        assertThat(result).isEqualTo(userId);
    }

    // ==================== MDC Operations Tests ====================

    @Test
    void put_WithValidKeyValue_SetsMdcValue() {
        String key = "testKey";
        String value = "testValue";

        adharLogger.put(key, value);

        assertThat(MDC.get(key)).isEqualTo(value);
    }

    @Test
    void put_WithNullValue_DoesNotSetMdc() {
        adharLogger.put("testKey", null);

        assertThat(MDC.get("testKey")).isNull();
    }

    @Test
    void get_WithExistingKey_ReturnsValue() {
        String key = "testKey";
        String value = "testValue";
        MDC.put(key, value);

        String result = adharLogger.get(key);

        assertThat(result).isEqualTo(value);
    }

    @Test
    void putAll_WithValidMap_SetsAllMdcValues() {
        Map<String, String> entries = Map.of(
            "key1", "value1",
            "key2", "value2"
        );

        adharLogger.putAll(entries);

        assertThat(MDC.get("key1")).isEqualTo("value1");
        assertThat(MDC.get("key2")).isEqualTo("value2");
    }

    @Test
    void remove_WithExistingKey_RemovesMdcValue() {
        String key = "testKey";
        MDC.put(key, "testValue");

        adharLogger.remove(key);

        assertThat(MDC.get(key)).isNull();
    }

    @Test
    void getCurrentMdcContext_WithExistingEntries_ReturnsContextMap() {
        MDC.put("key1", "value1");
        MDC.put("key2", "value2");

        Map<String, String> context = adharLogger.getCurrentMdcContext();

        assertThat(context).containsEntry("key1", "value1");
        assertThat(context).containsEntry("key2", "value2");
    }

    // ==================== Context Management Tests ====================

    @Test
    void withContext_ExecutesRunnableWithTemporaryContext() {
        String originalValue = "original";
        String temporaryValue = "temporary";
        String key = "testKey";

        MDC.put(key, originalValue);

        AtomicReference<String> capturedValue = new AtomicReference<>();

        adharLogger.withContext(Map.of(key, temporaryValue), () -> {
            capturedValue.set(MDC.get(key));
        });

        assertThat(capturedValue.get()).isEqualTo(temporaryValue);
        assertThat(MDC.get(key)).isEqualTo(originalValue);
    }

    @Test
    void withContextSupplier_ExecutesSupplierWithTemporaryContextAndReturnsValue() {
        String key = "testKey";
        String temporaryValue = "temporary";

        String result = adharLogger.withContextSupplier(Map.of(key, temporaryValue), () -> {
            return MDC.get(key);
        });

        assertThat(result).isEqualTo(temporaryValue);
        assertThat(MDC.get(key)).isNull();
    }

    @Test
    void withContext_WithNullEntries_ExecutesRunnableDirectly() {
        AtomicReference<Boolean> executed = new AtomicReference<>(false);

        adharLogger.withContext(null, () -> executed.set(true));

        assertThat(executed.get()).isTrue();
    }

    // ==================== Tracing Integration Tests ====================

    @Test
    void setTraceId_WithValidId_SetsMdcValue() {
        String traceId = "trace123";

        adharLogger.setTraceId(traceId);

        assertThat(MDC.get("traceId")).isEqualTo(traceId);
    }

    @Test
    void setTraceId_WithTracerAndCurrentSpan_SetsTraceIdFromSpan() {
        String expectedTraceId = "span-trace-123";
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(expectedTraceId);

        adharLogger.setTraceId(null);

        assertThat(MDC.get("traceId")).isEqualTo(expectedTraceId);
    }

    @Test
    void setSpanId_WithValidId_SetsMdcValue() {
        String spanId = "span123";

        adharLogger.setSpanId(spanId);

        assertThat(MDC.get("spanId")).isEqualTo(spanId);
    }

    @Test
    void setParentId_WithValidId_SetsMdcValue() {
        String parentId = "parent123";

        adharLogger.setParentId(parentId);

        assertThat(MDC.get("parentId")).isEqualTo(parentId);
    }

    @Test
    void setTracingInfo_WithTracer_SetsAllTracingInfo() {
        String expectedTraceId = "trace123";
        String expectedSpanId = "span123";
        String expectedParentId = "parent123";

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(expectedTraceId);
        when(traceContext.spanId()).thenReturn(expectedSpanId);
        when(traceContext.parentId()).thenReturn(expectedParentId);

        adharLogger.setTracingInfo();

        assertThat(MDC.get("traceId")).isEqualTo(expectedTraceId);
        assertThat(MDC.get("spanId")).isEqualTo(expectedSpanId);
        assertThat(MDC.get("parentId")).isEqualTo(expectedParentId);
    }

    // ==================== JSON Logging Tests ====================

    @Test
    void infoJson_WithValidData_LogsJsonStructure() {
        Map<String, Object> data = Map.of("key", "value", "number", 42);

        // This would normally require a logger mock, but we're testing the method exists and doesn't throw
        assertDoesNotThrow(() -> adharLogger.infoJson(AdharLoggerTest.class, "Test message", data));
    }

    @Test
    void debugJson_WithValidData_LogsJsonStructure() {
        Map<String, Object> data = Map.of("debug", "data");

        assertDoesNotThrow(() -> adharLogger.debugJson(AdharLoggerTest.class, "Debug message", data));
    }

    @Test
    void warnJson_WithValidData_LogsJsonStructure() {
        Map<String, Object> data = Map.of("warn", "data");

        assertDoesNotThrow(() -> adharLogger.warnJson(AdharLoggerTest.class, "Warn message", data));
    }

    @Test
    void errorJson_WithValidData_LogsJsonStructure() {
        Map<String, Object> data = Map.of("error", "data");

        assertDoesNotThrow(() -> adharLogger.errorJson(AdharLoggerTest.class, "Error message", data));
    }

    @Test
    void traceJson_WithValidData_LogsJsonStructure() {
        Map<String, Object> data = Map.of("trace", "data");

        assertDoesNotThrow(() -> adharLogger.traceJson(AdharLoggerTest.class, "Trace message", data));
    }

    // ==================== Utility Methods Tests ====================

    @Test
    void isMdcEnabled_ReturnsCorrectValue() {
        when(mdcProperties.isEnabled()).thenReturn(true);
        assertThat(adharLogger.isMdcEnabled()).isTrue();

        when(mdcProperties.isEnabled()).thenReturn(false);
        assertThat(adharLogger.isMdcEnabled()).isFalse();
    }

    @Test
    void isTracingEnabled_ReturnsCorrectValue() {
        when(tracingProperties.isEnabled()).thenReturn(true);
        assertThat(adharLogger.isTracingEnabled()).isTrue();

        when(tracingProperties.isEnabled()).thenReturn(false);
        assertThat(adharLogger.isTracingEnabled()).isFalse();
    }

    @Test
    void isMaskingEnabled_ReturnsCorrectValue() {
        when(maskingProperties.isEnabled()).thenReturn(true);
        assertThat(adharLogger.isMaskingEnabled()).isTrue();

        when(maskingProperties.isEnabled()).thenReturn(false);
        assertThat(adharLogger.isMaskingEnabled()).isFalse();
    }

    @Test
    void getObjectMapper_ReturnsConfiguredMapper() {
        ObjectMapper mapper = adharLogger.getObjectMapper();
        assertThat(mapper).isNotNull();
        assertThat(mapper).isSameAs(objectMapper);
    }

    @Test
    void getProperties_ReturnsConfiguredProperties() {
        AdharLoggingProperties props = adharLogger.getProperties();
        assertThat(props).isNotNull();
        assertThat(props).isSameAs(properties);
    }

    @Test
    void logger_WithClass_ReturnsLoggerForClass() {
        var logger = adharLogger.logger(AdharLoggerTest.class);
        assertThat(logger).isNotNull();
        assertThat(logger.getName()).isEqualTo(AdharLoggerTest.class.getName());
    }

    // ==================== Convenience Logging Methods Tests ====================

    @Test
    void info_WithClassAndMessage_DoesNotThrow() {
        assertDoesNotThrow(() -> adharLogger.info(AdharLoggerTest.class, "Test info message"));
    }

    @Test
    void debug_WithClassAndMessage_DoesNotThrow() {
        assertDoesNotThrow(() -> adharLogger.debug(AdharLoggerTest.class, "Test debug message"));
    }

    @Test
    void warn_WithClassAndMessage_DoesNotThrow() {
        assertDoesNotThrow(() -> adharLogger.warn(AdharLoggerTest.class, "Test warn message"));
    }

    @Test
    void error_WithClassAndMessage_DoesNotThrow() {
        assertDoesNotThrow(() -> adharLogger.error(AdharLoggerTest.class, "Test error message"));
    }

    @Test
    void error_WithClassMessageAndThrowable_DoesNotThrow() {
        Exception testException = new RuntimeException("Test exception");
        assertDoesNotThrow(() -> adharLogger.error(AdharLoggerTest.class, "Test error message", testException));
    }

    @Test
    void trace_WithClassAndMessage_DoesNotThrow() {
        assertDoesNotThrow(() -> adharLogger.trace(AdharLoggerTest.class, "Test trace message"));
    }

    // ==================== Edge Cases Tests ====================

    @Test
    void clear_ClearsMdcContext() {
        MDC.put("key1", "value1");
        MDC.put("key2", "value2");

        adharLogger.clear();

        assertThat(MDC.get("key1")).isNull();
        assertThat(MDC.get("key2")).isNull();
    }

    @Test
    void withContext_WithNullRunnable_ThrowsException() {
        assertThrows(NullPointerException.class,
            () -> adharLogger.withContext(Map.of("key", "value"), null));
    }

    @Test
    void withContextSupplier_WithNullSupplier_ThrowsException() {
        assertThrows(NullPointerException.class,
            () -> adharLogger.withContextSupplier(Map.of("key", "value"), null));
    }
}
