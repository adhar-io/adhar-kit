package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.Audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect implementation for @Audit annotation.
 * Provides comprehensive audit logging for security and compliance purposes.
 */
@Aspect
@Component
public class AuditAspect {

    private final ObjectMapper objectMapper;

    public AuditAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audit) || @within(audit)")
    public Object auditExecution(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        if (audit == null) {
            audit = getAuditAnnotation(joinPoint);
        }

        if (audit == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Logger logger = LoggerFactory.getLogger("AUDIT." + method.getDeclaringClass().getName());

        String operationName = getOperationName(audit, method);
        String eventType = getEventType(audit, method);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            exception = throwable;
            throw throwable;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            logAuditEvent(logger, audit, method, joinPoint.getArgs(), result, exception,
                operationName, eventType, executionTime);
        }
    }

    private Audit getAuditAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check method-level annotation first
        Audit methodAnnotation = AnnotationUtils.findAnnotation(method, Audit.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Check class-level annotation
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), Audit.class);
    }

    private String getOperationName(Audit annotation, Method method) {
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private String getEventType(Audit annotation, Method method) {
        if (!annotation.eventType().isEmpty()) {
            return annotation.eventType();
        }
        return "METHOD_EXECUTION";
    }

    private void logAuditEvent(Logger logger, Audit annotation, Method method, Object[] args,
                              Object result, Throwable exception, String operationName,
                              String eventType, long executionTime) {
        Map<String, Object> logData = new HashMap<>();

        // Basic audit information
        logData.put("event", "audit");
        logData.put("eventType", eventType);
        logData.put("operation", operationName);
        logData.put("method", method.getName());
        logData.put("class", method.getDeclaringClass().getName());
        logData.put("timestamp", Instant.now().toString());
        logData.put("executionTimeMs", executionTime);
        logData.put("status", exception == null ? "SUCCESS" : "FAILURE");

        // Add correlation ID if available
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            logData.put("correlationId", correlationId);
        }

        // Add user context if requested and available
        if (annotation.includeUser()) {
            addUserContext(logData);
        }

        // Add method arguments if requested
        if (annotation.includeArgs() && args != null && args.length > 0) {
            logData.put("arguments", sanitizeAuditArguments(args));
        }

        // Add method result if requested and successful
        if (annotation.includeResult() && result != null && exception == null) {
            logData.put("result", sanitizeAuditResult(result));
        }

        // Add exception information if failed
        if (exception != null) {
            Map<String, Object> exceptionData = new HashMap<>();
            exceptionData.put("type", exception.getClass().getName());
            exceptionData.put("message", exception.getMessage());
            logData.put("exception", exceptionData);
        }

        // Add tags if specified
        if (annotation.tags().length > 0) {
            logData.put("tags", Arrays.asList(annotation.tags()));
        }

        // Add additional audit metadata
        addAuditMetadata(logData);

        logAtLevel(logger, annotation.level(), "Audit event: {}",
            toJsonString(logData));
    }

    private void addUserContext(Map<String, Object> logData) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Map<String, Object> userContext = new HashMap<>();
                userContext.put("username", authentication.getName());
                userContext.put("authorities", authentication.getAuthorities().toString());
                userContext.put("authenticated", authentication.isAuthenticated());
                logData.put("user", userContext);
            }
        } catch (Exception e) {
            // Ignore security context issues in audit logging
            logData.put("user", "UNKNOWN");
        }
    }

    private Object[] sanitizeAuditArguments(Object[] args) {
        // For audit logs, be more conservative with argument logging
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = sanitizeForAudit(args[i]);
        }
        return sanitized;
    }

    private Object sanitizeAuditResult(Object result) {
        return sanitizeForAudit(result);
    }

    private Object sanitizeForAudit(Object obj) {
        if (obj == null) {
            return null;
        }

        // For audit purposes, avoid logging complex objects or sensitive data
        String className = obj.getClass().getSimpleName();
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            String str = obj.toString();
            // Check for potentially sensitive content
            if (containsSensitiveKeywords(str)) {
                return "[REDACTED]";
            }
            return obj;
        }

        return "[" + className + "]";
    }

    private boolean containsSensitiveKeywords(String str) {
        if (str == null) return false;
        String lowerStr = str.toLowerCase();
        return lowerStr.contains("password") ||
               lowerStr.contains("secret") ||
               lowerStr.contains("token") ||
               lowerStr.contains("key") ||
               lowerStr.contains("credential");
    }

    private void addAuditMetadata(Map<String, Object> logData) {
        // Add thread information
        Thread currentThread = Thread.currentThread();
        Map<String, Object> threadInfo = new HashMap<>();
        threadInfo.put("name", currentThread.getName());
        threadInfo.put("id", currentThread.getId());
        logData.put("thread", threadInfo);

        // Add session information if available from MDC
        String sessionId = MDC.get("sessionId");
        if (sessionId != null) {
            logData.put("sessionId", sessionId);
        }

        String remoteAddr = MDC.get("remoteAddr");
        if (remoteAddr != null) {
            logData.put("remoteAddr", remoteAddr);
        }

        String userAgent = MDC.get("userAgent");
        if (userAgent != null) {
            logData.put("userAgent", userAgent);
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
