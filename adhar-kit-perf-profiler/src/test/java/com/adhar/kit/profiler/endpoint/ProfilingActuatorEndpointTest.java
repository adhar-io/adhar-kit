package com.adhar.kit.profiler.endpoint;

import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilingActuatorEndpointTest {

    private ProfilingRegistry registry;
    private MemoryProfiler memoryProfiler;
    private ProfilingActuatorEndpoint endpoint;

    @BeforeEach
    void setUp() {
        registry = new ProfilingRegistry();
        memoryProfiler = new MemoryProfiler();
        endpoint = new ProfilingActuatorEndpoint(registry, memoryProfiler);
    }

    private void record(String method, String clazz, long ms, boolean success) {
        registry.record(new ProfilingResult(method, clazz, ms, success,
                success ? null : "RuntimeException", Instant.now()));
    }

    @Test
    @DisplayName("getReport returns the registry's aggregated report")
    void getReportReturnsReport() {
        record("a", "svc", 10L, true);

        ProfilingReport report = endpoint.getReport();

        assertThat(report).isNotNull();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
        assertThat(report.methodCallCounts()).containsKey("svc.a");
    }

    @Test
    @DisplayName("getSection('hotspots') returns hotspots with default top=10 when top is null")
    @SuppressWarnings("unchecked")
    void getSectionHotspotsDefaultTop() {
        record("slow", "svc", 100L, true);
        record("fast", "svc", 1L, true);

        Object result = endpoint.getSection("hotspots", null);

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsKey("hotspots");
        List<MethodProfile> hotspots = (List<MethodProfile>) map.get("hotspots");
        assertThat(hotspots).hasSize(2);
        assertThat(hotspots.getFirst().name()).isEqualTo("svc.slow");
    }

    @Test
    @DisplayName("getSection('hotspots') honours an explicit positive top value")
    @SuppressWarnings("unchecked")
    void getSectionHotspotsExplicitTop() {
        record("a", "svc", 30L, true);
        record("b", "svc", 20L, true);
        record("c", "svc", 10L, true);

        Object result = endpoint.getSection("hotspots", 1);

        Map<String, Object> map = (Map<String, Object>) result;
        List<MethodProfile> hotspots = (List<MethodProfile>) map.get("hotspots");
        assertThat(hotspots).hasSize(1);
        assertThat(hotspots.getFirst().name()).isEqualTo("svc.a");
    }

    @Test
    @DisplayName("getSection('hotspots') with non-positive top falls back to default 10")
    @SuppressWarnings("unchecked")
    void getSectionHotspotsNonPositiveTop() {
        for (int i = 0; i < 12; i++) {
            record("m" + i, "svc", i + 1, true);
        }

        Object result = endpoint.getSection("hotspots", 0);

        Map<String, Object> map = (Map<String, Object>) result;
        List<MethodProfile> hotspots = (List<MethodProfile>) map.get("hotspots");
        assertThat(hotspots).hasSize(10);
    }

    @Test
    @DisplayName("getSection('memory') returns a memory snapshot")
    void getSectionMemory() {
        Object result = endpoint.getSection("memory", null);

        assertThat(result).isInstanceOf(MemorySnapshot.class);
        MemorySnapshot snapshot = (MemorySnapshot) result;
        assertThat(snapshot.capturedAt()).isNotNull();
    }

    @Test
    @DisplayName("getSection with unknown section returns an error map")
    @SuppressWarnings("unchecked")
    void getSectionUnknown() {
        Object result = endpoint.getSection("bogus", null);

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("error")).isEqualTo("Unknown section: bogus");
    }

    @Test
    @DisplayName("resetStats clears the registry and returns a status message")
    void resetStatsClearsRegistry() {
        record("a", "svc", 10L, true);
        assertThat(registry.getReport().totalProfiledCalls()).isEqualTo(1);

        Map<String, String> response = endpoint.resetStats();

        assertThat(response.get("status")).isEqualTo("Profiling statistics reset successfully");
        assertThat(registry.getReport().totalProfiledCalls()).isZero();
    }

    @Test
    @DisplayName("getSection('percentiles') exposes per-method avg/min/max/p95/p99")
    @SuppressWarnings("unchecked")
    void getSectionPercentiles() {
        record("a", "svc", 10L, true);
        record("a", "svc", 20L, true);
        record("a", "svc", 30L, true);

        Object result = endpoint.getSection("percentiles", null);

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsKey("percentiles");
        Map<String, ProfilingReport.MethodStats> percentiles =
                (Map<String, ProfilingReport.MethodStats>) map.get("percentiles");
        assertThat(percentiles).containsKey("svc.a");
        ProfilingReport.MethodStats stats = percentiles.get("svc.a");
        assertThat(stats.averageMs()).isEqualTo(20.0);
        assertThat(stats.p95Ms()).isGreaterThan(0);
        assertThat(stats.p99Ms()).isGreaterThan(0);
    }

    @Test
    @DisplayName("getSection('windows') exposes the current report plus bounded window history")
    @SuppressWarnings("unchecked")
    void getSectionWindows() {
        record("a", "svc", 10L, true);

        Object result = endpoint.getSection("windows", null);

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsKeys("current", "history");
        assertThat(map.get("current")).isInstanceOf(ProfilingReport.class);
        assertThat(map.get("history")).isInstanceOf(java.util.List.class);
        assertThat(((ProfilingReport) map.get("current")).totalProfiledCalls()).isEqualTo(1);
    }
}
