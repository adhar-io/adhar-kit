package com.adhar.kit.profiler.aspect;

import com.adhar.kit.profiler.annotation.Profiled;
import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.event.SlowCallEvent;
import com.adhar.kit.profiler.event.SlowCallThresholdBreachedEvent;
import com.adhar.kit.profiler.model.ProfilingResult;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AOP aspect that intercepts methods annotated with {@link Profiled}
 * and records execution time, success/failure, and slow execution warnings.
 * Results are registered in {@link ProfilingRegistry} for aggregation and reporting.
 *
 * <p>Two overhead-control knobs are enforced here (see {@link PerfProfilerProperties}):
 * <ul>
 *   <li><b>sampleRate</b> - only a fraction of calls are actually timed/recorded; the
 *       underlying method always executes regardless of sampling.</li>
 *   <li><b>maxTrackedMethods</b> - caps the number of distinct method names recorded into the
 *       registry, protecting against unbounded growth from highly dynamic metric names.</li>
 * </ul>
 * Slow calls (per-call threshold) publish a {@link SlowCallEvent} in addition to the existing
 * log warning, and a sustained aggregate p99 breach publishes a debounced
 * {@link SlowCallThresholdBreachedEvent}.
 */
@Aspect
public class ProfilingAspect {

    private static final Logger log = LoggerFactory.getLogger(ProfilingAspect.class);

    private final MeterRegistry meterRegistry;
    private final ProfilingRegistry profilingRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final double sampleRate;
    private final int maxTrackedMethods;
    private final long p99AlertThresholdMs;

    private final Set<String> trackedMethods = ConcurrentHashMap.newKeySet();
    private final Set<String> breachedMethods = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean capWarningLogged = new AtomicBoolean(false);

    /**
     * Convenience constructor for manual/test wiring: profiles every call, publishes no
     * application events, and tracks up to the default {@link PerfProfilerProperties#getMaxTrackedMethods()}.
     */
    public ProfilingAspect(MeterRegistry meterRegistry, ProfilingRegistry profilingRegistry) {
        this(meterRegistry, profilingRegistry, null, new PerfProfilerProperties());
    }

    public ProfilingAspect(MeterRegistry meterRegistry,
                            ProfilingRegistry profilingRegistry,
                            ApplicationEventPublisher eventPublisher,
                            PerfProfilerProperties properties) {
        this.meterRegistry = meterRegistry;
        this.profilingRegistry = profilingRegistry;
        this.eventPublisher = eventPublisher;

        double configuredRate = properties == null ? 1.0 : properties.getSampleRate();
        this.sampleRate = Math.max(0.0, Math.min(configuredRate, 1.0));

        int configuredMax = properties == null ? Integer.MAX_VALUE : properties.getMaxTrackedMethods();
        this.maxTrackedMethods = configuredMax <= 0 ? Integer.MAX_VALUE : configuredMax;

        this.p99AlertThresholdMs = properties == null ? 0 : properties.getP99AlertThresholdMs();
    }

    @Around("@annotation(profiled)")
    public Object profileMethod(ProceedingJoinPoint joinPoint, Profiled profiled) throws Throwable {
        return doProfile(joinPoint, profiled);
    }

    @Around("@within(profiled) && execution(public * *(..))")
    public Object profileClass(ProceedingJoinPoint joinPoint, Profiled profiled) throws Throwable {
        return doProfile(joinPoint, profiled);
    }

    private Object doProfile(ProceedingJoinPoint joinPoint, Profiled profiled) throws Throwable {
        if (!shouldSample()) {
            // Overhead guard: skip all timing/recording/metric work, but still run the method.
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String metricName = profiled.value().isEmpty()
                ? className + "." + methodName
                : profiled.value();
        String registryKey = className + "." + methodName;
        boolean trackingAllowed = isTrackingAllowed(registryKey);

        long startTime = System.nanoTime();
        boolean success = true;
        String errorType = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            errorType = t.getClass().getSimpleName();
            throw t;
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

            // Record metrics
            Timer.Builder timerBuilder = Timer.builder("adhar.profiler." + metricName)
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("success", String.valueOf(success));

            if (errorType != null) {
                timerBuilder.tag("error", errorType);
            }

            if (profiled.histogram()) {
                timerBuilder.publishPercentileHistogram();
            }

            timerBuilder.register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            // Log slow executions and publish a per-call slow event
            if (profiled.logSlow() && durationMs > profiled.slowThresholdMs()) {
                log.warn("Slow execution detected: {}.{}() took {}ms (threshold: {}ms)",
                        className, methodName, durationMs, profiled.slowThresholdMs());
                publishEvent(new SlowCallEvent(this, className, methodName, durationMs, profiled.slowThresholdMs()));
            }

            if (trackingAllowed) {
                ProfilingResult result = new ProfilingResult(
                        methodName, className, durationMs, success, errorType, Instant.now());

                // Register result for aggregation and reporting
                profilingRegistry.record(result);
                checkAggregateP99Threshold(registryKey);
            }

            if (log.isDebugEnabled()) {
                log.debug("Profiled {}.{}(): {}ms, success={}", className, methodName, durationMs, success);
            }
        }
    }

    private boolean shouldSample() {
        return sampleRate >= 1.0 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    /**
     * Enforces the {@code maxTrackedMethods} cap: methods already being tracked are always
     * allowed; new methods are allowed until the cap is reached, after which they're skipped
     * (with a single warning log so the cap isn't silently hit repeatedly).
     */
    private boolean isTrackingAllowed(String registryKey) {
        if (trackedMethods.contains(registryKey)) {
            return true;
        }
        if (trackedMethods.size() >= maxTrackedMethods) {
            if (capWarningLogged.compareAndSet(false, true)) {
                log.warn("Perf profiler maxTrackedMethods={} reached; further distinct methods will not be " +
                        "tracked in the registry (first skipped: {})", maxTrackedMethods, registryKey);
            }
            return false;
        }
        trackedMethods.add(registryKey);
        return true;
    }

    private void checkAggregateP99Threshold(String registryKey) {
        if (p99AlertThresholdMs <= 0) {
            return;
        }
        profilingRegistry.getMethodStats(registryKey).ifPresent(methodStats -> {
            if (methodStats.p99Ms() > p99AlertThresholdMs) {
                if (breachedMethods.add(registryKey)) {
                    publishEvent(new SlowCallThresholdBreachedEvent(
                            this, registryKey, methodStats.p99Ms(), p99AlertThresholdMs));
                }
            } else {
                breachedMethods.remove(registryKey);
            }
        });
    }

    private void publishEvent(ApplicationEvent event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }
}
