package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aspect implementation for @LogMetrics annotation.
 * Provides business metrics and KPI logging for monitoring and analytics.
 */
@Aspect
@Component
public class LogMetricsAspect {

    private final ObjectMapper objectMapper;

    public LogMetricsAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(logMetrics) || @within(logMetrics)")
    public Object logMetrics(ProceedingJoinPoint joinPoint, LogMetrics logMetrics) throws Throwable {
        if (logMetrics == null) {
            logMetrics = getLogMetricsAnnotation(joinPoint);
        }

        if (logMetrics == null || !shouldLog(logMetrics.sampleRate())) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Logger logger = LoggerFactory.getLogger("METRICS." + method.getDeclaringClass().getName());

        String operationName = getOperationName(logMetrics, method);
        String metricName = getMetricName(logMetrics, method);

        long startTime = System.nanoTime();
        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            exception = throwable;
            throw throwable;
        } finally {
            long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;
            logMetricEvent(logger, logMetrics, method, joinPoint.getArgs(), result, exception,
                operationName, metricName, executionTimeMs);
        }
    }

    private LogMetrics getLogMetricsAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check method-level annotation first
        LogMetrics methodAnnotation = AnnotationUtils.findAnnotation(method, LogMetrics.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Check class-level annotation
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), LogMetrics.class);
    }

    private boolean shouldLog(double sampleRate) {
        return sampleRate >= 1.0 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private String getOperationName(LogMetrics annotation, Method method) {
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private String getMetricName(LogMetrics annotation, Method method) {
        if (!annotation.metricName().isEmpty()) {
            return annotation.metricName();
        }
        return method.getName();
    }

    private void logMetricEvent(Logger logger, LogMetrics annotation, Method method, Object[] args,
                               Object result, Throwable exception, String operationName,
                               String metricName, long executionTimeMs) {
        Map<String, Object> logData = new HashMap<>();

        // Basic metric information
        logData.put("event", "metric");
        logData.put("metricName", metricName);
        logData.put("operation", operationName);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getSimpleName());
        logData.put("timestamp", Instant.now().toString());
        logData.put("status", exception == null ? "SUCCESS" : "FAILURE");

        // Add correlation ID if available
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            logData.put("correlationId", correlationId);
        }

        // Add execution time if requested
        if (annotation.includeExecutionTime()) {
            logData.put("executionTimeMs", executionTimeMs);
            logData.put("executionTimeNs", executionTimeMs * 1_000_000);
        }

        // Add method arguments if requested
        if (annotation.includeArgs() && args != null && args.length > 0) {
            logData.put("arguments", sanitizeMetricArguments(args));
        }

        // Add method result if requested and successful
        if (annotation.includeResult() && result != null && exception == null) {
            logData.put("result", sanitizeMetricResult(result));
        }

        // Add exception information if failed
        if (exception != null) {
            Map<String, Object> exceptionData = new HashMap<>();
            exceptionData.put("type", exception.getClass().getSimpleName());
            exceptionData.put("message", exception.getMessage());
            logData.put("exception", exceptionData);
        }

        // Add tags if specified
        if (annotation.tags().length > 0) {
            logData.put("tags", Arrays.asList(annotation.tags()));
        }

        // Add additional metric metadata
        addMetricMetadata(logData, method);

        logAtLevel(logger, annotation.level(), "Metric event: {}",
            toJsonString(logData));
    }

    private Object[] sanitizeMetricArguments(Object[] args) {
        // For metrics, focus on extracting relevant business data
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = sanitizeForMetrics(args[i]);
        }
        return sanitized;
    }

    private Object sanitizeMetricResult(Object result) {
        return sanitizeForMetrics(result);
    }

    private Object sanitizeForMetrics(Object obj) {
        if (obj == null) {
            return null;
        }

        // For metrics, extract meaningful business data
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }

        // For collections, return size information
        if (obj instanceof java.util.Collection) {
            Map<String, Object> collectionInfo = new HashMap<>();
            collectionInfo.put("type", "Collection");
            collectionInfo.put("size", ((java.util.Collection<?>) obj).size());
            return collectionInfo;
        }

        if (obj instanceof java.util.Map) {
            Map<String, Object> mapInfo = new HashMap<>();
            mapInfo.put("type", "Map");
            mapInfo.put("size", ((java.util.Map<?, ?>) obj).size());
            return mapInfo;
        }

        if (obj.getClass().isArray()) {
            Map<String, Object> arrayInfo = new HashMap<>();
            arrayInfo.put("type", "Array");
            arrayInfo.put("length", java.lang.reflect.Array.getLength(obj));
            return arrayInfo;
        }

        // For other objects, return type information
        return "[" + obj.getClass().getSimpleName() + "]";
    }

    private void addMetricMetadata(Map<String, Object> logData, Method method) {
        // Add method metadata for better metric categorization
        Map<String, Object> methodInfo = new HashMap<>();
        methodInfo.put("parameterCount", method.getParameterCount());
        methodInfo.put("returnType", method.getReturnType().getSimpleName());
        logData.put("methodInfo", methodInfo);

        // Add thread information for concurrency metrics
        Thread currentThread = Thread.currentThread();
        Map<String, Object> threadInfo = new HashMap<>();
        threadInfo.put("name", currentThread.getName());
        threadInfo.put("id", currentThread.getId());
        logData.put("thread", threadInfo);

        // Add JVM metrics if available
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvmInfo = new HashMap<>();
        jvmInfo.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        jvmInfo.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        jvmInfo.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        jvmInfo.put("availableProcessors", runtime.availableProcessors());
        logData.put("jvm", jvmInfo);

        // Add additional MDC context for business metrics
        String userId = MDC.get("userId");
        if (userId != null) {
            logData.put("userId", userId);
        }

        String tenantId = MDC.get("tenantId");
        if (tenantId != null) {
            logData.put("tenantId", tenantId);
        }

        String businessContext = MDC.get("businessContext");
        if (businessContext != null) {
            logData.put("businessContext", businessContext);
        }
    }

    // Helper methods for logging
    private void logAtLevel(org.slf4j.Logger logger, org.slf4j.event.Level level, String message, String jsonData) {
        switch (level) {
            case ERROR -> logger.error(message, jsonData);
            case WARN -> logger.warn(message, jsonData);
            case INFO -> logger.info(message, jsonData);
            case DEBUG -> logger.debug(message, jsonData);
            case TRACE -> logger.trace(message, jsonData);
        }
    }

    private String toJsonString(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "JSON_SERIALIZATION_FAILED: " + obj.toString();
        }
    }
}
