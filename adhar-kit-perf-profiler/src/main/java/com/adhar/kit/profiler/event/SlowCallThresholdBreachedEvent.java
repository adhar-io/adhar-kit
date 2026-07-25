package com.adhar.kit.profiler.event;

import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Published by {@link com.adhar.kit.profiler.aspect.ProfilingAspect} when a method's
 * aggregate p99 latency crosses the configured {@code adhar.profiler.p99-alert-threshold-ms}.
 * Unlike {@link SlowCallEvent} (fired per slow call), this is a debounced signal fired once
 * when the aggregate crosses the threshold, and re-armed once it drops back below it.
 */
public class SlowCallThresholdBreachedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String methodKey;
    private final double p99Ms;
    private final long thresholdMs;

    public SlowCallThresholdBreachedEvent(Object source, String methodKey, double p99Ms, long thresholdMs) {
        super(source);
        this.methodKey = methodKey;
        this.p99Ms = p99Ms;
        this.thresholdMs = thresholdMs;
    }

    public String getMethodKey() {
        return methodKey;
    }

    public double getP99Ms() {
        return p99Ms;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }
}
