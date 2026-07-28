package com.adhar.kit.profiler.endpoint;

import com.adhar.kit.profiler.contention.ThreadContentionCollector;
import com.adhar.kit.profiler.jfr.FlameGraphExporter;
import com.adhar.kit.profiler.jfr.JfrRecordingManager;
import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.model.WindowSnapshot;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Boot Actuator endpoint exposing profiling data at {@code /actuator/profiling}.
 *
 * <ul>
 *   <li>GET /actuator/profiling - full profiling report</li>
 *   <li>GET /actuator/profiling/hotspots?top=10 - top N hotspots</li>
 *   <li>GET /actuator/profiling/memory - JVM memory snapshot</li>
 *   <li>GET /actuator/profiling/percentiles - per-method avg/min/max/p95/p99 for the current window</li>
 *   <li>GET /actuator/profiling/windows - current window plus bounded rolling-window history</li>
 *   <li>GET /actuator/profiling/jfr - continuous JFR recording status and retained dumps</li>
 *   <li>GET /actuator/profiling/flamegraph - latest JFR dump as collapsed-stack text</li>
 *   <li>GET /actuator/profiling/contention?top=10 - top N contended threads</li>
 *   <li>POST /actuator/profiling/jfr-start|jfr-stop|jfr-dump - control the JFR recording</li>
 *   <li>DELETE /actuator/profiling - reset profiling statistics</li>
 * </ul>
 */
@Endpoint(id = "profiling")
public class ProfilingActuatorEndpoint {

    private final ProfilingRegistry profilingRegistry;
    private final MemoryProfiler memoryProfiler;
    private final JfrRecordingManager jfrRecordingManager;
    private final FlameGraphExporter flameGraphExporter;
    private final ThreadContentionCollector threadContentionCollector;

    public ProfilingActuatorEndpoint(ProfilingRegistry profilingRegistry,
                                     MemoryProfiler memoryProfiler,
                                     JfrRecordingManager jfrRecordingManager,
                                     FlameGraphExporter flameGraphExporter,
                                     ThreadContentionCollector threadContentionCollector) {
        this.profilingRegistry = profilingRegistry;
        this.memoryProfiler = memoryProfiler;
        this.jfrRecordingManager = jfrRecordingManager;
        this.flameGraphExporter = flameGraphExporter;
        this.threadContentionCollector = threadContentionCollector;
    }

    /**
     * GET /actuator/profiling - returns the full profiling report.
     */
    @ReadOperation
    public ProfilingReport getReport() {
        return profilingRegistry.getReport();
    }

    /**
     * GET /actuator/profiling/{section} - returns specific profiling data.
     * Supported sections: "hotspots", "memory", "percentiles", "windows", "jfr",
     * "flamegraph", "contention".
     */
    @ReadOperation
    public Object getSection(@Selector String section, @Nullable Integer top) {
        return switch (section) {
            case "hotspots" -> {
                int topN = (top != null && top > 0) ? top : 10;
                yield Map.of("hotspots", profilingRegistry.getHotspots(topN));
            }
            case "memory" -> memoryProfiler.getMemorySnapshot();
            case "percentiles" -> Map.of("percentiles", profilingRegistry.getReport().methodStatistics());
            case "windows" -> {
                List<WindowSnapshot> history = profilingRegistry.getWindowHistory();
                yield Map.of(
                        "current", profilingRegistry.getReport(),
                        "history", history
                );
            }
            case "jfr" -> jfrRecordingManager.status();
            case "flamegraph" -> exportFlameGraph();
            case "contention" -> threadContentionCollector.snapshot(top);
            default -> Map.of("error", "Unknown section: " + section);
        };
    }

    /**
     * POST /actuator/profiling/{action} - controls the continuous JFR recording.
     * Supported actions: "jfr-start", "jfr-stop", "jfr-dump".
     */
    @WriteOperation
    public Map<String, Object> control(@Selector String action) {
        return switch (action) {
            case "jfr-start" -> {
                boolean started = jfrRecordingManager.start();
                yield Map.of(
                        "status", started
                                ? "JFR recording started"
                                : "JFR recording not started (already running or JFR unavailable)",
                        "jfr", jfrRecordingManager.status());
            }
            case "jfr-stop" -> {
                boolean stopped = jfrRecordingManager.stop();
                yield Map.of(
                        "status", stopped ? "JFR recording stopped" : "No JFR recording was running",
                        "jfr", jfrRecordingManager.status());
            }
            case "jfr-dump" -> jfrRecordingManager.dump()
                    .<Map<String, Object>>map(file -> Map.of(
                            "status", "JFR dump written",
                            "file", file.toString()))
                    .orElseGet(() -> Map.of("error", "No active JFR recording to dump"));
            default -> Map.of("error", "Unknown action: " + action);
        };
    }

    /**
     * DELETE /actuator/profiling - resets all profiling statistics.
     */
    @DeleteOperation
    public Map<String, String> resetStats() {
        profilingRegistry.reset();
        return Map.of("status", "Profiling statistics reset successfully");
    }

    /**
     * Collapses the freshest available JFR dump (dumping the running recording on demand,
     * falling back to the most recent retained dump) into flame-graph text.
     */
    private Object exportFlameGraph() {
        Optional<Path> dumpFile = jfrRecordingManager.dump().or(jfrRecordingManager::latestDump);
        if (dumpFile.isEmpty()) {
            return Map.of("error", "No JFR dump available: start the JFR recording first");
        }
        try {
            return flameGraphExporter.exportCollapsed(dumpFile.get());
        } catch (IOException e) {
            return Map.of("error", "Failed to parse JFR dump " + dumpFile.get() + ": " + e.getMessage());
        }
    }
}
