package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Tracks costs and metrics for AI operations.
 *
 * <p>Automatically tracks token usage, costs, latency, and success rates
 * for AI operations.</p>
 *
 * <p><b>Example - Track All Metrics:</b></p>
 * <pre>{@code
 * @Service
 * @AiMetrics(
 *     trackCost = true,
 *     trackLatency = true,
 *     trackTokens = true
 * )
 * public class AiService {
 *
 *     @AiChat(prompt = "Answer: {question}")
 *     public String answer(String question) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Cost Alerts:</b></p>
 * <pre>{@code
 * @Service
 * public class ExpensiveAiService {
 *
 *     @AiMetrics(
 *         trackCost = true,
 *         costAlertThreshold = 0.10,  // Alert if cost > $0.10
 *         alertEmail = "admin@example.com"
 *     )
 *     @AiChat(prompt = "Generate detailed report: {topic}", maxTokens = 4000)
 *     public String generateReport(String topic) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Custom Metrics:</b></p>
 * <pre>{@code
 * @Service
 * public class TranslationService {
 *
 *     @AiMetrics(
 *         metricName = "translation.requests",
 *         tags = {"service=translation", "lang={targetLang}"}
 *     )
 *     @AiChat(prompt = "Translate to {targetLang}: {text}")
 *     public String translate(String text, String targetLang) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiMetrics {

    /**
     * Track token usage.
     */
    boolean trackTokens() default true;

    /**
     * Track cost in USD.
     */
    boolean trackCost() default true;

    /**
     * Track latency in milliseconds.
     */
    boolean trackLatency() default true;

    /**
     * Track success/failure rate.
     */
    boolean trackSuccessRate() default true;

    /**
     * Custom metric name.
     */
    String metricName() default "";

    /**
     * Metric tags (key=value or key={paramName}).
     */
    String[] tags() default {};

    /**
     * Alert threshold for cost (USD).
     */
    double costAlertThreshold() default 0.0;

    /**
     * Alert threshold for latency (ms).
     */
    long latencyAlertThreshold() default 0;

    /**
     * Alert email for threshold violations.
     */
    String alertEmail() default "";

    /**
     * Export metrics to external system.
     */
    boolean exportMetrics() default true;

    /**
     * Metrics export destination.
     */
    String exportDestination() default "prometheus";
}

