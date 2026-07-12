package com.adhar.kit.ai.component;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiMetricsCollector} using a real in-memory meter registry.
 */
class AiMetricsCollectorTest {

    private SimpleMeterRegistry registry;
    private AiMetricsCollector collector;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new AiMetricsCollector(registry);
    }

    @Test
    void recordChatRequestIncrementsCountersAndTokens() {
        collector.recordChatRequest("openai", "gpt-4", Duration.ofMillis(120), 50);
        collector.recordChatRequest("openai", "gpt-4", Duration.ofMillis(80), 30);

        AiMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertThat(summary.chatRequests()).isEqualTo(2);
        assertThat(summary.totalTokens()).isEqualTo(80);
        assertThat(registry.find("ai.chat.requests").tag("provider", "openai").counter()).isNotNull();
    }

    @Test
    void recordEmbeddingRequestTracked() {
        collector.recordEmbeddingRequest("openai", Duration.ofMillis(15), 256);

        assertThat(collector.getSummary().embeddingRequests()).isEqualTo(1);
        assertThat(registry.find("ai.embedding.requests").tag("provider", "openai").counter()).isNotNull();
    }

    @Test
    void recordRagRequestTracked() {
        collector.recordRagRequest("kb-docs", Duration.ofMillis(200), 3);

        assertThat(collector.getSummary().ragRequests()).isEqualTo(1);
        assertThat(registry.find("ai.rag.requests").tag("knowledge_base", "kb-docs").counter()).isNotNull();
    }

    @Test
    void recordErrorTracked() {
        collector.recordError("timeout", "openai", "chat");

        assertThat(collector.getSummary().errors()).isEqualTo(1);
        assertThat(registry.find("ai.errors").tag("type", "timeout").counter()).isNotNull();
    }

    @Test
    void recordRateLimitExceededTracked() {
        collector.recordRateLimitExceeded("user-1");

        assertThat(registry.find("ai.rate_limit.exceeded").tag("identifier", "user-1").counter()).isNotNull();
    }

    @Test
    void recordCostAccumulatesIntoSummary() {
        collector.recordChatRequest("openai", "gpt-4", Duration.ofMillis(10), 100);
        collector.recordCost(0.25, 100, "openai", "gpt-4");

        AiMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertThat(summary.totalCostUsd()).isEqualTo(0.25);
        assertThat(summary.totalTokens()).isEqualTo(100);
    }

    @Test
    void recordCacheEventHitAndMiss() {
        collector.recordCacheEvent(true, "chat");
        collector.recordCacheEvent(false, "chat");

        assertThat(registry.find("ai.cache.events").tag("result", "hit").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("ai.cache.events").tag("result", "miss").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordVectorStoreOperationTracked() {
        collector.recordVectorStoreOperation("add", Duration.ofMillis(40), 5);

        assertThat(registry.find("ai.vector_store.documents").tag("operation", "add").counter().count())
                .isEqualTo(5.0);
        assertThat(registry.find("ai.vector_store.operation.time").tag("operation", "add").timer()).isNotNull();
    }

    @Test
    void emptySummaryHasZeros() {
        AiMetricsCollector.MetricsSummary summary = collector.getSummary();
        assertThat(summary.chatRequests()).isZero();
        assertThat(summary.embeddingRequests()).isZero();
        assertThat(summary.ragRequests()).isZero();
        assertThat(summary.errors()).isZero();
        assertThat(summary.totalTokens()).isZero();
        assertThat(summary.totalCostUsd()).isZero();
    }
}
