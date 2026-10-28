package com.adhar.kit.dapr.observation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where the trace context of a Dapr call comes from.
 */
class TraceContextTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void theInboundRequestsHeadersAreUsedWhenNoTracerIsConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-aaaa-bbbb-01");
        request.addHeader("API-Version", "2.1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(new RequestHeaderTraceContext().current()).isEqualTo(Map.of("traceparent", "00-aaaa-bbbb-01"));
        assertThat(RequestHeaderTraceContext.headers("API-Version")).containsEntry("API-Version", "2.1.0");
        RequestContextHolder.resetRequestAttributes();
        assertThat(new RequestHeaderTraceContext().current()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theCurrentSpanIsRenderedWithTheConfiguredPropagator() {
        Tracer tracer = mock(Tracer.class);
        Propagator propagator = mock(Propagator.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        doAnswer(invocation -> {
            Propagator.Setter<Object> setter = invocation.getArgument(2);
            setter.set(invocation.getArgument(1), "traceparent", "00-span-01");
            setter.set(invocation.getArgument(1), "tracestate", "k=v");
            setter.set(invocation.getArgument(1), "b3", "ignored");
            return null;
        }).when(propagator).inject(eq(traceContext), any(), any(Propagator.Setter.class));

        MicrometerTraceContext context = new MicrometerTraceContext(tracer, propagator);
        assertThat(context.current()).isEqualTo(Map.of("traceparent", "00-span-01", "tracestate", "k=v"));

        when(tracer.currentSpan()).thenReturn(null);
        assertThat(context.current()).isEmpty();
        assertThat(new MicrometerTraceContext(() -> null, () -> propagator).current()).isEmpty();
    }

    @Test
    void contextsFallBackInOrder() {
        DaprTraceContext span = Map::of;
        DaprTraceContext inbound = () -> Map.of("traceparent", "from-request");
        assertThat(span.orElse(inbound).current()).containsEntry("traceparent", "from-request");
        DaprTraceContext withSpan = () -> Map.of("traceparent", "from-span");
        assertThat(withSpan.orElse(inbound).current()).containsEntry("traceparent", "from-span");
    }
}
