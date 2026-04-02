package com.adhar.kit.profiler.endpoint;

import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Spring Boot Actuator endpoint exposing profiling data at {@code /actuator/profiling}.
 *
 * <ul>
 *   <li>GET /actuator/profiling - full profiling report</li>
 *   <li>GET /actuator/profiling/hotspots?top=10 - top N hotspots</li>
 *   <li>GET /actuator/profiling/memory - JVM memory snapshot</li>
 *   <li>DELETE /actuator/profiling - reset profiling statistics</li>
 * </ul>
 */
@Endpoint(id = "profiling")
public class ProfilingActuatorEndpoint {

    private final ProfilingRegistry profilingRegistry;
    private final MemoryProfiler memoryProfiler;

    public ProfilingActuatorEndpoint(ProfilingRegistry profilingRegistry, MemoryProfiler memoryProfiler) {
        this.profilingRegistry = profilingRegistry;
        this.memoryProfiler = memoryProfiler;
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
     * Supported sections: "hotspots", "memory".
     */
    @ReadOperation
    public Object getSection(@Selector String section, @Nullable Integer top) {
        return switch (section) {
            case "hotspots" -> {
                int topN = (top != null && top > 0) ? top : 10;
                yield Map.of("hotspots", profilingRegistry.getHotspots(topN));
            }
            case "memory" -> memoryProfiler.getMemorySnapshot();
            default -> Map.of("error", "Unknown section: " + section);
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
}
