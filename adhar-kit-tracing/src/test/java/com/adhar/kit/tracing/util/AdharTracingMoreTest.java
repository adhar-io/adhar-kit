package com.adhar.kit.tracing.util;

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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Supplemental tests for {@link AdharTracing} covering the overloads and branches
 * not exercised by {@link AdharTracingTest} (callables, runnable-with-tags, async
 * error paths, current trace/span id, context wrappers and no-span branches).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdharTracingMoreTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    private AdharTracing adharTracing;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.currentSpan()).thenReturn(span);

        adharTracing = new AdharTracing(tracer);
    }

    // ========== Runnable with tags ==========

    @Test
    void withinSpanRunnableWithTags() {
        AtomicBoolean ran = new AtomicBoolean(false);
        adharTracing.withinSpan("op", Map.of("k", "v"), (Runnable) () -> ran.set(true));

        assertThat(ran).isTrue();
        verify(span).tag("k", "v");
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void withinSpanRunnableException() {
        RuntimeException ex = new RuntimeException("run-fail");
        Runnable op = () -> { throw ex; };

        assertThatThrownBy(() -> adharTracing.withinSpan("op", op)).isSameAs(ex);

        verify(span).tag("success", "false");
        verify(span).tag("error.class", "RuntimeException");
        verify(span).tag("error.message", "run-fail");
        verify(span).end();
    }

    // ========== Callable ==========

    @Test
    void withinSpanCallableSuccess() throws Exception {
        String result = adharTracing.withinSpanCallable("op", () -> "called");

        assertThat(result).isEqualTo("called");
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void withinSpanCallableWithTags() throws Exception {
        String result = adharTracing.withinSpanCallable("op", Map.of("k", "v"), () -> "called");

        assertThat(result).isEqualTo("called");
        verify(span).tag("k", "v");
        verify(span).tag("success", "true");
    }

    @Test
    void withinSpanCallableCheckedException() {
        Exception checked = new Exception("checked");
        Callable<String> callable = () -> { throw checked; };

        assertThatThrownBy(() -> adharTracing.withinSpanCallable("op", callable)).isSameAs(checked);

        verify(span).tag("success", "false");
        verify(span).tag("error.class", "Exception");
        verify(span).tag("error.message", "checked");
        verify(span).end();
    }

    // ========== Async ==========

    @Test
    void withinSpanAsyncWithTagsSuccess() throws Exception {
        Supplier<CompletableFuture<String>> op = () -> CompletableFuture.completedFuture("done");

        CompletableFuture<String> result = adharTracing.withinSpanAsync("op", Map.of("k", "v"), op);

        assertThat(result.get()).isEqualTo("done");
        verify(span).tag("async", "true");
        verify(span).tag("k", "v");
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void withinSpanAsyncCompletesExceptionally() {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("async-fail"));
        Supplier<CompletableFuture<String>> op = () -> failed;

        CompletableFuture<String> result = adharTracing.withinSpanAsync("op", op);

        assertThat(result).isCompletedExceptionally();
        verify(span).tag("success", "false");
        verify(span).tag("error.class", "IllegalStateException");
        verify(span).tag("error.message", "async-fail");
        verify(span).end();
    }

    @Test
    void withinSpanAsyncSupplierThrows() {
        Supplier<CompletableFuture<String>> op = () -> { throw new RuntimeException("supplier-fail"); };

        assertThatThrownBy(() -> adharTracing.withinSpanAsync("op", op))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("supplier-fail");

        verify(span).tag("success", "false");
        verify(span).end();
    }

    // ========== Current trace / span ids ==========

    @Test
    void getCurrentTraceIdAndSpanId() {
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("t-1");
        when(traceContext.spanId()).thenReturn("s-1");

        assertThat(adharTracing.getCurrentTraceId()).isEqualTo("t-1");
        assertThat(adharTracing.getCurrentSpanId()).isEqualTo("s-1");
    }

    @Test
    void getCurrentTraceIdAndSpanIdWithoutSpan() {
        when(tracer.currentSpan()).thenReturn(null);

        assertThat(adharTracing.getCurrentTraceId()).isNull();
        assertThat(adharTracing.getCurrentSpanId()).isNull();
    }

    // ========== No-span branches ==========

    @Test
    void addEventWithoutSpanIsNoOp() {
        when(tracer.currentSpan()).thenReturn(null);

        adharTracing.addEvent("e");
        adharTracing.addEvent("e", Map.of("a", "b"));

        verify(span, never()).event(anyString());
    }

    @Test
    void recordExceptionWithoutSpanIsNoOp() {
        when(tracer.currentSpan()).thenReturn(null);

        adharTracing.recordException(new RuntimeException("x"));

        verify(span, never()).tag(anyString(), anyString());
    }

    @Test
    void createSpanWithNullTags() {
        Span result = adharTracing.createSpan("s", null);

        assertThat(result).isSameAs(span);
        verify(span).name("s");
    }

    // ========== Context wrappers ==========

    @Test
    void wrapFunctionWithActiveSpanInvokesDelegate() {
        Function<Integer, Integer> wrapped = adharTracing.wrapWithTraceContext((Function<Integer, Integer>) i -> i + 1);

        assertThat(wrapped.apply(1)).isEqualTo(2);
    }

    @Test
    void wrapFunctionWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Function<Integer, Integer> original = i -> i + 1;

        Function<Integer, Integer> wrapped = adharTracing.wrapWithTraceContext(original);

        assertThat(wrapped).isSameAs(original);
    }

    @Test
    void wrapConsumerWithActiveSpanInvokesDelegate() {
        AtomicBoolean called = new AtomicBoolean(false);
        Consumer<String> wrapped = adharTracing.wrapWithTraceContext((Consumer<String>) s -> called.set(true));

        wrapped.accept("x");
        assertThat(called).isTrue();
    }

    @Test
    void wrapConsumerWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Consumer<String> original = s -> {};

        assertThat(adharTracing.wrapWithTraceContext(original)).isSameAs(original);
    }

    @Test
    void wrapRunnableWithActiveSpanInvokesDelegate() {
        AtomicBoolean called = new AtomicBoolean(false);
        Runnable wrapped = adharTracing.wrapWithTraceContext((Runnable) () -> called.set(true));

        wrapped.run();
        assertThat(called).isTrue();
    }

    @Test
    void wrapRunnableWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Runnable original = () -> {};

        assertThat(adharTracing.wrapWithTraceContext(original)).isSameAs(original);
    }

    // ========== Baggage error / edge paths ==========

    @Test
    void setBaggageSwallowsSpanTagException() {
        Span throwingSpan = org.mockito.Mockito.mock(Span.class);
        when(throwingSpan.tag(anyString(), anyString())).thenThrow(new RuntimeException("tag boom"));
        when(tracer.currentSpan()).thenReturn(throwingSpan);

        // Exception from span.tag is caught and logged, not propagated.
        adharTracing.setBaggage("k", "v");

        // localBaggage is only populated after the (failed) tag call, so it stays empty.
        assertThat(adharTracing.getBaggage("k")).isNull();
    }

    @Test
    void getBaggageWithNullKeyReturnsNull() {
        // ConcurrentHashMap.get(null) throws NPE which is caught -> null returned.
        assertThat(adharTracing.getBaggage(null)).isNull();
    }

    @Test
    void removeBaggageWithNullKeySwallowsException() {
        // ConcurrentHashMap.remove(null) throws NPE which is caught.
        adharTracing.removeBaggage(null);
        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void copyBaggageToSpanSwallowsException() {
        adharTracing.setBaggage("k", "v");
        Span throwingSpan = org.mockito.Mockito.mock(Span.class);
        when(throwingSpan.tag(anyString(), anyString())).thenThrow(new RuntimeException("copy boom"));

        // Exception is caught inside copyBaggageToSpan.
        adharTracing.copyBaggageToSpan(throwingSpan);

        verify(throwingSpan).tag("baggage.k", "v");
    }

    @Test
    void extractBaggageFromNullHeadersSwallowsException() {
        // headers.entrySet() on null throws NPE which is caught.
        adharTracing.extractBaggageFromHeaders(null);
        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void injectBaggageIntoImmutableHeadersSwallowsException() {
        adharTracing.setBaggage("k", "v");
        // Map.of() is immutable; put() throws UnsupportedOperationException which is caught.
        adharTracing.injectBaggageIntoHeaders(Map.of());
        assertThat(adharTracing.getBaggage("k")).isEqualTo("v");
    }
}
