package com.adhar.kit.tracing.web;

import com.adhar.kit.tracing.util.AdharTracing;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TraceContextMdcFilter}, using Spring's {@link MockHttpServletRequest}/
 * {@link MockHttpServletResponse} rather than a real servlet container.
 */
class TraceContextMdcFilterTest {

    @Test
    void injectsTraceAndSpanIdIntoMdcForRequestDurationThenClears() throws Exception {
        AdharTracing adharTracing = mock(AdharTracing.class);
        when(adharTracing.getCurrentTraceId()).thenReturn("trace-123");
        when(adharTracing.getCurrentSpanId()).thenReturn("span-456");

        TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, new String[0]);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] observedTraceId = new String[1];
        String[] observedSpanId = new String[1];
        FilterChain chain = (req, res) -> {
            observedTraceId[0] = MDC.get(TraceContextMdcFilter.TRACE_ID_MDC_KEY);
            observedSpanId[0] = MDC.get(TraceContextMdcFilter.SPAN_ID_MDC_KEY);
        };

        MDC.clear();
        filter.doFilter(request, response, chain);

        assertThat(observedTraceId[0]).isEqualTo("trace-123");
        assertThat(observedSpanId[0]).isEqualTo("span-456");
        // Cleared after the request completes so it never leaks onto a pooled thread.
        assertThat(MDC.get(TraceContextMdcFilter.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(TraceContextMdcFilter.SPAN_ID_MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() throws Exception {
        AdharTracing adharTracing = mock(AdharTracing.class);
        when(adharTracing.getCurrentTraceId()).thenReturn("trace-1");
        when(adharTracing.getCurrentSpanId()).thenReturn("span-1");

        TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, new String[0]);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException boom = new RuntimeException("boom");
        FilterChain chain = (req, res) -> { throw boom; };

        MDC.clear();
        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException caught) {
            assertThat(caught).isSameAs(boom);
        }

        assertThat(MDC.get(TraceContextMdcFilter.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(TraceContextMdcFilter.SPAN_ID_MDC_KEY)).isNull();
    }

    @Test
    void doesNotPutMdcEntriesWhenNoCurrentTraceOrSpan() throws Exception {
        AdharTracing adharTracing = mock(AdharTracing.class);
        when(adharTracing.getCurrentTraceId()).thenReturn(null);
        when(adharTracing.getCurrentSpanId()).thenReturn(null);

        TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, new String[0]);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/no-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] observedTraceId = new String[]{"unset"};
        FilterChain chain = (req, res) -> observedTraceId[0] = MDC.get(TraceContextMdcFilter.TRACE_ID_MDC_KEY);

        MDC.clear();
        filter.doFilter(request, response, chain);

        assertThat(observedTraceId[0]).isNull();
    }

    @Test
    void skipsFilteringForConfiguredSkipPatterns() throws Exception {
        AdharTracing adharTracing = mock(AdharTracing.class);
        when(adharTracing.getCurrentTraceId()).thenReturn("trace-xyz");
        when(adharTracing.getCurrentSpanId()).thenReturn("span-xyz");

        TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, new String[]{"/actuator/**", "/health/**"});

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        MDC.clear();
        filter.doFilter(request, response, chain);

        // Since the path is skipped, AdharTracing should never even be consulted for
        // trace/span ids, and the chain still proceeds.
        verify(adharTracing, never()).getCurrentTraceId();
        verify(chain).doFilter(request, response);
        assertThat(MDC.get(TraceContextMdcFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void doesNotSkipRequestsNotMatchingSkipPatterns() throws Exception {
        AdharTracing adharTracing = mock(AdharTracing.class);
        when(adharTracing.getCurrentTraceId()).thenReturn("trace-1");
        when(adharTracing.getCurrentSpanId()).thenReturn("span-1");

        TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, new String[]{"/actuator/**"});

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        MDC.clear();
        filter.doFilter(request, response, chain);

        verify(adharTracing).getCurrentTraceId();
        verify(chain).doFilter(request, response);
    }

    @Test
    void nullSkipPatternsArrayIsTreatedAsEmpty() throws Exception {
        AdharTracing adharTracing = mock(AdharTracing.class);
        when(adharTracing.getCurrentTraceId()).thenReturn("trace-1");
        when(adharTracing.getCurrentSpanId()).thenReturn("span-1");

        TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
