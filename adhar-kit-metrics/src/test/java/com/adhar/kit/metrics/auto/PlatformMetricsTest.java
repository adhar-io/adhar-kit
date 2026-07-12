package com.adhar.kit.metrics.auto;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural unit tests for {@link PlatformMetrics}.
 */
class PlatformMetricsTest {

    private SimpleMeterRegistry registry;
    private PlatformMetrics platform;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        platform = new PlatformMetrics(registry);
    }

    @Test
    void constructorSetsSingletonAndExposesRegistry() {
        assertThat(PlatformMetrics.getInstance()).isSameAs(platform);
        assertThat(platform.getRegistry()).isSameAs(registry);
    }

    @Test
    void persistenceMetrics() {
        platform.recordQueryLatency("findById", "Order", 45, true);
        assertThat(registry.find("adhar.persistence.query.duration")
                .tag("operation", "findById").timer().count()).isEqualTo(1L);
        assertThat(registry.find("adhar.persistence.query.count").counter().count()).isEqualTo(1.0);

        platform.recordTransactionDuration(20, true);
        platform.recordTransactionDuration(30, false);
        assertThat(registry.find("adhar.persistence.transaction.count")
                .tag("outcome", "rolled_back").counter().count()).isEqualTo(1.0);

        platform.recordConnectionPoolStats(5, 3, 10);
        assertThat(registry.find("adhar.persistence.connections.active").gauge().value()).isEqualTo(5.0);
        assertThat(registry.find("adhar.persistence.connections.total").gauge().value()).isEqualTo(10.0);
        // update existing gauge
        platform.recordConnectionPoolStats(7, 1, 10);
        assertThat(registry.find("adhar.persistence.connections.active").gauge().value()).isEqualTo(7.0);
    }

    @Test
    void cacheMetrics() {
        platform.recordCacheAccess("orders", true);
        platform.recordCacheAccess("orders", false);
        assertThat(registry.find("adhar.cache.access").tag("result", "hit").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("adhar.cache.access").tag("result", "miss").counter().count()).isEqualTo(1.0);

        platform.recordCacheEviction("orders");
        assertThat(registry.find("adhar.cache.eviction").counter().count()).isEqualTo(1.0);

        platform.recordCacheSize("orders", 123);
        assertThat(registry.find("adhar.cache.size.orders").gauge().value()).isEqualTo(123.0);
    }

    @Test
    void messagingMetrics() {
        platform.recordMessagePublished("topic-a", 12);
        assertThat(registry.find("adhar.messaging.publish.count").counter().count()).isEqualTo(1.0);

        platform.recordMessageConsumed("topic-a", 8, true);
        assertThat(registry.find("adhar.messaging.consume.count").counter().count()).isEqualTo(1.0);

        platform.recordMessageFailed("topic-a", "DeserializationError");
        assertThat(registry.find("adhar.messaging.errors").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resilienceMetricsAndCircuitBreakerStates() {
        platform.recordCircuitBreakerState("svc", "OPEN");
        assertThat(registry.find("adhar.resilience.circuit_breaker.svc").gauge().value()).isEqualTo(2.0);
        platform.recordCircuitBreakerState("svc", "HALF_OPEN");
        assertThat(registry.find("adhar.resilience.circuit_breaker.svc").gauge().value()).isEqualTo(1.0);
        platform.recordCircuitBreakerState("svc", "CLOSED");
        assertThat(registry.find("adhar.resilience.circuit_breaker.svc").gauge().value()).isEqualTo(0.0);
        platform.recordCircuitBreakerState("svc", "WEIRD");
        assertThat(registry.find("adhar.resilience.circuit_breaker.svc").gauge().value()).isEqualTo(-1.0);
        // Each state is recorded as a separate counter tagged by state, so sum all
        // transition counters (4 distinct states recorded above) rather than reading one.
        double transitions = registry.find("adhar.resilience.circuit_breaker.transitions").counters()
                .stream().mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
        assertThat(transitions).isEqualTo(4.0);

        platform.recordRetryAttempt("retry", 2, false);
        assertThat(registry.find("adhar.resilience.retry.attempts").counter().count()).isEqualTo(1.0);

        platform.recordRateLimitReject("limiter");
        assertThat(registry.find("adhar.resilience.rate_limit.rejected").counter().count()).isEqualTo(1.0);
    }

    @Test
    void httpMetrics() {
        platform.recordHttpRequest("GET", "/a", 200, 15);
        assertThat(registry.find("adhar.http.request.duration").tag("success", "true").timer().count()).isEqualTo(1L);
        assertThat(registry.find("adhar.http.request.count").counter().count()).isEqualTo(1.0);

        platform.recordHttpRequest("GET", "/a", 500, 15);
        assertThat(registry.find("adhar.http.request.duration").tag("success", "false").timer().count()).isEqualTo(1L);

        platform.recordHttpError("POST", "/b", "NullPointerException");
        assertThat(registry.find("adhar.http.errors").counter().count()).isEqualTo(1.0);
    }

    @Test
    void aiMetrics() {
        platform.recordAiLatency("anthropic", "completion", 250);
        assertThat(registry.find("adhar.ai.operation.count").counter().count()).isEqualTo(1.0);

        platform.recordAiTokens("anthropic", 1500);
        assertThat(registry.find("adhar.ai.tokens.total").counter().count()).isEqualTo(1500.0);
    }

    @Test
    void generalOperationMetricsWithSuccessAndFailure() {
        platform.recordOperationLatency("security", "authenticate", 5, true);
        assertThat(registry.find("adhar.operation.count").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("adhar.operation.errors").counter()).isNull();

        platform.recordOperationLatency("security", "authenticate", 5, false);
        assertThat(registry.find("adhar.operation.errors").counter().count()).isEqualTo(1.0);
    }
}
