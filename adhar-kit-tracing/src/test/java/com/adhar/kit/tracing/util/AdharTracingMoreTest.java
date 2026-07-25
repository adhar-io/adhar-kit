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
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private Tracer.SpanInScope spanInScope;

    private AdharTracing adharTracing;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(spanInScope);

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
    //
    // wrapWithTraceContext captures the current span at wrap-time and re-attaches it (via
    // tracer.withSpan(...)) for the duration of every invocation of the wrapped
    // function/consumer/runnable/supplier/callable, closing the scope afterward. We verify
    // both that the delegate is invoked and that tracer.withSpan(capturedSpan) was actually
    // used to re-attach the context.

    @Test
    void wrapFunctionWithActiveSpanInvokesDelegateAndReattachesContext() {
        Function<Integer, Integer> wrapped = adharTracing.wrapWithTraceContext((Function<Integer, Integer>) i -> i + 1);
        assertThat(wrapped.apply(1)).isEqualTo(2);

        verify(tracer).withSpan(span);
    }

    @Test
    void wrapFunctionWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Function<Integer, Integer> original = i -> i + 1;

        Function<Integer, Integer> wrapped = adharTracing.wrapWithTraceContext(original);

        assertThat(wrapped).isSameAs(original);
    }

    @Test
    void wrapConsumerWithActiveSpanInvokesDelegateAndReattachesContext() {
        AtomicBoolean called = new AtomicBoolean(false);
        Consumer<String> wrapped = adharTracing.wrapWithTraceContext((Consumer<String>) s -> called.set(true));
        wrapped.accept("x");

        assertThat(called).isTrue();
        verify(tracer).withSpan(span);
    }

    @Test
    void wrapConsumerWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Consumer<String> original = s -> {};

        assertThat(adharTracing.wrapWithTraceContext(original)).isSameAs(original);
    }

    @Test
    void wrapRunnableWithActiveSpanInvokesDelegateAndReattachesContext() {
        AtomicBoolean called = new AtomicBoolean(false);
        Runnable wrapped = adharTracing.wrapWithTraceContext((Runnable) () -> called.set(true));
        wrapped.run();

        assertThat(called).isTrue();
        verify(tracer).withSpan(span);
    }

    @Test
    void wrapRunnableWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Runnable original = () -> {};

        assertThat(adharTracing.wrapWithTraceContext(original)).isSameAs(original);
    }

    @Test
    void wrapSupplierWithActiveSpanInvokesDelegateAndReattachesContext() {
        Supplier<String> wrapped = adharTracing.wrapWithTraceContext((Supplier<String>) () -> "value");
        assertThat(wrapped.get()).isEqualTo("value");

        verify(tracer).withSpan(span);
    }

    @Test
    void wrapSupplierWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Supplier<String> original = () -> "value";

        assertThat(adharTracing.wrapWithTraceContext(original)).isSameAs(original);
    }

    @Test
    void wrapCallableWithActiveSpanInvokesDelegateAndReattachesContext() throws Exception {
        Callable<String> wrapped = adharTracing.wrapWithTraceContext((Callable<String>) () -> "called");
        assertThat(wrapped.call()).isEqualTo("called");

        verify(tracer).withSpan(span);
    }

    @Test
    void wrapCallableWithoutSpanReturnsOriginal() {
        when(tracer.currentSpan()).thenReturn(null);
        Callable<String> original = () -> "value";

        assertThat(adharTracing.wrapWithTraceContext(original)).isSameAs(original);
    }

    @Test
    void wrapCallableClosesScopeEvenWhenDelegateThrows() {
        RuntimeException boom = new RuntimeException("boom");
        Callable<String> wrapped = adharTracing.wrapWithTraceContext((Callable<String>) () -> { throw boom; });
        assertThatThrownBy(wrapped::call).isSameAs(boom);

        verify(tracer).withSpan(span);
        verify(spanInScope).close();
    }

    // ========== Baggage error / edge paths (real Tracer baggage API) ==========
    //
    // These use the mocked Tracer to exercise error handling / validation branches that are
    // hard to trigger against a real Tracer implementation. Happy-path set/get/propagation
    // coverage against a real (non-mock) Tracer lives in AdharTracingBaggageTest.

    @Test
    void setBaggageWithNullKeyIsNoOp() {
        adharTracing.setBaggage(null, "v");

        verify(tracer, never()).createBaggageInScope(anyString(), anyString());
    }

    @Test
    void setBaggageWithNullValueIsNoOp() {
        adharTracing.setBaggage("k", null);

        verify(tracer, never()).createBaggageInScope(anyString(), anyString());
    }

    @Test
    void setBaggageWhenDisabledIsNoOp() {
        com.adhar.kit.tracing.properties.AdharTracingProperties.BaggageProperties disabled =
                new com.adhar.kit.tracing.properties.AdharTracingProperties.BaggageProperties();
        disabled.setEnabled(false);
        AdharTracing disabledTracing = new AdharTracing(tracer, disabled);

        disabledTracing.setBaggage("k", "v");

        verify(tracer, never()).createBaggageInScope(anyString(), anyString());
    }

    @Test
    void setBaggageSwallowsTracerException() {
        when(tracer.createBaggageInScope(anyString(), anyString())).thenThrow(new RuntimeException("boom"));

        // Must not throw; the exception is caught and logged.
        adharTracing.setBaggage("k", "v");
    }

    @Test
    void getBaggageWithNullKeyReturnsNullWithoutCallingTracer() {
        assertThat(adharTracing.getBaggage(null)).isNull();

        verify(tracer, never()).getBaggage(anyString());
    }

    @Test
    void getBaggageSwallowsTracerException() {
        when(tracer.getBaggage(anyString())).thenThrow(new RuntimeException("boom"));

        assertThat(adharTracing.getBaggage("k")).isNull();
    }

    @Test
    void getAllBaggageSwallowsTracerExceptionReturningEmptyMap() {
        when(tracer.getAllBaggage()).thenThrow(new RuntimeException("boom"));

        assertThat(adharTracing.getAllBaggage()).isEmpty();
    }

    @Test
    void removeBaggageWithUnknownKeyIsNoOp() {
        // Never set via this instance, so there is no tracked scope to close; must not throw.
        adharTracing.removeBaggage("never-set");
    }

    @Test
    void copyBaggageToSpanSwallowsSpanTagException() {
        when(tracer.getAllBaggage()).thenReturn(Map.of("k", "v"));
        Span throwingSpan = org.mockito.Mockito.mock(Span.class);
        when(throwingSpan.tag(anyString(), anyString())).thenThrow(new RuntimeException("copy boom"));

        // Exception is caught inside copyBaggageToSpan.
        adharTracing.copyBaggageToSpan(throwingSpan);

        verify(throwingSpan).tag("baggage.k", "v");
    }

    @Test
    void extractBaggageFromNullHeadersIsNoOp() {
        adharTracing.extractBaggageFromHeaders(null);

        verify(tracer, never()).createBaggageInScope(anyString(), anyString());
    }

    @Test
    void injectBaggageIntoNullHeadersIsNoOp() {
        when(tracer.getAllBaggage()).thenReturn(Map.of("k", "v"));

        // Must not throw.
        adharTracing.injectBaggageIntoHeaders(null);
    }

    @Test
    void injectBaggageWithEmptyBaggageDoesNotAddHeader() {
        when(tracer.getAllBaggage()).thenReturn(Map.of());
        Map<String, String> headers = new java.util.HashMap<>();

        adharTracing.injectBaggageIntoHeaders(headers);

        assertThat(headers).isEmpty();
    }
}
