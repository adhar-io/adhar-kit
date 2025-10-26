package com.adhar.adharkit.logging.filter;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.AdharLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MdcLoggingFilter}.
 */
@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

    @Mock
    private AdharLoggingProperties properties;

    @Mock
    private AdharLoggingProperties.MdcLoggingProperties mdcProperties;

    @Mock
    private AdharLoggingProperties.TracingProperties tracingProperties;

    @Mock
    private AdharLogger adharLogger;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    // No security-related mocks needed

    private MdcLoggingFilter filter;

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

        filter = new MdcLoggingFilter(properties, adharLogger);
    }

    @Test
    void doFilterInternal_ShouldAddCorrelationIdToMdc() throws ServletException, IOException {
        // Given
        String correlationId = "test-correlation-id";
        when(request.getHeader("correlationId")).thenReturn(correlationId);
        when(adharLogger.setCorrelationId(correlationId)).thenReturn(correlationId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).setCorrelationId(correlationId);
        verify(response).setHeader("correlationId", correlationId);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    @Test
    void doFilterInternal_WithNoCorrelationId_ShouldGenerateNewId() throws ServletException, IOException {
        // Given
        String generatedId = "generated-id";
        when(request.getHeader("correlationId")).thenReturn(null);
        when(adharLogger.setCorrelationId(null)).thenReturn(generatedId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).setCorrelationId(null);
        verify(response).setHeader("correlationId", generatedId);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    @Test
    void doFilterInternal_WithTracing_ShouldAddTraceIdToMdc() throws ServletException, IOException {
        // Given
        String traceId = "test-trace-id";
        when(request.getHeader("traceId")).thenReturn(traceId);
        when(adharLogger.setTraceId(traceId)).thenReturn(traceId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).setTraceId(traceId);
        verify(response).setHeader("traceId", traceId);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    @Test
    void doFilterInternal_WithSpanId_ShouldAddSpanIdToMdc() throws ServletException, IOException {
        // Given
        String spanId = "test-span-id";
        when(request.getHeader("spanId")).thenReturn(spanId);
        when(adharLogger.setSpanId(spanId)).thenReturn(spanId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).setSpanId(spanId);
        verify(response).setHeader("spanId", spanId);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    @Test
    void doFilterInternal_WithTraceparentHeader_ShouldExtractTraceAndSpanIds() throws ServletException, IOException {
        // Given
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";

        when(request.getHeader("traceparent")).thenReturn(traceparent);
        when(request.getHeader("traceId")).thenReturn(null);
        when(request.getHeader("spanId")).thenReturn(null);
        when(adharLogger.setTraceId(traceId)).thenReturn(traceId);
        when(adharLogger.setSpanId(spanId)).thenReturn(spanId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).setTraceId(traceId);
        verify(adharLogger).setSpanId(spanId);
        verify(response).setHeader("traceId", traceId);
        verify(response).setHeader("spanId", spanId);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    @Test
    void doFilterInternal_WithB3Headers_ShouldExtractTraceAndSpanIds() throws ServletException, IOException {
        // Given
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";

        when(request.getHeader("X-B3-TraceId")).thenReturn(traceId);
        when(request.getHeader("X-B3-SpanId")).thenReturn(spanId);
        when(request.getHeader("traceId")).thenReturn(null);
        when(request.getHeader("spanId")).thenReturn(null);
        when(adharLogger.setTraceId(traceId)).thenReturn(traceId);
        when(adharLogger.setSpanId(spanId)).thenReturn(spanId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).setTraceId(traceId);
        verify(adharLogger).setSpanId(spanId);
        verify(response).setHeader("traceId", traceId);
        verify(response).setHeader("spanId", spanId);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    // Security-related test removed as Spring Security is not a required dependency

    @Test
    void doFilterInternal_ShouldAddRequestInfoToMdc() throws ServletException, IOException {
        // Given
        String method = "GET";
        String uri = "/api/test";
        String remoteAddr = "127.0.0.1";
        String userAgent = "Mozilla/5.0";

        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("User-Agent")).thenReturn(userAgent);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(adharLogger).putMdc("requestMethod", method);
        verify(adharLogger).putMdc("requestUri", uri);
        verify(adharLogger).putMdc("clientIp", remoteAddr);
        verify(adharLogger).putMdc("userAgent", userAgent);
        verify(filterChain).doFilter(request, response);
        verify(adharLogger).clearMdc();
    }

    @Test
    void doFilterInternal_WhenExceptionThrown_ShouldStillClearMdc() throws ServletException, IOException {
        // Given
        doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(request, response);

        // When/Then
        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException e) {
            // Expected
        }

        // Verify MDC is cleared even when exception occurs
        verify(adharLogger).clearMdc();
    }
}
