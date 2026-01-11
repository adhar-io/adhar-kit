package com.adhar.kit.metrics.aspect;

import com.adhar.kit.metrics.annotation.Timed;
import com.adhar.kit.metrics.util.MetricsUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * Aspect for automatically timing methods annotated with {@link Timed}.
 * <p>
 * This aspect provides automated instrumentation for methods, recording their execution time
 * as metrics in the meter registry.
 * </p>
 */
@Aspect
@Component
@Slf4j
@ConditionalOnProperty(prefix = "adhar.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MetricsAspect {

    private final MeterRegistry registry;
    private final MetricsUtils metricsUtils;

    /**
     * Constructor for MetricsAspect.
     *
     * @param registry The meter registry
     * @param metricsUtils The metrics utility
     */
    @Autowired
    public MetricsAspect(MeterRegistry registry, MetricsUtils metricsUtils) {
        this.registry = registry;
        this.metricsUtils = metricsUtils;
        log.info("Initializing metrics aspect for automated method timing");
    }

    /**
     * Around advice for methods annotated with {@link Timed}.
     *
     * @param joinPoint The join point
     * @return The result of the method
     * @throws Throwable If an error occurs
     */
    @Around("@annotation(com.adhar.kit.metrics.annotation.Timed)")
    public Object timeMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Timed timed = method.getAnnotation(Timed.class);

        String metricName = getMetricName(timed, method, joinPoint);
        String[] tags = getTags(timed, method, joinPoint);

        Timer.Sample sample = Timer.start(registry);
        
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            // Add exception tag
            String[] tagsWithException = addExceptionTag(tags, throwable);
            sample.stop(metricsUtils.timer(metricName, tagsWithException));
            throw throwable;
        } finally {
            sample.stop(metricsUtils.timer(metricName, tags));
        }
    }

    /**
    /**
     * Get the metric name from the annotation or method.
     * @param timed The timed annotation
     * @param method The method
     * @param joinPoint The join point
     * @return The metric name
     */
    private String getMetricName(Timed timed, Method method, ProceedingJoinPoint joinPoint) {

        if (StringUtils.hasText(timed.name())) {
            return timed.name();
        }
        
        return joinPoint.getTarget().getClass().getSimpleName() + "." + method.getName();
    }

    /**
     * Gets the tags from the annotation.
     *
     * @param timed The timed annotation
     * @param method The method
     * @param joinPoint The join point
     * @return The tags
     */
    private String[] getTags(Timed timed, Method method, ProceedingJoinPoint joinPoint) {
        String[] tags = timed.tags();
        
        if (tags.length == 0) {
            // Add default tags
            return new String[] {
                "class", joinPoint.getTarget().getClass().getSimpleName(),
                "method", method.getName()
            };
        }
        
        return tags;
    }

    /**
     * Adds an exception tag to the tags.
     *
     * @param tags The tags
     * @param throwable The exception
     * @return The tags with the exception tag
     */
    private String[] addExceptionTag(String[] tags, Throwable throwable) {
        String[] newTags = new String[tags.length + 2];
        System.arraycopy(tags, 0, newTags, 0, tags.length);
        newTags[tags.length] = "exception";
        newTags[tags.length + 1] = throwable.getClass().getSimpleName();
        return newTags;
    }
}