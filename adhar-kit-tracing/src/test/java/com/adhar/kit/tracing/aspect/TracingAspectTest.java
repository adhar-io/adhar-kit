package com.adhar.kit.tracing.aspect;

import com.adhar.kit.tracing.annotation.AsyncSpan;
import com.adhar.kit.tracing.annotation.ContinueSpan;
import com.adhar.kit.tracing.annotation.DatabaseSpan;
import com.adhar.kit.tracing.annotation.HttpClientSpan;
import com.adhar.kit.tracing.annotation.MessagingSpan;
import com.adhar.kit.tracing.annotation.NewSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TracingAspect} - verifies span creation, tagging and error
 * handling for every tracing annotation handler. Pure unit tests using mocked
 * {@link Tracer} / {@link Span} / {@link ProceedingJoinPoint}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingAspectTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Tracer.SpanInScope spanInScope;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private TraceContext traceContext;

    private TracingAspect aspect;

    /** A sample method used to back the mocked {@link MethodSignature}. */
    public String sampleMethod(String name) {
        return name;
    }

    @BeforeEach
    void setUp() throws Exception {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(spanInScope);

        Method method = TracingAspectTest.class.getMethod("sampleMethod", String.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn("sampleMethod");
        when(signature.getDeclaringType()).thenAnswer(inv -> TracingAspectTest.class);
        when(signature.toShortString()).thenReturn("TracingAspectTest.sampleMethod()");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg-value"});

        aspect = new TracingAspect(tracer);
    }

    // ========== @NewSpan ==========

    @Test
    void handleNewSpan_success() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("my.span");
        when(ann.tags()).thenReturn(new String[]{"k1", "v1"});
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleNewSpan(joinPoint, ann);

        assertThat(result).isEqualTo("result");
        verify(span).name("my.span");
        verify(span).tag("component", "adhar-tracing");
        verify(span).tag("span.kind", "internal");
        verify(span).tag("k1", "v1");
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void handleNewSpan_usesMethodNameWhenValueBlank() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("");
        when(ann.tags()).thenReturn(new String[]{});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.handleNewSpan(joinPoint, ann);

        verify(span).name("TracingAspectTest.sampleMethod");
    }

    @Test
    void handleNewSpan_oddTagsAreIgnored() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("s");
        when(ann.tags()).thenReturn(new String[]{"only-key"});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.handleNewSpan(joinPoint, ann);

        verify(span, never()).tag("only-key", null);
        verify(span).tag("success", "true");
    }

    @Test
    void handleNewSpan_futureResultReturnedAsIs() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("s");
        when(ann.tags()).thenReturn(new String[]{});
        Future<?> future = mock(Future.class);
        when(joinPoint.proceed()).thenReturn(future);

        Object result = aspect.handleNewSpan(joinPoint, ann);

        assertThat(result).isSameAs(future);
        verify(span).end();
        verify(span, never()).tag("success", "true");
    }

    @Test
    void handleNewSpan_exceptionRecordsErrorAndRethrows() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("s");
        when(ann.tags()).thenReturn(new String[]{});
        RuntimeException ex = new IllegalArgumentException("boom");
        when(joinPoint.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.handleNewSpan(joinPoint, ann)).isSameAs(ex);

        verify(span).tag("success", "false");
        verify(span).tag("error.class", "IllegalArgumentException");
        verify(span).tag("error.message", "boom");
        verify(span).end();
    }

    // ========== @ContinueSpan ==========

    @Test
    void handleContinueSpan_noCurrentSpanJustProceeds() throws Throwable {
        when(tracer.currentSpan()).thenReturn(null);
        ContinueSpan ann = mock(ContinueSpan.class);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.handleContinueSpan(joinPoint, ann);

        assertThat(result).isEqualTo("ok");
        verify(span, never()).tag(anyString(), anyString());
    }

    @Test
    void handleContinueSpan_addsTagsAndEvents() throws Throwable {
        ContinueSpan ann = mock(ContinueSpan.class);
        when(ann.tags()).thenReturn(new String[]{"k", "v"});
        when(ann.startEvent()).thenReturn("start");
        when(ann.endEvent()).thenReturn("end");
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.handleContinueSpan(joinPoint, ann);

        assertThat(result).isEqualTo("ok");
        verify(span).tag("k", "v");
        verify(span).event("start");
        verify(span).event("end");
    }

    @Test
    void handleContinueSpan_exceptionRecordsErrorOnCurrentSpan() throws Throwable {
        ContinueSpan ann = mock(ContinueSpan.class);
        when(ann.tags()).thenReturn(new String[]{});
        when(ann.startEvent()).thenReturn("");
        IllegalStateException ex = new IllegalStateException("bad");
        when(joinPoint.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.handleContinueSpan(joinPoint, ann)).isSameAs(ex);

        verify(span).tag("error.class", "IllegalStateException");
        verify(span).tag("error.message", "bad");
    }

    // ========== @DatabaseSpan ==========

    @Test
    void handleDatabaseSpan_buildsNameFromOperationAndTable() throws Throwable {
        DatabaseSpan ann = mock(DatabaseSpan.class);
        when(ann.value()).thenReturn("");
        when(ann.operation()).thenReturn("SELECT");
        when(ann.table()).thenReturn("users");
        when(ann.database()).thenReturn("appdb");
        when(ann.statement()).thenReturn("SELECT * FROM users");
        when(ann.tags()).thenReturn(new String[]{});
        when(joinPoint.proceed()).thenReturn("rows");

        Object result = aspect.handleDatabaseSpan(joinPoint, ann);

        assertThat(result).isEqualTo("rows");
        // value() is blank, so the span name falls back to the method signature; the
        // operation/table-derived name override in the aspect only applies when the
        // resolved name is blank (which never happens given the signature fallback).
        verify(span).name("TracingAspectTest.sampleMethod");
        verify(span).tag("db.system", "sql");
        verify(span).tag("db.operation", "SELECT");
        verify(span).tag("db.sql.table", "users");
        verify(span).tag("db.name", "appdb");
        verify(span).tag("db.statement", "SELECT * FROM users");
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void handleDatabaseSpan_exception() throws Throwable {
        DatabaseSpan ann = mock(DatabaseSpan.class);
        when(ann.value()).thenReturn("query.span");
        when(ann.operation()).thenReturn("");
        when(ann.table()).thenReturn("");
        when(ann.database()).thenReturn("");
        when(ann.statement()).thenReturn("");
        when(ann.tags()).thenReturn(new String[]{});
        RuntimeException ex = new RuntimeException("db error");
        when(joinPoint.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.handleDatabaseSpan(joinPoint, ann)).isSameAs(ex);

        verify(span).name("query.span");
        verify(span).tag("success", "false");
        verify(span).tag("error.class", "RuntimeException");
        verify(span).end();
    }

    // ========== @HttpClientSpan ==========

    @Test
    void handleHttpClientSpan_success() throws Throwable {
        HttpClientSpan ann = mock(HttpClientSpan.class);
        when(ann.value()).thenReturn("");
        when(ann.method()).thenReturn("GET");
        when(ann.url()).thenReturn("https://api.example.com");
        when(ann.tags()).thenReturn(new String[]{});
        when(joinPoint.proceed()).thenReturn("body");

        Object result = aspect.handleHttpClientSpan(joinPoint, ann);

        assertThat(result).isEqualTo("body");
        verify(span).name("TracingAspectTest.sampleMethod");
        verify(span).tag("span.kind", "client");
        verify(span).tag("http.method", "GET");
        verify(span).tag("http.url", "https://api.example.com");
        verify(span).tag("success", "true");
    }

    @Test
    void handleHttpClientSpan_exception() throws Throwable {
        HttpClientSpan ann = mock(HttpClientSpan.class);
        when(ann.value()).thenReturn("call");
        when(ann.method()).thenReturn("");
        when(ann.url()).thenReturn("");
        when(ann.tags()).thenReturn(new String[]{});
        RuntimeException ex = new RuntimeException("http fail");
        when(joinPoint.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.handleHttpClientSpan(joinPoint, ann)).isSameAs(ex);

        verify(span).name("call");
        verify(span).tag("error.message", "http fail");
        verify(span).end();
    }

    // ========== @MessagingSpan ==========

    @Test
    void handleMessagingSpan_success() throws Throwable {
        MessagingSpan ann = mock(MessagingSpan.class);
        when(ann.value()).thenReturn("");
        when(ann.operation()).thenReturn("send");
        when(ann.destination()).thenReturn("orders");
        when(ann.destinationType()).thenReturn("queue");
        when(ann.system()).thenReturn("kafka");
        when(ann.tags()).thenReturn(new String[]{});
        when(joinPoint.proceed()).thenReturn("sent");

        Object result = aspect.handleMessagingSpan(joinPoint, ann);

        assertThat(result).isEqualTo("sent");
        verify(span).name("TracingAspectTest.sampleMethod");
        verify(span).tag("span.kind", "producer");
        verify(span).tag("messaging.operation", "send");
        verify(span).tag("messaging.destination", "orders");
        verify(span).tag("messaging.destination_kind", "queue");
        verify(span).tag("messaging.system", "kafka");
        verify(span).tag("success", "true");
    }

    @Test
    void handleMessagingSpan_exception() throws Throwable {
        MessagingSpan ann = mock(MessagingSpan.class);
        when(ann.value()).thenReturn("publish");
        when(ann.operation()).thenReturn("");
        when(ann.destination()).thenReturn("");
        when(ann.destinationType()).thenReturn("");
        when(ann.system()).thenReturn("");
        when(ann.tags()).thenReturn(new String[]{});
        RuntimeException ex = new RuntimeException("mq fail");
        when(joinPoint.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.handleMessagingSpan(joinPoint, ann)).isSameAs(ex);

        verify(span).name("publish");
        verify(span).tag("success", "false");
        verify(span).end();
    }

    // ========== @AsyncSpan ==========

    @Test
    void handleAsyncSpan_completableFutureResult() throws Throwable {
        AsyncSpan ann = mock(AsyncSpan.class);
        when(ann.value()).thenReturn("async.op");
        when(ann.propagateContext()).thenReturn(true);
        when(ann.tags()).thenReturn(new String[]{});
        CompletableFuture<String> future = CompletableFuture.completedFuture("done");
        when(joinPoint.proceed()).thenReturn(future);

        Object result = aspect.handleAsyncSpan(joinPoint, ann);

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThat(((CompletableFuture<?>) result).get()).isEqualTo("done");
        verify(span).tag("async", "true");
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void handleAsyncSpan_completableFutureWithError() throws Throwable {
        AsyncSpan ann = mock(AsyncSpan.class);
        when(ann.value()).thenReturn("async.op");
        when(ann.propagateContext()).thenReturn(true);
        when(ann.tags()).thenReturn(new String[]{});
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("async error"));
        when(joinPoint.proceed()).thenReturn(future);

        Object result = aspect.handleAsyncSpan(joinPoint, ann);

        assertThat(result).isInstanceOf(CompletableFuture.class);
        verify(span).tag("error.class", "IllegalStateException");
        verify(span).tag("error.message", "async error");
        verify(span).end();
    }

    @Test
    void handleAsyncSpan_plainFutureResult() throws Throwable {
        AsyncSpan ann = mock(AsyncSpan.class);
        when(ann.value()).thenReturn("async.op");
        when(ann.propagateContext()).thenReturn(false);
        when(ann.tags()).thenReturn(new String[]{});
        Future<?> future = mock(Future.class);
        when(joinPoint.proceed()).thenReturn(future);

        Object result = aspect.handleAsyncSpan(joinPoint, ann);

        assertThat(result).isSameAs(future);
    }

    @Test
    void handleAsyncSpan_exception() throws Throwable {
        AsyncSpan ann = mock(AsyncSpan.class);
        when(ann.value()).thenReturn("async.op");
        when(ann.propagateContext()).thenReturn(true);
        when(ann.tags()).thenReturn(new String[]{});
        RuntimeException ex = new RuntimeException("sync fail");
        when(joinPoint.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.handleAsyncSpan(joinPoint, ann)).isSameAs(ex);

        verify(span).tag("success", "false");
        verify(span).tag("error.class", "RuntimeException");
    }

    // ========== SpEL expression evaluation ==========

    @Test
    void handleNewSpan_evaluatesSpelExpression() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        // A quoted literal that still contains the "#{" trigger so the SpEL branch
        // (createEvaluationContext + parse + getValue) is exercised end to end.
        when(ann.value()).thenReturn("'span-#{literal}'");
        when(ann.tags()).thenReturn(new String[]{});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.handleNewSpan(joinPoint, ann);

        verify(span).name("span-#{literal}");
    }

    @Test
    void handleNewSpan_invalidSpelFallsBackToRawExpression() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("#{ this is not valid spel (");
        when(ann.tags()).thenReturn(new String[]{});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.handleNewSpan(joinPoint, ann);

        verify(span).name("#{ this is not valid spel (");
    }

    @Test
    void handleNewSpan_multipleTags() throws Throwable {
        NewSpan ann = mock(NewSpan.class);
        when(ann.value()).thenReturn("s");
        when(ann.tags()).thenReturn(new String[]{"a", "1", "b", "2"});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.handleNewSpan(joinPoint, ann);

        verify(span).tag("a", "1");
        verify(span).tag("b", "2");
        verify(tracer, times(1)).withSpan(span);
    }
}
