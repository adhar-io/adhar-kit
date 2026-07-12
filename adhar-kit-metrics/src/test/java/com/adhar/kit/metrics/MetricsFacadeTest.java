package com.adhar.kit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural unit tests for {@link MetricsFacade}.
 * <p>
 * On the test classpath Spring Boot is present, so the facade detects the
 * SPRING_BOOT framework and delegates to a {@code SpringMetricsAdapter} backed
 * by Micrometer's global registry.
 * </p>
 */
class MetricsFacadeTest {

    @Test
    void getInstanceReturnsSingleton() {
        MetricsFacade first = MetricsFacade.getInstance();
        MetricsFacade second = MetricsFacade.getInstance();
        assertThat(first).isNotNull().isSameAs(second);
    }

    @Test
    void counterAndIncrementDelegate() {
        MetricsFacade metrics = MetricsFacade.getInstance();

        // The facade delegates to Metrics.globalRegistry, a no-op composite that
        // records nothing unless a concrete registry is attached. Attach one so the
        // delegated meters actually accumulate (then detach to avoid leaking state).
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Metrics.addRegistry(reg);
        try {
            Counter counter = metrics.counter("facade.counter." + System.nanoTime(), "k", "v");
            assertThat(counter).isNotNull();

            String name = "facade.increment." + System.nanoTime();
            metrics.increment(name, "k", "v");
            metrics.increment(name, 4.0, "k", "v");
            assertThat(reg.find(name).counter().count()).isEqualTo(5.0);
        } finally {
            Metrics.removeRegistry(reg);
            reg.close();
        }
    }

    @Test
    void timerAndRecordTimeDelegate() {
        MetricsFacade metrics = MetricsFacade.getInstance();

        Timer timer = metrics.timer("facade.timer." + System.nanoTime(), "k", "v");
        assertThat(timer).isNotNull();

        String result = metrics.recordTime("facade.record." + System.nanoTime(), () -> "value");
        assertThat(result).isEqualTo("value");
    }

    @Test
    void gaugeDelegateReturnsSupplierValue() {
        MetricsFacade metrics = MetricsFacade.getInstance();
        Integer value = metrics.gauge("facade.gauge." + System.nanoTime(), () -> 7, "k", "v");
        assertThat(value).isEqualTo(7);
    }
}
