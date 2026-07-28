package com.adhar.kit.tracing.sampling;

import com.adhar.kit.tracing.properties.AdharTracingProperties;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.DoubleSupplier;

/**
 * A tail-based sampling {@link SpanProcessor}: it buffers finished spans per trace and, once
 * the trace's root span ends (or a safety timeout elapses), decides whether to keep the whole
 * trace and, if kept, forwards every buffered span to a delegate processor (typically the
 * batch/export processor). Traces that are dropped are simply discarded.
 * <p>
 * A trace is kept when any of the following hold, honoring
 * {@link AdharTracingProperties.TailSamplingProperties}:
 * </p>
 * <ul>
 *     <li>any of its spans ended with an {@link StatusCode#ERROR error} status, or</li>
 *     <li>its root span's latency exceeds {@code latencyThresholdMs}, or</li>
 *     <li>otherwise, with probability {@code keepRate} (rate-based head fraction).</li>
 * </ul>
 * <p>
 * The buffer is bounded at {@code maxBufferedTraces}: when exceeded, the oldest still-pending
 * trace is decided immediately. A per-trace safety timeout ({@code traceTimeoutMs}) guarantees
 * a decision even if the root span never finishes. For this processor to see every span, the
 * SDK sampler must record all spans (the auto-configuration uses {@code Sampler.alwaysOn()}
 * when tail sampling is active).
 * </p>
 * <p>
 * <strong>SDK-hook note:</strong> the OpenTelemetry SDK does not offer a first-class tail
 * sampler; the faithful hook is a buffering {@link SpanProcessor} placed in front of the
 * exporting processor, which is what this class implements.
 * </p>
 */
@Slf4j
public class TailSamplingSpanProcessor implements SpanProcessor {

    private final SpanProcessor delegate;
    private final long holdWindowMs;
    private final long latencyThresholdNanos;
    private final int maxBufferedTraces;
    private final long traceTimeoutMs;
    private final double keepRate;
    private final DoubleSupplier rng;
    private final ScheduledExecutorService scheduler;
    private final boolean ownScheduler;

    private final ReentrantLock lock = new ReentrantLock();
    /** Pending traces awaiting a decision, in insertion order (oldest first) for eviction. */
    private final Map<String, TraceBuffer> pending = new LinkedHashMap<>();
    /** Bounded LRU of already-decided traces, so late-arriving spans are handled consistently. */
    private final Map<String, Boolean> decided;
    private volatile boolean shutdown = false;

    public TailSamplingSpanProcessor(SpanProcessor delegate, AdharTracingProperties.TailSamplingProperties props) {
        this(delegate, props, null, null);
    }

