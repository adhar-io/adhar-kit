package com.adhar.kit.tracing.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.tracing.api.TracingService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SpringTracingAdapter} including its inner {@code SpringSpanBuilder}
 * and {@code SpringSpan} implementations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpringTracingAdapterTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Span.Builder builder;

    @Mock
    private Tracer.SpanInScope spanInScope;

    @Mock
    private TraceContext traceContext;

    private SpringTracingAdapter adapter;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
        when(tracer.spanBuilder()).thenReturn(builder);
        when(builder.name(anyString())).thenReturn(builder);
        when(builder.tag(anyString(), anyString())).thenReturn(builder);
        when(builder.event(anyString())).thenReturn(builder);
        when(builder.start()).thenReturn(span);

        adapter = new SpringTracingAdapter(tracer);
    }

    @Test
    void getSupportedFrameworkIsSpringBoot() {
        assertThat(adapter.getSupportedFramework()).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    void getServiceReturnsSelf() {
        assertThat(adapter.getService()).isSameAs(adapter);
    }

    @Test
    void executeInSpanSupplierReturnsResult() {
        String result = adapter.executeInSpan("op", () -> "value");

        assertThat(result).isEqualTo("value");
        verify(span).name("op");
        verify(span).start();
        verify(span).end();
    }

    @Test
    void executeInSpanRunnableRuns() {
        StringBuilder sink = new StringBuilder();
        // Block body keeps this bound to the Runnable overload (an expression body
        // returning a value would also match the Supplier overload).
        adapter.executeInSpan("op", () -> { sink.append("ran"); });

        assertThat(sink.toString()).isEqualTo("ran");
        verify(span).start();
        verify(span).end();
    }

    @Test
    void getCurrentTraceIdAndSpanIdWithActiveSpan() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-1");
        when(traceContext.spanId()).thenReturn("span-1");

        assertThat(adapter.getCurrentTraceId()).isEqualTo("trace-1");
        assertThat(adapter.getCurrentSpanId()).isEqualTo("span-1");
    }

    @Test
    void getCurrentTraceIdAndSpanIdWithoutActiveSpan() {
        when(tracer.currentSpan()).thenReturn(null);

        assertThat(adapter.getCurrentTraceId()).isNull();
        assertThat(adapter.getCurrentSpanId()).isNull();
    }

    @Test
    void addTagAndEventWithActiveSpan() {
        when(tracer.currentSpan()).thenReturn(span);

        adapter.addTag("k", "v");
        adapter.addEvent("e");

        verify(span).tag("k", "v");
        verify(span).event("e");
    }

    @Test
    void addTagAndEventWithoutActiveSpanAreNoOps() {
        when(tracer.currentSpan()).thenReturn(null);

        adapter.addTag("k", "v");
        adapter.addEvent("e");

        verify(span, never()).tag(anyString(), anyString());
        verify(span, never()).event(anyString());
    }

    @Test
    void spanBuilderBuildsTagsEventsAndSpan() {
        TracingService.SpanBuilder spanBuilder = adapter.spanBuilder("my-span");

        TracingService.SpanBuilder chained = spanBuilder.tag("k", "v").event("ev");
        assertThat(chained).isSameAs(spanBuilder);

        TracingService.Span apiSpan = spanBuilder.start();
        assertThat(apiSpan).isNotNull();

        verify(builder).name("my-span");
        verify(builder).tag("k", "v");
        verify(builder).event("ev");
        verify(builder).start();
    }

    @Test
    void apiSpanDelegatesToMicrometerSpan() {
        TracingService.Span apiSpan = adapter.spanBuilder("s").start();

        apiSpan.setTag("k", "v");
        apiSpan.addEvent("ev");
        RuntimeException ex = new RuntimeException("err");
        apiSpan.recordException(ex);
        // close() delegates to end() via the default interface method
        apiSpan.close();
        apiSpan.end();

        verify(span).tag("k", "v");
        verify(span).event("ev");
        verify(span).error(ex);
        verify(span, org.mockito.Mockito.times(2)).end();
    }
}
