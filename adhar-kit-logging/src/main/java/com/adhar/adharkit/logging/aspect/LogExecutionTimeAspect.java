package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogExecutionTime;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aspect implementation for @LogExecutionTime annotation.
 * Logs method execution time with optional thresholds and sampling.
 */
@Aspect
@Component
public class LogExecutionTimeAspect {

    private final ObjectMapper objectMapper;

    public LogExecutionTimeAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(logExecutionTime) || @within(logExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, LogExecutionTime logExecutionTime) throws Throwable {
        if (logExecutionTime == null) {
            logExecutionTime = getLogExecutionTimeAnnotation(joinPoint);
        }

        if (logExecutionTime == null || !shouldLog(logExecutionTime.sampleRate())) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass());

        String operationName = getOperationName(logExecutionTime, method);

        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();

            long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;

            if (executionTimeMs >= logExecutionTime.thresholdMs()) {
                logExecutionTime(logger, logExecutionTime, method, joinPoint.getArgs(),
                    operationName, executionTimeMs);
            }

            return result;
        } catch (Throwable throwable) {
            long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;

            if (executionTimeMs >= logExecutionTime.thresholdMs()) {
                logExecutionTimeWithException(logger, logExecutionTime, method, joinPoint.getArgs(),
                    operationName, executionTimeMs, throwable);
            }

            throw throwable;
        }
    }

    private LogExecutionTime getLogExecutionTimeAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check method-level annotation first
        LogExecutionTime methodAnnotation = AnnotationUtils.findAnnotation(method, LogExecutionTime.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Check class-level annotation
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), LogExecutionTime.class);
    }

    private boolean shouldLog(double sampleRate) {
        return sampleRate >= 1.0 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private String getOperationName(LogExecutionTime annotation, Method method) {
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private void logExecutionTime(Logger logger, LogExecutionTime annotation, Method method, Object[] args,
                                 String operationName, long executionTimeMs) {
        Map<String, Object> logData = createBaseLogData(method, operationName, executionTimeMs);
        logData.put("event", "execution_time");
        logData.put("status", "success");

        if (annotation.includeArgs() && args != null && args.length > 0) {
            logData.put("arguments", args);
        }

        // Add correlation ID if available
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            logData.put("correlationId", correlationId);
        }

        logAtLevel(logger, annotation.level(), "Execution time: {}",
            toJsonString(logData));
    }

    private void logExecutionTimeWithException(Logger logger, LogExecutionTime annotation, Method method,
                                             Object[] args, String operationName, long executionTimeMs,
                                             Throwable throwable) {
        Map<String, Object> logData = createBaseLogData(method, operationName, executionTimeMs);
        logData.put("event", "execution_time");
        logData.put("status", "exception");
        logData.put("exception", throwable.getClass().getSimpleName());
        logData.put("exceptionMessage", throwable.getMessage());

        if (annotation.includeArgs() && args != null && args.length > 0) {
            logData.put("arguments", args);
        }

        // Add correlation ID if available
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            logData.put("correlationId", correlationId);
        }

        logAtLevel(logger, annotation.level(), "Execution time (with exception): {}",
            toJsonString(logData));
    }

    private Map<String, Object> createBaseLogData(Method method, String operationName, long executionTimeMs) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("operation", operationName);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getSimpleName());
        logData.put("executionTimeMs", executionTimeMs);
        return logData;
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
