package com.adhar.kit.metrics.auto;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link JvmMetricsCollector}.
 * <p>
 * The collector registers {@code adhar.jvm.*} gauges and performs an initial
 * snapshot in its constructor. These tests assert the gauges exist and carry
 * sensible values, then drive an additional collection cycle (with primed GC
 * deltas) via reflection to exercise the GC pause-recording branch.
 * </p>
 */
class JvmMetricsCollectorTest {

    private SimpleMeterRegistry registry;
    private JvmMetricsCollector collector;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new JvmMetricsCollector(registry);
    }

    @AfterEach
    void tearDown() {
        collector.shutdown();
    }

    @Test
    void constructor_registersAllCoreGauges() {
        assertThat(registry.find("adhar.jvm.memory.heap.used").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.memory.heap.committed").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.memory.heap.max").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.memory.nonheap.used").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.memory.nonheap.committed").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.threads.live").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.threads.daemon").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.threads.peak").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.classes.loaded").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.classes.unloaded").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.classes.loaded.total").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.cpu.processors").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.cpu.system_load_average").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.file_descriptors.open").gauge()).isNotNull();
        assertThat(registry.find("adhar.jvm.file_descriptors.max").gauge()).isNotNull();
    }

    @Test
    void initialSnapshot_populatesPositiveMemoryAndThreadValues() {
        Gauge heapUsed = registry.find("adhar.jvm.memory.heap.used").gauge();
        Gauge liveThreads = registry.find("adhar.jvm.threads.live").gauge();
        Gauge processors = registry.find("adhar.jvm.cpu.processors").gauge();

        assertThat(heapUsed.value()).isGreaterThan(0.0);
        assertThat(liveThreads.value()).isGreaterThan(0.0);
        assertThat(processors.value()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void collectOnce_withPrimedGcDeltas_recordsGcPauseTimer() throws Exception {
        // Prime previous GC counters with negative values so the next collection
        // observes a positive delta and records a pause timer for each collector.
        setLongArrayToNegative("prevGcTimes");
        setLongArrayToNegative("prevGcCounts");

        invokePrivate("collectOnce");

        assertThat(registry.find("adhar.jvm.gc.pause").timers()).isNotEmpty();
    }

    @Test
    void collectSafely_runsWithoutThrowing() {
        assertThatCode(() -> invokePrivate("collectSafely")).doesNotThrowAnyException();
    }

    @Test
    void shutdown_isIdempotentEnoughToCallTwice() {
        assertThatCode(() -> {
            collector.shutdown();
            collector.shutdown();
        }).doesNotThrowAnyException();
    }

    // ---- reflection helpers ------------------------------------------------

    private void setLongArrayToNegative(String fieldName) throws Exception {
        Field f = JvmMetricsCollector.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        long[] arr = (long[]) f.get(collector);
        for (int i = 0; i < arr.length; i++) {
            arr[i] = -1_000_000L;
        }
    }

    private void invokePrivate(String methodName) {
        try {
            Method m = JvmMetricsCollector.class.getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(collector);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
