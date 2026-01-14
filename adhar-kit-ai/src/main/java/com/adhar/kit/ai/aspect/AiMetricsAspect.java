package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Aspect for processing @AiMetrics annotations.
 *
 * <p>Tracks AI operation metrics including tokens, costs, latency, and success rates.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(10)  // Execute early to track all operations
@Slf4j
public class AiMetricsAspect {

    private final MeterRegistry meterRegistry;

    public AiMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Intercepts methods annotated with @AiMetrics.
     */
    @Around("@annotation(aiMetrics)")
    public Object processMetricsAnnotation(ProceedingJoinPoint joinPoint, AiMetrics aiMetrics)
            throws Throwable {

        String metricName = aiMetrics.metricName().isEmpty()
            ? "ai.operation"
            : aiMetrics.metricName();

        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            // Execute method
            Object result = joinPoint.proceed();
            success = true;
            return result;

        } catch (Throwable t) {
            success = false;
            throw t;

        } finally {
            // Track metrics
            long duration = System.currentTimeMillis() - startTime;

            if (aiMetrics.trackLatency()) {
                trackLatency(metricName, duration, aiMetrics);
            }

            if (aiMetrics.trackSuccessRate()) {
                trackSuccess(metricName, success, aiMetrics);
            }

            // Check thresholds
            if (aiMetrics.latencyAlertThreshold() > 0 &&
                duration > aiMetrics.latencyAlertThreshold()) {
                log.warn("Latency threshold exceeded: {}ms > {}ms for metric: {}",
                    duration, aiMetrics.latencyAlertThreshold(), metricName);
                sendAlert(aiMetrics.alertEmail(),
                    "Latency threshold exceeded: " + duration + "ms");
            }
        }
    }

    /**
     * Tracks operation latency.
     */
    private void trackLatency(String metricName, long durationMs, AiMetrics aiMetrics) {
        Timer.builder(metricName + ".latency")
            .description("AI operation latency")
            .tags(parseTags(aiMetrics.tags()))
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Tracks success/failure.
     */
    private void trackSuccess(String metricName, boolean success, AiMetrics aiMetrics) {
        Counter.builder(metricName + ".total")
            .description("AI operation count")
            .tags(parseTags(aiMetrics.tags()))
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .increment();
    }

    /**
     * Parses tag array into tag pairs.
     */
    private String[] parseTags(String[] tagArray) {
        // Simple implementation - convert "key=value" to ["key", "value"]
        // In production, handle parameter substitution
        return tagArray;
    }

    /**
     * Sends alert email (placeholder).
     */
    private void sendAlert(String email, String message) {
        if (email != null && !email.isEmpty()) {
            log.warn("ALERT to {}: {}", email, message);
            // In production, integrate with email service
        }
    }
}

