package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.TrackPerformance;
import com.adhar.adharkit.logging.performance.PerformanceLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect implementation for {@link TrackPerformance @TrackPerformance}: records each invocation
 * of an annotated method (or method of an annotated class) with the {@link PerformanceLogger},
 * feeding aggregated statistics and slow-operation detection.
 */
@Aspect
@Component
public class TrackPerformanceAspect {

    private final PerformanceLogger performanceLogger;

    public TrackPerformanceAspect(PerformanceLogger performanceLogger) {
        this.performanceLogger = performanceLogger;
    }

    @Around("@annotation(com.adhar.adharkit.logging.annotation.TrackPerformance)"
            + " || @within(com.adhar.adharkit.logging.annotation.TrackPerformance)")
    public Object trackPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        TrackPerformance annotation = AnnotationUtils.findAnnotation(method, TrackPerformance.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(method.getDeclaringClass(), TrackPerformance.class);
        }
        if (annotation == null) {
            return joinPoint.proceed();
        }

        String operation = annotation.value().isEmpty()
                ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : annotation.value();

        long startNanos = System.nanoTime();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (annotation.slowThresholdMs() >= 0) {
                performanceLogger.record(operation, durationMs, success, annotation.slowThresholdMs());
            } else {
                performanceLogger.record(operation, durationMs, success);
            }
        }
    }
}
