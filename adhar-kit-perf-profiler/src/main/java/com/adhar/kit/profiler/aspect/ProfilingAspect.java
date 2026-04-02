package com.adhar.kit.profiler.aspect;

import com.adhar.kit.profiler.annotation.Profiled;
import com.adhar.kit.profiler.model.ProfilingResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * AOP aspect that intercepts methods annotated with {@link Profiled}
 * and records execution time, success/failure, and slow execution warnings.
 */
@Aspect
public class ProfilingAspect {

    private static final Logger log = LoggerFactory.getLogger(ProfilingAspect.class);

    private final MeterRegistry meterRegistry;

    public ProfilingAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
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
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String metricName = profiled.value().isEmpty()
                ? className + "." + methodName
                : profiled.value();

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

            // Log slow executions
            if (profiled.logSlow() && durationMs > profiled.slowThresholdMs()) {
                log.warn("Slow execution detected: {}.{}() took {}ms (threshold: {}ms)",
                        className, methodName, durationMs, profiled.slowThresholdMs());
            }

            ProfilingResult result = new ProfilingResult(
                    methodName, className, durationMs, success, errorType, Instant.now());

            if (log.isDebugEnabled()) {
                log.debug("Profiled {}.{}(): {}ms, success={}", className, methodName, durationMs, success);
            }
        }
    }
}
