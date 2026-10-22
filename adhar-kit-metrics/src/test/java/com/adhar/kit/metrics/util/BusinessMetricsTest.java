package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.Tags;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The per-application dashboard discovers business metrics by the Prometheus name prefix
 * {@code adhar_business_}; every helper must produce it.
 */
class BusinessMetricsTest {

    private PrometheusMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AdharMetricsProperties properties = new AdharMetricsProperties();
        AdharMetrics.initialize(registry, properties, new MetricsUtils(registry, properties), null);
    }

    @Test
    void namesAlwaysCarryThePrefixExactlyOnce() {
        assertThat(AdharMetrics.businessName("orders.placed")).isEqualTo("adhar.business.orders.placed");
        assertThat(AdharMetrics.businessName("adhar.business.orders.placed")).isEqualTo("adhar.business.orders.placed");
        assertThatThrownBy(() -> AdharMetrics.businessName(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void helpersProduceTheNamesTheDashboardDiscovers() {
        AdharMetrics.businessCounter("payments", "billing", "success", "channel", "web").increment();
        AdharMetrics.businessTimer("checkout").record(Duration.ofMillis(20));
        AdharMetrics.businessSummary("order.value").record(42.5);
        AtomicInteger depth = new AtomicInteger(3);
        AdharMetrics.businessGauge("queue.depth", depth, AtomicInteger::get);
        AdharMetrics.recordBusiness("created", "order", 1.0);

        assertThat(registry.get("adhar.business.payments").counter().getId().getTags())
                .containsAll(Tags.of("category", "billing", "outcome", "success", "channel", "web"));

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("adhar_business_payments_total{category=\"billing\",channel=\"web\",outcome=\"success\"} 1.0")
                .contains("adhar_business_checkout_seconds_count")
                .contains("adhar_business_order_value_sum")
                .contains("adhar_business_queue_depth 3.0")
                .contains("adhar_business_events_total");
    }
}
