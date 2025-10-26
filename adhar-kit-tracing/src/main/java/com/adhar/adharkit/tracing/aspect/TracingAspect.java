package com.adhar.adharkit.tracing.aspect;

import com.adhar.adharkit.tracing.annotation.AsyncSpan;
import com.adhar.adharkit.tracing.annotation.ContinueSpan;
import com.adhar.adharkit.tracing.annotation.DatabaseSpan;
import com.adhar.adharkit.tracing.annotation.HttpClientSpan;
import com.adhar.adharkit.tracing.annotation.MessagingSpan;
import com.adhar.adharkit.tracing.annotation.NewSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Aspect that handles all tracing annotations and creates/manages spans accordingly.
 * <p>
 * This aspect intercepts methods annotated with tracing annotations and creates appropriate spans
 * with the correct semantic attributes and context propagation.
 * </p>
 */
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class TracingAspect {

    private final Tracer tracer;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * Handle @NewSpan annotation.
     */
    @Around("@annotation(newSpan)")
    public Object handleNewSpan(ProceedingJoinPoint joinPoint, NewSpan newSpan) throws Throwable {
        String spanName = getSpanName(newSpan.value(), joinPoint);

        Span span = tracer.nextSpan()
                .name(spanName)
                .tag("component", "adhar-tracing")
                .tag("span.kind", "internal");

        // Add custom tags
        addTags(span, newSpan.tags(), joinPoint);

        Span startedSpan = span.start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(startedSpan)) {
            Object result = joinPoint.proceed();

            // Handle async results
            if (result instanceof Future<?>) {
                return handleAsyncResult(startedSpan, (Future<?>) result);
            }

            startedSpan.tag("success", "true");
            return result;

        } catch (Throwable throwable) {
            startedSpan.tag("success", "false")
                .tag("error.class", throwable.getClass().getSimpleName())
                .tag("error.message", throwable.getMessage());
            throw throwable;
        } finally {
            startedSpan.end();
        }
    }

    /**
     * Handle @ContinueSpan annotation.
     */
    @Around("@annotation(continueSpan)")
    public Object handleContinueSpan(ProceedingJoinPoint joinPoint, ContinueSpan continueSpan) throws Throwable {
        Span currentSpan = tracer.currentSpan();

        if (currentSpan == null) {
            log.warn("@ContinueSpan used but no current span exists. Method: {}", joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        // Add tags to current span
        addTags(currentSpan, continueSpan.tags(), joinPoint);

        // Add start event
        if (StringUtils.hasText(continueSpan.startEvent())) {
            currentSpan.event(continueSpan.startEvent());
        }

        try {
            Object result = joinPoint.proceed();

            // Add end event
            if (StringUtils.hasText(continueSpan.endEvent())) {
                currentSpan.event(continueSpan.endEvent());
            }

            return result;

        } catch (Throwable throwable) {
            currentSpan.tag("error.class", throwable.getClass().getSimpleName())
                     .tag("error.message", throwable.getMessage());
            throw throwable;
        }
    }

    /**
     * Handle @DatabaseSpan annotation.
     */
    @Around("@annotation(databaseSpan)")
    public Object handleDatabaseSpan(ProceedingJoinPoint joinPoint, DatabaseSpan databaseSpan) throws Throwable {
        String spanName = getSpanName(databaseSpan.value(), joinPoint);
        if (!StringUtils.hasText(spanName) && StringUtils.hasText(databaseSpan.operation()) && StringUtils.hasText(databaseSpan.table())) {
            spanName = databaseSpan.operation() + " " + databaseSpan.table();
        }

        Span span = tracer.nextSpan()
                .name(spanName)
                .tag("component", "adhar-tracing")
                .tag("span.kind", "client")
                .tag("db.system", "sql"); // Can be made configurable

        // Add database-specific tags
        if (StringUtils.hasText(databaseSpan.operation())) {
            span.tag("db.operation", databaseSpan.operation());
        }
        if (StringUtils.hasText(databaseSpan.table())) {
            span.tag("db.sql.table", databaseSpan.table());
        }
        if (StringUtils.hasText(databaseSpan.database())) {
            span.tag("db.name", databaseSpan.database());
        }
        if (StringUtils.hasText(databaseSpan.statement())) {
            String statement = evaluateExpression(databaseSpan.statement(), joinPoint);
            span.tag("db.statement", statement);
        }

        // Add custom tags
        addTags(span, databaseSpan.tags(), joinPoint);

        Span startedSpan = span.start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(startedSpan)) {
            Object result = joinPoint.proceed();
            startedSpan.tag("success", "true");
            return result;

        } catch (Throwable throwable) {
            startedSpan.tag("success", "false")
                .tag("error.class", throwable.getClass().getSimpleName())
                .tag("error.message", throwable.getMessage());
            throw throwable;
        } finally {
            startedSpan.end();
        }
    }

    /**
     * Handle @HttpClientSpan annotation.
     */
    @Around("@annotation(httpClientSpan)")
    public Object handleHttpClientSpan(ProceedingJoinPoint joinPoint, HttpClientSpan httpClientSpan) throws Throwable {
        String spanName = getSpanName(httpClientSpan.value(), joinPoint);
        if (!StringUtils.hasText(spanName) && StringUtils.hasText(httpClientSpan.method())) {
            spanName = "HTTP " + httpClientSpan.method();
        }

        Span span = tracer.nextSpan()
                .name(spanName)
                .tag("component", "adhar-tracing")
                .tag("span.kind", "client");

        // Add HTTP-specific tags
        if (StringUtils.hasText(httpClientSpan.method())) {
            span.tag("http.method", httpClientSpan.method());
        }
        if (StringUtils.hasText(httpClientSpan.url())) {
            String url = evaluateExpression(httpClientSpan.url(), joinPoint);
            span.tag("http.url", url);
        }

        // Add custom tags
        addTags(span, httpClientSpan.tags(), joinPoint);

        Span startedSpan = span.start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(startedSpan)) {
            Object result = joinPoint.proceed();
            startedSpan.tag("success", "true");
            return result;

        } catch (Throwable throwable) {
            startedSpan.tag("success", "false")
                .tag("error.class", throwable.getClass().getSimpleName())
                .tag("error.message", throwable.getMessage());
            throw throwable;
        } finally {
            startedSpan.end();
        }
    }

    /**
     * Handle @MessagingSpan annotation.
     */
    @Around("@annotation(messagingSpan)")
    public Object handleMessagingSpan(ProceedingJoinPoint joinPoint, MessagingSpan messagingSpan) throws Throwable {
        String spanName = getSpanName(messagingSpan.value(), joinPoint);
        if (!StringUtils.hasText(spanName) && StringUtils.hasText(messagingSpan.destination())) {
            spanName = messagingSpan.destination() + " " + messagingSpan.operation();
        }

        Span span = tracer.nextSpan()
                .name(spanName)
                .tag("component", "adhar-tracing")
                .tag("span.kind", "producer"); // Can be configurable

        // Add messaging-specific tags
        if (StringUtils.hasText(messagingSpan.operation())) {
            span.tag("messaging.operation", messagingSpan.operation());
        }
        if (StringUtils.hasText(messagingSpan.destination())) {
            span.tag("messaging.destination", messagingSpan.destination());
        }
        if (StringUtils.hasText(messagingSpan.destinationType())) {
            span.tag("messaging.destination_kind", messagingSpan.destinationType());
        }
        if (StringUtils.hasText(messagingSpan.system())) {
            span.tag("messaging.system", messagingSpan.system());
        }

        // Add custom tags
        addTags(span, messagingSpan.tags(), joinPoint);

        Span startedSpan = span.start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(startedSpan)) {
            Object result = joinPoint.proceed();
            startedSpan.tag("success", "true");
            return result;

        } catch (Throwable throwable) {
            startedSpan.tag("success", "false")
                .tag("error.class", throwable.getClass().getSimpleName())
                .tag("error.message", throwable.getMessage());
            throw throwable;
        } finally {
            startedSpan.end();
        }
    }

    /**
     * Handle @AsyncSpan annotation.
     */
    @Around("@annotation(asyncSpan)")
    public Object handleAsyncSpan(ProceedingJoinPoint joinPoint, AsyncSpan asyncSpan) throws Throwable {
        String spanName = getSpanName(asyncSpan.value(), joinPoint);

        Span span = tracer.nextSpan()
                .name(spanName)
                .tag("component", "adhar-tracing")
                .tag("span.kind", "internal")
                .tag("async", "true");

        // Add custom tags
        addTags(span, asyncSpan.tags(), joinPoint);

        // Capture current trace context for propagation
        TraceContext traceContext = asyncSpan.propagateContext() ? span.context() : null;

        Span startedSpan = span.start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(startedSpan)) {
            Object result = joinPoint.proceed();

            // Handle async results with context propagation
            if (result instanceof CompletableFuture<?>) {
                return handleAsyncCompletableFuture(startedSpan, (CompletableFuture<?>) result, traceContext);
            } else if (result instanceof Future<?>) {
                return handleAsyncResult(startedSpan, (Future<?>) result);
            }

            startedSpan.tag("success", "true");
            return result;

        } catch (Throwable throwable) {
            startedSpan.tag("success", "false")
                .tag("error.class", throwable.getClass().getSimpleName())
                .tag("error.message", throwable.getMessage());
            throw throwable;
        } finally {
            if (!(joinPoint.proceed() instanceof Future<?>)) {
                startedSpan.end();
            }
        }
    }

    /**
     * Get span name from annotation value or method signature.
     */
    private String getSpanName(String annotationValue, ProceedingJoinPoint joinPoint) {
        if (StringUtils.hasText(annotationValue)) {
            return evaluateExpression(annotationValue, joinPoint);
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /**
     * Add tags to span, evaluating SpEL expressions.
     */
    private void addTags(Span span, String[] tags, ProceedingJoinPoint joinPoint) {
        if (tags.length % 2 != 0) {
            log.warn("Tags array must have even number of elements (key-value pairs). Ignoring tags for method: {}",
                    joinPoint.getSignature().toShortString());
            return;
        }

        for (int i = 0; i < tags.length; i += 2) {
            String key = tags[i];
            String value = evaluateExpression(tags[i + 1], joinPoint);
            span.tag(key, value);
        }
    }

    /**
     * Evaluate SpEL expression with method context.
     */
    private String evaluateExpression(String expression, ProceedingJoinPoint joinPoint) {
        if (!StringUtils.hasText(expression)) {
            return expression;
        }

        // If not a SpEL expression, return as-is
        if (!expression.contains("#{")) {
            return expression;
        }

        try {
            EvaluationContext context = createEvaluationContext(joinPoint);
            Expression expr = expressionParser.parseExpression(expression);
            Object result = expr.getValue(context);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.warn("Failed to evaluate expression: {} for method: {}", expression, joinPoint.getSignature().toShortString(), e);
            return expression;
        }
    }

    /**
     * Create SpEL evaluation context with method parameters.
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }

        return context;
    }

    /**
     * Handle async Future results.
     */
    private Future<?> handleAsyncResult(Span span, Future<?> future) {
        // Note: This is a simplified implementation
        // In a real-world scenario, you might want to use CompletableFuture.supplyAsync
        // with proper context propagation
        return future;
    }

    /**
     * Handle async CompletableFuture results with context propagation.
     */
    private CompletableFuture<?> handleAsyncCompletableFuture(Span span, CompletableFuture<?> future, TraceContext traceContext) {
        return future.whenComplete((result, throwable) -> {
            try {
                if (traceContext != null) {
                    // Restore trace context in async callback
                    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                        if (throwable != null) {
                            span.tag("success", "false")
                                .tag("error.class", throwable.getClass().getSimpleName())
                                .tag("error.message", throwable.getMessage());
                        } else {
                            span.tag("success", "true");
                        }
                    }
                }
            } finally {
                span.end();
            }
        });
    }
}