    /**
     * Full constructor allowing a deterministic random source and an externally-managed
     * scheduler to be injected (used by tests). When {@code scheduler} is {@code null} an
     * internal single-threaded daemon scheduler is created and owned by this processor.
     */
    TailSamplingSpanProcessor(SpanProcessor delegate,
                              AdharTracingProperties.TailSamplingProperties props,
                              DoubleSupplier rng,
                              ScheduledExecutorService scheduler) {
        this.delegate = delegate;
        this.holdWindowMs = Math.max(0, props.getHoldWindowMs());
        this.latencyThresholdNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, props.getLatencyThresholdMs()));
        this.maxBufferedTraces = Math.max(1, props.getMaxBufferedTraces());
        this.traceTimeoutMs = Math.max(0, props.getTraceTimeoutMs());
        this.keepRate = props.getKeepRate();
        this.rng = rng != null ? rng : () -> ThreadLocalRandom.current().nextDouble();
        int cap = this.maxBufferedTraces;
        this.decided = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > cap;
            }
        };
        if (scheduler != null) {
            this.scheduler = scheduler;
            this.ownScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "adhar-tail-sampling");
                t.setDaemon(true);
                return t;
            });
            this.ownScheduler = true;
        }
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // Nothing to do at start; buffering happens on end.
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (shutdown) {
            return;
        }
        String traceId = span.getSpanContext().getTraceId();
        String evictTraceId = null;
        lock.lock();
        try {
            Boolean priorDecision = decided.get(traceId);
            if (priorDecision != null) {
                // Trace already decided; forward or drop this straggler consistently.
                if (Boolean.TRUE.equals(priorDecision)) {
                    forward(span);
                }
                return;
            }

            TraceBuffer buffer = pending.get(traceId);
            boolean isNew = buffer == null;
            if (isNew) {
                buffer = new TraceBuffer();
                pending.put(traceId, buffer);
            }
            buffer.spans.add(span);
            if (isError(span)) {
                buffer.hasError = true;
            }
            if (isRoot(span)) {
                buffer.rootEnded = true;
                buffer.rootLatencyNanos = span.getLatencyNanos();
                scheduleDecision(traceId, buffer, holdWindowMs);
            } else if (isNew && traceTimeoutMs > 0) {
                // Safety net: ensure the trace is eventually decided even if the root never ends.
                scheduleDecision(traceId, buffer, traceTimeoutMs);
            }

            if (pending.size() > maxBufferedTraces) {
                Iterator<String> it = pending.keySet().iterator();
                if (it.hasNext()) {
                    evictTraceId = it.next();
                }
            }
        } finally {
            lock.unlock();
        }

        if (evictTraceId != null) {
            decide(evictTraceId);
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    private void scheduleDecision(String traceId, TraceBuffer buffer, long delayMs) {
        if (delayMs <= 0) {
            // Decide synchronously. The lock is reentrant, so decide() may re-acquire it here.
            decide(traceId);
            return;
        }
        ScheduledFuture<?> existing = buffer.decisionTask;
        if (existing != null) {
            existing.cancel(false);
        }
        try {
            buffer.decisionTask = scheduler.schedule(() -> decide(traceId), delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // Scheduler unavailable/rejected (e.g. during shutdown): decide inline as a fallback.
            log.debug("Scheduler unavailable, deciding inline for trace {}", traceId);
            decide(traceId);
        }
    }

    void decide(String traceId) {
        TraceBuffer buffer;
        boolean keep;
        lock.lock();
        try {
            buffer = pending.remove(traceId);
            if (buffer == null) {
                return; // already decided
            }
            if (buffer.decisionTask != null) {
                buffer.decisionTask.cancel(false);
            }
            keep = shouldKeep(buffer);
            decided.put(traceId, keep);
        } finally {
            lock.unlock();
        }

        if (keep) {
            for (ReadableSpan span : buffer.spans) {
                forward(span);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Tail sampling decided trace {}: keep={}, spans={}, error={}, rootLatencyNanos={}",
                    traceId, keep, buffer.spans.size(), buffer.hasError, buffer.rootLatencyNanos);
        }
    }

    private boolean shouldKeep(TraceBuffer buffer) {
        if (buffer.hasError) {
            return true;
        }
        if (buffer.rootEnded && buffer.rootLatencyNanos > latencyThresholdNanos) {
            return true;
        }
        if (keepRate >= 1.0) {
            return true;
        }
        if (keepRate <= 0.0) {
            return false;
        }
        return rng.getAsDouble() < keepRate;
    }

    private void forward(ReadableSpan span) {
        try {
            delegate.onEnd(span);
        } catch (RuntimeException e) {
            log.warn("Tail sampling failed to forward span to delegate", e);
        }
    }

    private static boolean isRoot(ReadableSpan span) {
        var parent = span.getParentSpanContext();
        // A local root (no parent) or the entry span of a remote trace both count as "root"
        // for the purpose of latency/decision timing.
        return !parent.isValid() || parent.isRemote();
    }

    private static boolean isError(ReadableSpan span) {
        return span.toSpanData().getStatus().getStatusCode() == StatusCode.ERROR;
    }

    @Override
    public CompletableResultCode shutdown() {
        shutdown = true;
        List<String> remaining;
        lock.lock();
        try {
            remaining = new ArrayList<>(pending.keySet());
        } finally {
            lock.unlock();
        }
        // Flush every still-pending trace through the normal decision path before shutting down.
        for (String traceId : remaining) {
            decide(traceId);
        }
        if (ownScheduler) {
            scheduler.shutdown();
        }
        return delegate.shutdown();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return delegate.forceFlush();
    }

    /** Mutable per-trace buffer, guarded by {@link #lock}. */
    private static final class TraceBuffer {
        final List<ReadableSpan> spans = new ArrayList<>();
        boolean hasError = false;
        boolean rootEnded = false;
        long rootLatencyNanos = -1;
        ScheduledFuture<?> decisionTask;
    }
}
