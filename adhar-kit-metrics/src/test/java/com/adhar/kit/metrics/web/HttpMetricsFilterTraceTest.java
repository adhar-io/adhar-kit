package com.adhar.kit.metrics.web;

import com.adhar.kit.metrics.trace.TraceContext;
import com.adhar.kit.metrics.util.TagCardinalityLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the trace-correlation (MDC) behaviour of {@link HttpMetricsFilter}.
 */
class HttpMetricsFilterTraceTest {

    private static TraceContext fixedContext(String traceId, String spanId) {
        return new TraceContext() {
            @Override
            public Optional<String> currentTraceId() {
                return Optional.ofNullable(traceId);
            }

            @Override
            public Optional<String> currentSpanId() {
                return Optional.ofNullable(spanId);
            }

            @Override
            public boolean isSampled() {
                return true;
            }
        };
    }

    @Test
    void traceIdAndSpanId_arePresentInMdcDuringChainAndClearedAfter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsFilter filter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(100),
                null, fixedContext("trace-99", "span-7"));

        AtomicReference<String> traceDuring = new AtomicReference<>();
        AtomicReference<String> spanDuring = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            traceDuring.set(MDC.get(TraceContext.TRACE_ID_KEY));
            spanDuring.set(MDC.get(TraceContext.SPAN_ID_KEY));
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), chain);

        assertThat(traceDuring.get()).isEqualTo("trace-99");
        assertThat(spanDuring.get()).isEqualTo("span-7");
        // MDC is cleaned up after the request.
        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(TraceContext.SPAN_ID_KEY)).isNull();
    }

    @Test
    void noTraceContext_leavesMdcUntouched() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsFilter filter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(100));

        AtomicReference<String> traceDuring = new AtomicReference<>("sentinel");
        FilterChain chain = (req, res) -> {
            traceDuring.set(MDC.get(TraceContext.TRACE_ID_KEY));
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/api/y"), new MockHttpServletResponse(), chain);

        assertThat(traceDuring.get()).isNull();
    }

    @Test
    void inactiveTrace_doesNotSetMdc() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsFilter filter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(100),
                null, fixedContext(null, null));

        AtomicReference<String> traceDuring = new AtomicReference<>("sentinel");
        FilterChain chain = (req, res) -> {
            traceDuring.set(MDC.get(TraceContext.TRACE_ID_KEY));
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/api/z"), new MockHttpServletResponse(), chain);

        assertThat(traceDuring.get()).isNull();
    }
}
