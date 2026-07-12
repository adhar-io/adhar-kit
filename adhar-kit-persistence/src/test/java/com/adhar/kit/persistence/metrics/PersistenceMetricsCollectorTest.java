package com.adhar.kit.persistence.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PersistenceMetricsCollector Tests")
class PersistenceMetricsCollectorTest {

    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    @Test
    @DisplayName("records a fast query without flagging it as slow")
    void testRecordFastQuery() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        collector.recordQuery(ms(10));

        QueryStats stats = collector.getStats();
        assertEquals(1, stats.totalQueries());
        assertEquals(0, stats.slowQueryCount());
        assertEquals(10, stats.maxLatencyMs());
        assertEquals(0, stats.errorCount());
        assertTrue(stats.avgLatencyMs() >= 10.0);
    }

    @Test
    @DisplayName("flags queries above the slow threshold")
    void testRecordSlowQuery() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 50);
        collector.recordQuery(ms(100));
        collector.recordQuery(ms(10));

        QueryStats stats = collector.getStats();
        assertEquals(2, stats.totalQueries());
        assertEquals(1, stats.slowQueryCount());
        assertEquals(100, stats.maxLatencyMs());
    }

    @Test
    @DisplayName("tracks max latency across multiple queries")
    void testMaxLatency() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 1000);
        collector.recordQuery(ms(30));
        collector.recordQuery(ms(80));
        collector.recordQuery(ms(20));
        assertEquals(80, collector.getStats().maxLatencyMs());
    }

    @Test
    @DisplayName("recordError increments error count")
    void testRecordError() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        collector.recordError();
        collector.recordError();
        assertEquals(2, collector.getStats().errorCount());
    }

    @Test
    @DisplayName("recordTransaction is a no-op without registry")
    void testRecordTransactionNoRegistry() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        collector.recordTransaction(ms(5));
        // No exception, no query recorded.
        assertEquals(0, collector.getStats().totalQueries());
    }

    @Test
    @DisplayName("recordQueryExecution(Supplier) returns result and records the query")
    void testRecordQueryExecutionSupplierSuccess() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        String result = collector.recordQueryExecution(() -> "ok");
        assertEquals("ok", result);
        assertEquals(1, collector.getStats().totalQueries());
        assertEquals(0, collector.getStats().errorCount());
    }

    @Test
    @DisplayName("recordQueryExecution(Supplier) records error and rethrows on failure")
    void testRecordQueryExecutionSupplierFailure() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        assertThrows(IllegalStateException.class, () ->
                collector.recordQueryExecution((java.util.function.Supplier<String>) () -> {
                    throw new IllegalStateException("fail");
                }));
        QueryStats stats = collector.getStats();
        assertEquals(1, stats.totalQueries());
        assertEquals(1, stats.errorCount());
    }

    @Test
    @DisplayName("recordQueryExecution(Runnable) runs and records the query")
    void testRecordQueryExecutionRunnableSuccess() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        boolean[] ran = {false};
        collector.recordQueryExecution(() -> ran[0] = true);
        assertTrue(ran[0]);
        assertEquals(1, collector.getStats().totalQueries());
    }

    @Test
    @DisplayName("recordQueryExecution(Runnable) records error and rethrows on failure")
    void testRecordQueryExecutionRunnableFailure() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        assertThrows(RuntimeException.class, () ->
                collector.recordQueryExecution((Runnable) () -> {
                    throw new RuntimeException("boom");
                }));
        assertEquals(1, collector.getStats().errorCount());
        assertEquals(1, collector.getStats().totalQueries());
    }

    @Test
    @DisplayName("reset clears all accumulators")
    void testReset() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 50);
        collector.recordQuery(ms(100));
        collector.recordError();
        collector.reset();

        QueryStats stats = collector.getStats();
        assertEquals(0, stats.totalQueries());
        assertEquals(0, stats.slowQueryCount());
        assertEquals(0, stats.maxLatencyMs());
        assertEquals(0, stats.errorCount());
        assertEquals(0.0, stats.avgLatencyMs());
    }

    @Test
    @DisplayName("getStats returns zero average when no queries recorded")
    void testEmptyStats() {
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(null, 500);
        assertEquals(0.0, collector.getStats().avgLatencyMs());
    }

    @Test
    @DisplayName("registers and updates Micrometer instruments when a registry is present")
    void testWithRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(registry, 50);

        collector.recordQuery(ms(100)); // slow -> increments slow query counter + timer
        collector.recordQuery(ms(5));   // fast
        collector.recordTransaction(ms(20));
        collector.recordError();

        assertEquals(2.0, registry.get("adhar.persistence.query.duration").timer().count(), 0.0001);
        assertEquals(1.0, registry.get("adhar.persistence.transaction.duration").timer().count(), 0.0001);
        assertEquals(1.0, registry.get("adhar.persistence.errors").counter().count(), 0.0001);
        assertEquals(1.0, registry.get("adhar.persistence.slow.queries").counter().count(), 0.0001);

        QueryStats stats = collector.getStats();
        assertEquals(2, stats.totalQueries());
        assertEquals(1, stats.slowQueryCount());
        assertEquals(1, stats.errorCount());
    }

    @Test
    @DisplayName("recordQueryExecution with registry records error counter on failure")
    void testRegistryErrorOnExecutionFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PersistenceMetricsCollector collector = new PersistenceMetricsCollector(registry, 500);
        assertThrows(RuntimeException.class, () ->
                collector.recordQueryExecution((Runnable) () -> {
                    throw new RuntimeException("x");
                }));
        assertEquals(1.0, registry.get("adhar.persistence.errors").counter().count(), 0.0001);
    }
}
