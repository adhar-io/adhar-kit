package com.adhar.adharkit.ai.component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics and monitoring component for AI services.
 * Tracks performance, usage, and error metrics for comprehensive observability.
 */
@Slf4j
@Component
public class AiMetricsCollector {

    private final MeterRegistry meterRegistry;

    // Request metrics
    private final Counter chatRequestsTotal;
    private final Counter embeddingRequestsTotal;
    private final Counter ragRequestsTotal;

    // Error metrics
    private final Counter errorsTotal;
    private final Counter rateLimitExceeded;

    // Performance metrics
    private final Timer chatResponseTime;
    private final Timer embeddingResponseTime;
    private final Timer ragResponseTime;

    // Usage metrics
    private final AtomicLong totalTokensUsed;
    private final AtomicLong totalCostUsd;

    public AiMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize request metrics
        this.chatRequestsTotal = Counter.builder("ai.chat.requests.total")
                .description("Total number of chat requests")
                .register(meterRegistry);

        this.embeddingRequestsTotal = Counter.builder("ai.embedding.requests.total")
                .description("Total number of embedding requests")
                .register(meterRegistry);

        this.ragRequestsTotal = Counter.builder("ai.rag.requests.total")
                .description("Total number of RAG requests")
                .register(meterRegistry);

        // Initialize error metrics
        this.errorsTotal = Counter.builder("ai.errors.total")
                .description("Total number of AI errors")
                .register(meterRegistry);

        this.rateLimitExceeded = Counter.builder("ai.rate_limit.exceeded")
                .description("Total number of rate limit exceeded errors")
                .register(meterRegistry);

        // Initialize performance metrics
        this.chatResponseTime = Timer.builder("ai.chat.response.time")
                .description("AI chat response time")
                .register(meterRegistry);

        this.embeddingResponseTime = Timer.builder("ai.embedding.response.time")
                .description("AI embedding response time")
                .register(meterRegistry);

        this.ragResponseTime = Timer.builder("ai.rag.response.time")
                .description("AI RAG response time")
                .register(meterRegistry);

        // Initialize usage metrics
        this.totalTokensUsed = new AtomicLong(0);
        this.totalCostUsd = new AtomicLong(0);
    }

    /**
     * Records a successful chat request.
     */
    public void recordChatRequest(String provider, String model, Duration duration, long tokens) {
        chatRequestsTotal.increment();
        chatResponseTime.record(duration);
        totalTokensUsed.addAndGet(tokens);

        // Provider-specific metrics
        Counter.builder("ai.chat.requests")
                .tag("provider", provider)
                .tag("model", model)
                .register(meterRegistry)
                .increment();

        log.debug("Recorded chat request - Provider: {}, Model: {}, Duration: {}ms, Tokens: {}",
                provider, model, duration.toMillis(), tokens);
    }

    /**
     * Records a successful embedding request.
     */
    public void recordEmbeddingRequest(String provider, Duration duration, int inputLength) {
        embeddingRequestsTotal.increment();
        embeddingResponseTime.record(duration);

        Counter.builder("ai.embedding.requests")
                .tag("provider", provider)
                .register(meterRegistry)
                .increment();

        log.debug("Recorded embedding request - Provider: {}, Duration: {}ms, InputLength: {}",
                provider, duration.toMillis(), inputLength);
    }

    /**
     * Records a successful RAG request.
     */
    public void recordRagRequest(String knowledgeBase, Duration duration, int documentsUsed) {
        ragRequestsTotal.increment();

        Counter.builder("ai.rag.requests")
                .tag("knowledge_base", knowledgeBase)
                .register(meterRegistry)
                .increment();

        log.debug("Recorded RAG request - KnowledgeBase: {}, Duration: {}ms, Documents: {}",
                knowledgeBase, duration.toMillis(), documentsUsed);
    }

    /**
     * Records an error.
     */
    public void recordError(String errorType, String provider, String operation) {
        errorsTotal.increment();

        Counter.builder("ai.errors")
                .tag("type", errorType)
                .tag("provider", provider)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();

        log.warn("Recorded AI error - Type: {}, Provider: {}, Operation: {}",
                errorType, provider, operation);
    }

    /**
     * Records rate limit exceeded.
     */
    public void recordRateLimitExceeded(String identifier) {
        rateLimitExceeded.increment();

        Counter.builder("ai.rate_limit.exceeded")
                .tag("identifier", identifier)
                .register(meterRegistry)
                .increment();

        log.warn("Rate limit exceeded for identifier: {}", identifier);
    }

    /**
     * Records cost information.
     */
    public void recordCost(double costUsd, int tokens, String provider, String model) {
        totalCostUsd.addAndGet((long) (costUsd * 1000)); // Store as thousandths of USD

        meterRegistry.gauge("ai.cost.total.usd", totalCostUsd.get() / 1000.0);
        meterRegistry.gauge("ai.tokens.total", totalTokensUsed.get());

        log.debug("Recorded cost - Provider: {}, Model: {}, Cost: ${}, Tokens: {}",
                provider, model, costUsd, tokens);
    }

    /**
     * Records cache hit/miss.
     */
    public void recordCacheEvent(boolean hit, String operation) {
        Counter.builder("ai.cache.events")
                .tag("result", hit ? "hit" : "miss")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();

        log.debug("Cache {} for operation: {}", hit ? "hit" : "miss", operation);
    }

    /**
     * Records vector store operations.
     */
    public void recordVectorStoreOperation(String operation, Duration duration, int documentCount) {
        Timer.builder("ai.vector_store.operation.time")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(duration);

        Counter.builder("ai.vector_store.documents")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment(documentCount);

        log.debug("Recorded vector store {} operation - Duration: {}ms, Documents: {}",
                operation, duration.toMillis(), documentCount);
    }

    /**
     * Gets current metrics summary.
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                (long) chatRequestsTotal.count(),
                (long) embeddingRequestsTotal.count(),
                (long) ragRequestsTotal.count(),
                (long) errorsTotal.count(),
                totalTokensUsed.get(),
                totalCostUsd.get() / 1000.0
        );
    }

    public record MetricsSummary(
            long chatRequests,
            long embeddingRequests,
            long ragRequests,
            long errors,
            long totalTokens,
            double totalCostUsd
    ) {}
}
