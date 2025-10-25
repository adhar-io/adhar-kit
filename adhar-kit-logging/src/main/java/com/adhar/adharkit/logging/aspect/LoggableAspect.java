package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.Loggable;
import com.adhar.adharkit.logging.annotation.Sensitive;
import com.adhar.adharkit.logging.util.AdharLogger;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aspect implementation for @Loggable annotation.
 * Provides structured logging with entry/exit, argument/result logging, and sampling.
 */
@Aspect
@Component
public class LoggableAspect {

    private static final String OPERATION_KEY = "operation";
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String EXECUTION_TIME_KEY = "executionTimeMs";

    private final ObjectMapper objectMapper;

    public LoggableAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(loggable) || @within(loggable)")
    public Object logExecution(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        if (loggable == null) {
            loggable = getLoggableAnnotation(joinPoint);
        }

        if (loggable == null || !shouldLog(loggable.sampleRate())) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass());

        String operationName = getOperationName(loggable, method);
        String correlationId = UUID.randomUUID().toString();

        // Set up MDC
        String previousOperation = MDC.get(OPERATION_KEY);
        String previousCorrelationId = MDC.get(CORRELATION_ID_KEY);

        try {
            MDC.put(OPERATION_KEY, operationName);
            MDC.put(CORRELATION_ID_KEY, correlationId);

            long startTime = System.currentTimeMillis();

            // Log method entry
            logMethodEntry(logger, loggable, method, joinPoint.getArgs(), operationName, correlationId);

            Object result;
            try {
                result = joinPoint.proceed();

                // Log method exit with result
                long executionTime = System.currentTimeMillis() - startTime;
                logMethodExit(logger, loggable, method, result, operationName, correlationId, executionTime);

                return result;
            } catch (Throwable throwable) {
                // Log method exit with exception
                long executionTime = System.currentTimeMillis() - startTime;
                logMethodException(logger, loggable, method, throwable, operationName, correlationId, executionTime);
                throw throwable;
            }
        } finally {
            // Restore previous MDC values
            if (previousOperation != null) {
                MDC.put(OPERATION_KEY, previousOperation);
            } else {
                MDC.remove(OPERATION_KEY);
            }

            if (previousCorrelationId != null) {
                MDC.put(CORRELATION_ID_KEY, previousCorrelationId);
            } else {
                MDC.remove(CORRELATION_ID_KEY);
            }
        }
    }

    private Loggable getLoggableAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check method-level annotation first
        Loggable methodAnnotation = AnnotationUtils.findAnnotation(method, Loggable.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Check class-level annotation
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), Loggable.class);
    }

    private boolean shouldLog(double sampleRate) {
        return sampleRate >= 1.0 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private String getOperationName(Loggable loggable, Method method) {
        if (!loggable.value().isEmpty()) {
            return loggable.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private void logMethodEntry(Logger logger, Loggable loggable, Method method, Object[] args,
                               String operationName, String correlationId) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "method_entry");
        logData.put("operation", operationName);
        logData.put("correlationId", correlationId);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getSimpleName());

        if (loggable.logArgs() && args != null && args.length > 0) {
            logData.put("arguments", maskSensitiveData(method, args, loggable.maskFields()));
        }

        logAtLevel(logger, loggable.level(), "Method entry: {}", toJsonString(logData));
    }

    private void logMethodExit(Logger logger, Loggable loggable, Method method, Object result,
                              String operationName, String correlationId, long executionTime) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "method_exit");
        logData.put("operation", operationName);
        logData.put("correlationId", correlationId);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getSimpleName());
        logData.put("executionTimeMs", executionTime);

        if (loggable.logResult() && result != null) {
            logData.put("result", maskSensitiveResult(result));
        }

        logAtLevel(logger, loggable.level(), "Method exit: {}", toJsonString(logData));
    }

    private void logMethodException(Logger logger, Loggable loggable, Method method, Throwable throwable,
                                   String operationName, String correlationId, long executionTime) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "method_exception");
        logData.put("operation", operationName);
        logData.put("correlationId", correlationId);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getSimpleName());
        logData.put("executionTimeMs", executionTime);
        logData.put("exception", throwable.getClass().getSimpleName());
        logData.put("exceptionMessage", throwable.getMessage());

        logAtLevel(logger, loggable.level(), "Method exception: {}", toJsonString(logData));
    }

    // Helper methods for logging
    private void logAtLevel(Logger logger, org.slf4j.event.Level level, String message, String jsonData) {
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

    private Object[] maskSensitiveData(Method method, Object[] args, String[] maskFields) {
        if (args == null || args.length == 0) {
            return args;
        }

        Parameter[] parameters = method.getParameters();
        Object[] maskedArgs = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            if (i < parameters.length && parameters[i].isAnnotationPresent(Sensitive.class)) {
                Sensitive sensitive = parameters[i].getAnnotation(Sensitive.class);
                maskedArgs[i] = maskValue(args[i], sensitive);
            } else {
                maskedArgs[i] = maskFieldsInObject(args[i], maskFields);
            }
        }

        return maskedArgs;
    }

    private Object maskSensitiveResult(Object result) {
        // For now, just return the result as-is
        // Could be enhanced to detect and mask sensitive fields in result objects
        return result;
    }

    private Object maskValue(Object value, Sensitive sensitive) {
        if (value == null) {
            return null;
        }

        String stringValue = value.toString();
        if (stringValue.isEmpty()) {
            return stringValue;
        }

        int showFirst = sensitive.showFirst();
        int showLast = sensitive.showLast();
        String mask = sensitive.mask();

        if (showFirst + showLast >= stringValue.length()) {
            return stringValue;
        }

        if (showFirst == 0 && showLast == 0) {
            return mask;
        }

        StringBuilder masked = new StringBuilder();
        if (showFirst > 0) {
            masked.append(stringValue, 0, Math.min(showFirst, stringValue.length()));
        }
        masked.append(mask);
        if (showLast > 0) {
            int startPos = Math.max(showFirst, stringValue.length() - showLast);
            masked.append(stringValue.substring(startPos));
        }

        return masked.toString();
    }

    private Object maskFieldsInObject(Object obj, String[] maskFields) {
        if (obj == null || maskFields.length == 0) {
            return obj;
        }

        // For now, return as-is
        // Could be enhanced to mask specific fields in Map or JavaBean objects
        return obj;
    }
}
