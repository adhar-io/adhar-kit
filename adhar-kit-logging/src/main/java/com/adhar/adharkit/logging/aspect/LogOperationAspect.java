package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogOperation;
import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.event.Level;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Aspect implementation for {@link LogOperation @LogOperation}: publishes an OPERATION
 * {@link AppLogEvent} with duration, outcome and optional masked arguments/result for every
 * invocation of an annotated method (or method of an annotated class).
 */
@Aspect
@Component
public class LogOperationAspect {

    private final AppLogEventPublisher publisher;
    private final LogDataMasker masker;

    public LogOperationAspect(AppLogEventPublisher publisher, LogDataMasker masker) {
        this.publisher = publisher;
        this.masker = masker;
    }

    @Around("@annotation(com.adhar.adharkit.logging.annotation.LogOperation)"
            + " || @within(com.adhar.adharkit.logging.annotation.LogOperation)")
    public Object trackOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogOperation annotation = AnnotationUtils.findAnnotation(method, LogOperation.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(method.getDeclaringClass(), LogOperation.class);
        }
        if (annotation == null) {
            return joinPoint.proceed();
        }

        String operation = annotation.value().isEmpty()
                ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : annotation.value();

        long startNanos = System.nanoTime();
        AppLogEvent.Builder builder = AppLogEvent.builder()
                .type(AppLogEventType.OPERATION)
                .name(operation)
                .category(annotation.category().isEmpty() ? null : annotation.category())
                .source(method.getDeclaringClass())
                .tags(annotation.tags());

        if (annotation.includeArgs()) {
            builder.metadata("arguments", sanitizeArgs(joinPoint.getArgs()));
        }

        try {
            Object result = joinPoint.proceed();
            builder.outcome(AppLogEventOutcome.SUCCESS);
            if (annotation.includeResult()) {
                builder.metadata("result", sanitize(result));
            }
            return result;
        } catch (Throwable t) {
            builder.outcome(AppLogEventOutcome.FAILURE)
                    .severity(Level.ERROR)
                    .error(t);
            throw t;
        } finally {
            builder.durationMs((System.nanoTime() - startNanos) / 1_000_000);
            publisher.publish(builder.build());
        }
    }

    private List<Object> sanitizeArgs(Object[] args) {
        List<Object> sanitized = new ArrayList<>(args != null ? args.length : 0);
        if (args != null) {
            for (Object arg : args) {
                sanitized.add(sanitize(arg));
            }
        }
        return sanitized;
    }

    /**
     * Only simple values are logged verbatim (masked); complex objects are reduced to their type
     * name so aspects never serialize entire object graphs into the log.
     */
    private Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String s) {
            return masker.maskText(s);
        }
        return "[" + value.getClass().getSimpleName() + "]";
    }
}
