package com.adhar.kit.profiler.endpoint;

import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.contention.ThreadContentionCollector;
import com.adhar.kit.profiler.jfr.FlameGraphExporter;
import com.adhar.kit.profiler.jfr.JfrRecordingManager;
import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.ContentionSnapshot;
import com.adhar.kit.profiler.model.JfrStatus;
import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.ProfilingResult;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProfilingActuatorEndpointTest {

    @TempDir
    Path dumpDir;

    private ProfilingRegistry registry;
    private MemoryProfiler memoryProfiler;
    private JfrRecordingManager jfrRecordingManager;
    private FlameGraphExporter flameGraphExporter;
    private ThreadContentionCollector threadContentionCollector;
    private ProfilingActuatorEndpoint endpoint;

    @BeforeEach
    void setUp() {
        registry = new ProfilingRegistry();
        memoryProfiler = new MemoryProfiler();

        PerfProfilerProperties.Jfr jfrProps = new PerfProfilerProperties.Jfr();
        jfrProps.setDumpDirectory(dumpDir.toString());
        jfrProps.setMaxSizeMb(8);
        jfrRecordingManager = new JfrRecordingManager(jfrProps);

        flameGraphExporter = new FlameGraphExporter();

        threadContentionCollector = new ThreadContentionCollector(new PerfProfilerProperties.Contention());

        endpoint = new ProfilingActuatorEndpoint(registry, memoryProfiler,
                jfrRecordingManager, flameGraphExporter, threadContentionCollector);
    }

    @AfterEach
    void tearDown() {
        jfrRecordingManager.close();
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

    @Test
    @DisplayName("getSection('jfr') returns the recording status")
    void getSectionJfr() {
        Object result = endpoint.getSection("jfr", null);

        assertThat(result).isInstanceOf(JfrStatus.class);
        JfrStatus status = (JfrStatus) result;
        assertThat(status.running()).isFalse();
        assertThat(status.dumpDirectory()).isEqualTo(dumpDir.toString());
        assertThat(status.settings()).isEqualTo("default");
    }

    @Test
    @DisplayName("getSection('contention') returns a contention snapshot with ranked threads")
    void getSectionContention() {
        Object result = endpoint.getSection("contention", 3);

        assertThat(result).isInstanceOf(ContentionSnapshot.class);
        ContentionSnapshot snapshot = (ContentionSnapshot) result;
        assertThat(snapshot.capturedAt()).isNotNull();
        assertThat(snapshot.topContendedThreads()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("getSection('flamegraph') returns an error map when no JFR dump is available")
    @SuppressWarnings("unchecked")
    void getSectionFlamegraphWithoutDump() {
        Object result = endpoint.getSection("flamegraph", null);

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat((String) map.get("error")).contains("No JFR dump available");
    }

    @Test
    @DisplayName("control with unknown action returns an error map")
    void controlUnknownAction() {
        Map<String, Object> result = endpoint.control("bogus");

        assertThat(result.get("error")).isEqualTo("Unknown action: bogus");
    }

    @Test
    @DisplayName("control('jfr-dump') without a running recording reports no active recording")
    void controlDumpWithoutRecording() {
        Map<String, Object> result = endpoint.control("jfr-dump");

        assertThat(result.get("error")).isEqualTo("No active JFR recording to dump");
    }

    @Test
    @DisplayName("control('jfr-stop') without a running recording reports none was running")
    void controlStopWithoutRecording() {
        Map<String, Object> result = endpoint.control("jfr-stop");

        assertThat(result.get("status")).isEqualTo("No JFR recording was running");
        assertThat(result.get("jfr")).isInstanceOf(JfrStatus.class);
    }

    @Test
    @DisplayName("control jfr-start, jfr-dump, flamegraph, then jfr-stop drive the full JFR lifecycle")
    @SuppressWarnings("unchecked")
    void controlJfrLifecycle() throws InterruptedException {
        assumeTrue(jfrRecordingManager.isJfrAvailable(), "JFR is not available in this JVM");

        Map<String, Object> start = endpoint.control("jfr-start");
        assertThat(start.get("status")).isEqualTo("JFR recording started");
        assertThat(((JfrStatus) start.get("jfr")).running()).isTrue();

        // Generate a little CPU work so the recording captures execution samples.
        burnCpu();
        Thread.sleep(200);

        Map<String, Object> dump = endpoint.control("jfr-dump");
        assertThat(dump.get("status")).isEqualTo("JFR dump written");
        assertThat(dump.get("file")).isNotNull();

        // Flame-graph export should now succeed (dumps the live recording) and return text.
        Object flame = endpoint.getSection("flamegraph", null);
        assertThat(flame).isInstanceOf(String.class);

        Map<String, Object> stop = endpoint.control("jfr-stop");
        assertThat(stop.get("status")).isEqualTo("JFR recording stopped");
        assertThat(((JfrStatus) stop.get("jfr")).running()).isFalse();
    }

    @Test
    @DisplayName("control('jfr-start') twice reports the second start as a no-op")
    void controlJfrStartTwice() {
        assumeTrue(jfrRecordingManager.isJfrAvailable(), "JFR is not available in this JVM");

        endpoint.control("jfr-start");
        Map<String, Object> second = endpoint.control("jfr-start");

        assertThat((String) second.get("status")).contains("not started");
    }

    private static void burnCpu() {
        long sink = 0;
        for (int i = 0; i < 5_000_000; i++) {
            sink += (long) Math.sqrt(i) ^ i;
        }
        if (sink == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable");
        }
    }
}
