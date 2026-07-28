package com.adhar.kit.tracing.sampling;

import com.adhar.kit.tracing.properties.AdharTracingProperties;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TailSamplingSpanProcessor}. Decisions are made synchronously by configuring
 * {@code holdWindowMs = 0} and {@code traceTimeoutMs = 0}, so no background scheduler is needed
 * (a mock scheduler is injected and never invoked on these paths).
 */
class TailSamplingSpanProcessorTest {

    /** Collecting delegate that records every forwarded span. */
    private static final class CollectingProcessor implements SpanProcessor {
        final List<ReadableSpan> ended = new CopyOnWriteArrayList<>();
        boolean shutdownCalled = false;
        boolean forceFlushCalled = false;

        @Override public void onStart(Context parentContext, ReadWriteSpan span) { }
        @Override public boolean isStartRequired() { return false; }
        @Override public void onEnd(ReadableSpan span) { ended.add(span); }
        @Override public boolean isEndRequired() { return true; }
        @Override public CompletableResultCode shutdown() { shutdownCalled = true; return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode forceFlush() { forceFlushCalled = true; return CompletableResultCode.ofSuccess(); }
    }

    private static final String TRACE_A = "0af7651916cd43dd8448eb211c80319a";
    private static final String TRACE_B = "0af7651916cd43dd8448eb211c80319b";

    private static SpanContext ctx(String traceId, String spanId) {
        return SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static SpanContext remoteCtx(String traceId, String spanId) {
        return SpanContext.createFromRemoteParent(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static ReadableSpan span(String traceId, String spanId, SpanContext parent, long latencyNanos, boolean error) {
        ReadableSpan span = mock(ReadableSpan.class);
        when(span.getSpanContext()).thenReturn(ctx(traceId, spanId));
        when(span.getParentSpanContext()).thenReturn(parent);
        when(span.getLatencyNanos()).thenReturn(latencyNanos);
        SpanData data = mock(SpanData.class);
        when(data.getStatus()).thenReturn(error ? StatusData.error() : StatusData.ok());
        when(span.toSpanData()).thenReturn(data);
        return span;
    }

    private static ReadableSpan root(String traceId, long latencyNanos, boolean error) {
        return span(traceId, "0000000000000001", SpanContext.getInvalid(), latencyNanos, error);
    }

    private static ReadableSpan child(String traceId, String spanId, boolean error) {
        return span(traceId, spanId, ctx(traceId, "0000000000000001"), 1000, error);
    }

    private AdharTracingProperties.TailSamplingProperties props(double keepRate) {
        AdharTracingProperties.TailSamplingProperties p = new AdharTracingProperties.TailSamplingProperties();
        p.setHoldWindowMs(0);
        p.setTraceTimeoutMs(0);
        p.setLatencyThresholdMs(1000);
        p.setKeepRate(keepRate);
        p.setMaxBufferedTraces(1000);
        return p;
    }

    private TailSamplingSpanProcessor processor(SpanProcessor delegate, AdharTracingProperties.TailSamplingProperties p, double rng) {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        return new TailSamplingSpanProcessor(delegate, p, () -> rng, scheduler);
    }

    @Test
    void keepsTraceWithErrorEvenWhenKeepRateZero() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.0), 0.99);

        ReadableSpan childSpan = child(TRACE_A, "0000000000000002", true);
        ReadableSpan rootSpan = root(TRACE_A, 10, false);
        p.onEnd(childSpan);
        p.onEnd(rootSpan); // root end triggers the (synchronous) decision

        assertThat(delegate.ended).containsExactlyInAnyOrder(childSpan, rootSpan);
    }

