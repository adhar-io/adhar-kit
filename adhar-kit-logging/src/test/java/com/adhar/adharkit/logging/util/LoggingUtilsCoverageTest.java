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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Supplemental coverage tests for {@link LoggingUtils} using real (default-enabled)
 * {@link AdharLoggingProperties} to exercise tenant/session/request and tracing paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoggingUtilsCoverageTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    private AdharLoggingProperties properties;
    private LoggingUtils utils;
    private LoggingUtils utilsWithTracer;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        utils = new LoggingUtils(properties);
        utilsWithTracer = new LoggingUtils(properties, tracer);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void tenantIdRoundTrip() {
        assertThat(utils.setTenantId("tenant-1")).isEqualTo("tenant-1");
        assertThat(MDC.get("tenantId")).isEqualTo("tenant-1");
        assertThat(utils.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    void tenantIdNullReturnsNullWithoutMdc() {
        assertThat(utils.setTenantId(null)).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void sessionIdRoundTrip() {
        assertThat(utils.setSessionId("sess-1")).isEqualTo("sess-1");
        assertThat(MDC.get("sessionId")).isEqualTo("sess-1");
        assertThat(utils.getSessionId()).isEqualTo("sess-1");
    }

    @Test
    void requestIdRoundTrip() {
        assertThat(utils.setRequestId("req-1")).isEqualTo("req-1");
        assertThat(MDC.get("requestId")).isEqualTo("req-1");
        assertThat(utils.getRequestId()).isEqualTo("req-1");
    }

    @Test
    void putGetRemoveMdc() {
        utils.putMdc("k", "v");
        assertThat(utils.getMdc("k")).isEqualTo("v");
        utils.removeMdc("k");
        assertThat(utils.getMdc("k")).isNull();
        // null-safe variants
        utils.putMdc(null, "v");
        utils.removeMdc(null);
        assertThat(utils.getMdc(null)).isNull();
    }

    @Test
    void getMdcContextReturnsCopy() {
        MDC.put("a", "1");
        assertThat(utils.getMdcContext()).containsEntry("a", "1");
    }

    @Test
    void disabledMdcSkipsAllOperations() {
        properties.getMdc().setEnabled(false);

        assertThat(utils.setCorrelationId("c")).isEqualTo("c");
        assertThat(utils.getCorrelationId()).isNull();
        assertThat(utils.setUserId("u")).isEqualTo("u");
        assertThat(utils.getUserId()).isNull();
        assertThat(utils.setTenantId("t")).isEqualTo("t");
        assertThat(utils.getTenantId()).isNull();
        assertThat(utils.setSessionId("s")).isEqualTo("s");
        assertThat(utils.getSessionId()).isNull();
        assertThat(utils.setRequestId("r")).isEqualTo("r");
        assertThat(utils.getRequestId()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void setTracingInfoWithNoTracerDoesNothing() {
        // utils has no tracer
        utils.setTracingInfo();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void setTracingInfoDisabledDoesNothing() {
        properties.getTracing().setEnabled(false);
        utilsWithTracer.setTracingInfo();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void setTracingInfoWithNullSpanDoesNothing() {
        when(tracer.currentSpan()).thenReturn(null);
        utilsWithTracer.setTracingInfo();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void setTraceIdDisabledReturnsValueWithoutMdc() {
        properties.getTracing().setEnabled(false);
        assertThat(utilsWithTracer.setTraceId("trace-x")).isEqualTo("trace-x");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void setTraceIdNullWithNoSpanReturnsNull() {
        when(tracer.currentSpan()).thenReturn(null);
        assertThat(utilsWithTracer.setTraceId(null)).isNull();
    }

    @Test
    void setSpanIdDisabledReturnsValueWithoutMdc() {
        properties.getTracing().setEnabled(false);
        assertThat(utilsWithTracer.setSpanId("span-x")).isEqualTo("span-x");
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void setSpanIdNullWithNoSpanReturnsNull() {
        when(tracer.currentSpan()).thenReturn(null);
        assertThat(utilsWithTracer.setSpanId(null)).isNull();
    }

    @Test
    void setTracingInfoPopulatesAllFields() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("tr");
        when(traceContext.spanId()).thenReturn("sp");
        when(traceContext.parentId()).thenReturn("pa");
        when(traceContext.sampled()).thenReturn(Boolean.TRUE);

        utilsWithTracer.setTracingInfo();

        assertThat(MDC.get("traceId")).isEqualTo("tr");
        assertThat(MDC.get("spanId")).isEqualTo("sp");
        assertThat(MDC.get("parentId")).isEqualTo("pa");
        assertThat(MDC.get("sampled")).isEqualTo("true");
    }
}
