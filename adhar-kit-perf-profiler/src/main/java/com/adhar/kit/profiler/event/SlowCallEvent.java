package com.adhar.kit.profiler.event;

import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Published by {@link com.adhar.kit.profiler.aspect.ProfilingAspect} whenever a single
 * profiled call exceeds its configured slow-execution threshold. Complements the existing
 * {@code log.warn} so applications can react (alerting, circuit breaking, etc.) without
 * scraping logs.
 */
public class SlowCallEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String className;
    private final String methodName;
    private final long durationMs;
    private final long thresholdMs;

    public SlowCallEvent(Object source, String className, String methodName, long durationMs, long thresholdMs) {
        super(source);
        this.className = className;
        this.methodName = methodName;
        this.durationMs = durationMs;
        this.thresholdMs = thresholdMs;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    /** Returns the {@code className.methodName} key used to identify the method in the registry. */
    public String getMethodKey() {
        return className + "." + methodName;
    }
}