    @Test
    void keepsTraceExceedingLatencyThreshold() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.0), 0.99);

        long slow = TimeUnit.MILLISECONDS.toNanos(1500); // > 1000ms threshold
        ReadableSpan rootSpan = root(TRACE_A, slow, false);
        p.onEnd(rootSpan);

        assertThat(delegate.ended).containsExactly(rootSpan);
    }

    @Test
    void dropsFastErrorFreeTraceWhenKeepRateZero() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.0), 0.99);

        ReadableSpan rootSpan = root(TRACE_A, 10, false);
        p.onEnd(rootSpan);

        assertThat(delegate.ended).isEmpty();
    }

    @Test
    void keepsByRateWhenRngBelowKeepRate() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.5), 0.1); // 0.1 < 0.5 -> keep

        ReadableSpan rootSpan = root(TRACE_A, 10, false);
        p.onEnd(rootSpan);

        assertThat(delegate.ended).containsExactly(rootSpan);
    }

    @Test
    void dropsByRateWhenRngAboveKeepRate() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.5), 0.9); // 0.9 >= 0.5 -> drop

        ReadableSpan rootSpan = root(TRACE_A, 10, false);
        p.onEnd(rootSpan);

        assertThat(delegate.ended).isEmpty();
    }

    @Test
    void keepsRemoteRootTraceExceedingLatency() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.0), 0.99);

        long slow = TimeUnit.MILLISECONDS.toNanos(2000);
        // Server span joining a remote trace: parent is remote -> treated as root.
        ReadableSpan serverSpan = span(TRACE_A, "0000000000000009",
                remoteCtx(TRACE_A, "00f067aa0ba902b7"), slow, false);
        p.onEnd(serverSpan);

        assertThat(delegate.ended).containsExactly(serverSpan);
    }

    @Test
    void evictsOldestPendingTraceWhenBufferBoundExceeded() {
        CollectingProcessor delegate = new CollectingProcessor();
        AdharTracingProperties.TailSamplingProperties p = props(1.0); // keep everything
        p.setMaxBufferedTraces(1);
        TailSamplingSpanProcessor proc = processor(delegate, p, 0.99);

        // Two traces with only (non-root) children -> both stay pending until eviction.
        ReadableSpan a = child(TRACE_A, "0000000000000002", false);
        ReadableSpan b = child(TRACE_B, "0000000000000003", false);
        proc.onEnd(a); // pending: {A}
        proc.onEnd(b); // pending size 2 > 1 -> evict oldest (A), decided keep -> forwarded

        assertThat(delegate.ended).contains(a);
    }

    @Test
    void forwardsLateStragglerConsistentlyForKeptTrace() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.0), 0.99);

        ReadableSpan rootSpan = root(TRACE_A, 10, true); // kept (error)
        p.onEnd(rootSpan);
        assertThat(delegate.ended).containsExactly(rootSpan);

        // A span arriving after the decision is forwarded because the trace was kept.
        ReadableSpan late = child(TRACE_A, "00000000000000aa", false);
        p.onEnd(late);
        assertThat(delegate.ended).containsExactly(rootSpan, late);
    }

    @Test
    void dropsLateStragglerConsistentlyForDroppedTrace() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.0), 0.99);

        ReadableSpan rootSpan = root(TRACE_A, 10, false); // dropped
        p.onEnd(rootSpan);
        assertThat(delegate.ended).isEmpty();

        ReadableSpan late = child(TRACE_A, "00000000000000bb", false);
        p.onEnd(late);
        assertThat(delegate.ended).isEmpty();
    }

    @Test
    void shutdownFlushesPendingTracesAndDelegates() {
        CollectingProcessor delegate = new CollectingProcessor();
        AdharTracingProperties.TailSamplingProperties p = props(1.0); // keep on flush
        TailSamplingSpanProcessor proc = processor(delegate, p, 0.99);

        ReadableSpan pending = child(TRACE_A, "0000000000000002", false); // never gets a root
        proc.onEnd(pending);
        assertThat(delegate.ended).isEmpty();

        CompletableResultCode result = proc.shutdown();

        assertThat(result.isSuccess()).isTrue();
        assertThat(delegate.shutdownCalled).isTrue();
        assertThat(delegate.ended).containsExactly(pending);

        // After shutdown, further spans are ignored.
        proc.onEnd(root(TRACE_B, 10, true));
        assertThat(delegate.ended).containsExactly(pending);
    }

    @Test
    void forceFlushAndContractFlagsDelegate() {
        CollectingProcessor delegate = new CollectingProcessor();
        TailSamplingSpanProcessor p = processor(delegate, props(0.1), 0.99);

        assertThat(p.isEndRequired()).isTrue();
        assertThat(p.isStartRequired()).isFalse();
        p.onStart(Context.root(), null); // no-op
        assertThat(p.forceFlush().isSuccess()).isTrue();
        assertThat(delegate.forceFlushCalled).isTrue();
    }

    @Test
    void usesRealSchedulerConstructorForHoldWindow() throws Exception {
        // Exercises the public constructor (own scheduler) with a small hold window; the
        // decision fires asynchronously shortly after the root ends.
        CollectingProcessor delegate = new CollectingProcessor();
        AdharTracingProperties.TailSamplingProperties p = new AdharTracingProperties.TailSamplingProperties();
        p.setHoldWindowMs(20);
        p.setTraceTimeoutMs(0);
        p.setKeepRate(1.0);
        TailSamplingSpanProcessor proc = new TailSamplingSpanProcessor(delegate, p);

        ReadableSpan rootSpan = root(TRACE_A, 10, false);
        proc.onEnd(rootSpan);

        AtomicInteger polls = new AtomicInteger();
        while (delegate.ended.isEmpty() && polls.incrementAndGet() < 100) {
            Thread.sleep(10);
        }
        assertThat(delegate.ended).containsExactly(rootSpan);
        proc.shutdown();
    }
}
