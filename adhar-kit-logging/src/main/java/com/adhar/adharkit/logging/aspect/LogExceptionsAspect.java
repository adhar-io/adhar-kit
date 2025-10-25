package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogExceptions;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Aspect implementation for @LogExceptions annotation.
 * Provides centralized exception logging with configurable filtering.
 */
@Aspect
@Component
public class LogExceptionsAspect {

    private final ObjectMapper objectMapper;

    public LogExceptionsAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(logExceptions) || @within(logExceptions)")
    public Object handleExceptions(ProceedingJoinPoint joinPoint, LogExceptions logExceptions) throws Throwable {
        if (logExceptions == null) {
            logExceptions = getLogExceptionsAnnotation(joinPoint);
        }

        if (logExceptions == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass());

        String operationName = getOperationName(logExceptions, method);

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            if (shouldLogException(throwable, logExceptions)) {
                logException(logger, logExceptions, method, joinPoint.getArgs(),
                    operationName, throwable);
            }
            throw throwable;
        }
    }

    private LogExceptions getLogExceptionsAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check method-level annotation first
        LogExceptions methodAnnotation = AnnotationUtils.findAnnotation(method, LogExceptions.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Check class-level annotation
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), LogExceptions.class);
    }

    private String getOperationName(LogExceptions annotation, Method method) {
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private boolean shouldLogException(Throwable throwable, LogExceptions annotation) {
        Class<? extends Throwable> exceptionClass = throwable.getClass();

        // Check exclude list first
        for (Class<? extends Throwable> excludeType : annotation.exclude()) {
            if (excludeType.isAssignableFrom(exceptionClass)) {
                return false;
            }
        }

        // If only list is specified, check if exception matches
        Class<? extends Throwable>[] onlyTypes = annotation.only();
        if (onlyTypes.length > 0) {
            for (Class<? extends Throwable> onlyType : onlyTypes) {
                if (onlyType.isAssignableFrom(exceptionClass)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    private void logException(Logger logger, LogExceptions annotation, Method method, Object[] args,
                             String operationName, Throwable throwable) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "exception");
        logData.put("operation", operationName);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getSimpleName());
        logData.put("exception", throwable.getClass().getName());
        logData.put("exceptionMessage", throwable.getMessage());

        // Add correlation ID if available
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            logData.put("correlationId", correlationId);
        }

        if (annotation.includeArgs() && args != null && args.length > 0) {
            logData.put("arguments", sanitizeArguments(args));
        }

        if (annotation.includeStackTrace()) {
            logData.put("stackTrace", getStackTrace(throwable));
        }

        // Add root cause information if available
        Throwable rootCause = getRootCause(throwable);
        if (rootCause != throwable) {
            Map<String, Object> rootCauseData = new HashMap<>();
            rootCauseData.put("exception", rootCause.getClass().getName());
            rootCauseData.put("message", rootCause.getMessage());
            logData.put("rootCause", rootCauseData);
        }

        logAtLevel(logger, annotation.level(), "Exception occurred: {}",
            toJsonString(logData));
    }

    private Object[] sanitizeArguments(Object[] args) {
        // Create a copy to avoid modifying original arguments
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = sanitizeArgument(args[i]);
        }
        return sanitized;
    }

    private Object sanitizeArgument(Object arg) {
        if (arg == null) {
            return null;
        }

        // For sensitive data types, return type info only
        String className = arg.getClass().getSimpleName();
        if (className.toLowerCase().contains("password") ||
            className.toLowerCase().contains("secret") ||
            className.toLowerCase().contains("token")) {
            return "[" + className + "]";
        }

        return arg;
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
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
