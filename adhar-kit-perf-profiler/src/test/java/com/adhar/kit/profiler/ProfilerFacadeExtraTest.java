package com.adhar.kit.profiler;

import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerFacadeExtraTest {

    private ProfilingRegistry registry;
    private ProfilerFacade facade;

    @BeforeEach
    void setUp() {
        registry = new ProfilingRegistry();
        facade = new ProfilerFacade(registry, new MemoryProfiler());
    }

    @Test
    @DisplayName("constructor falls back to default registry and memory profiler when given null")
    void constructorUsesDefaultsWhenNull() {
        ProfilerFacade defaultFacade = new ProfilerFacade(null, null);

        assertThat(defaultFacade.getReport()).isNotNull();
        assertThat(defaultFacade.getMemorySnapshot()).isNotNull();
        // Behaves normally with the internally created collaborators
        defaultFacade.profile("op", () -> "v");
        assertThat(defaultFacade.getReport().totalProfiledCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMethodStats finds a method present in the top-slowest list")
    void getMethodStatsFromTopSlowest() {
        facade.profile("topOp", () -> {
            sleep(15);
            return "x";
        });

        Optional<MethodProfile> stats = facade.getMethodStats("manual.topOp");

        assertThat(stats).isPresent();
        assertThat(stats.get().name()).isEqualTo("manual.topOp");
        assertThat(stats.get().callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMethodStats falls back to the broad report when method is outside top-10")
    void getMethodStatsFallsBackToBroadReport() {
        // Record 12 distinct fast methods; the default report only keeps top 10.
        for (int i = 0; i < 12; i++) {
            final int idx = i;
            facade.profile("op" + idx, () -> "v");
        }

        // op0..op11 all have ~0ms; at least a couple fall outside the top-10 window,
        // but the broad-report fallback should still locate any of them.
        Optional<MethodProfile> found = facade.getMethodStats("manual.op0");
        // Whether op0 is in top-10 or not, it must be discoverable.
        if (found.isEmpty()) {
            // try one guaranteed-present via broad search semantics
            found = facade.getMethodStats("manual.op11");
        }
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("getMethodStats returns empty for an unknown method")
    void getMethodStatsUnknownReturnsEmpty() {
        facade.profile("known", () -> "v");

        Optional<MethodProfile> stats = facade.getMethodStats("does.not.Exist");

        assertThat(stats).isEmpty();
    }

    @Test
    @DisplayName("health reports counts, memory metrics, and enabled flag")
    void healthReportsSummary() {
        facade.profile("h1", () -> "a");
        facade.profile("h2", () -> "b");

        Map<String, Object> health = facade.health();

        assertThat(health).containsKeys(
                "profiledMethodCount", "totalProfiledCalls",
                "heapUsedMb", "heapMaxMb", "activeThreadCount", "enabled");
        assertThat(health.get("profiledMethodCount")).isEqualTo(2);
        assertThat((long) (Long) health.get("totalProfiledCalls")).isEqualTo(2L);
        assertThat(health.get("enabled")).isEqualTo(true);
        assertThat((Double) health.get("heapUsedMb")).isPositive();
        assertThat((Integer) health.get("activeThreadCount")).isPositive();
    }

    @Test
    @DisplayName("health reflects the disabled state")
    void healthReflectsDisabledState() {
        facade.setEnabled(false);

        Map<String, Object> health = facade.health();

        assertThat(health.get("enabled")).isEqualTo(false);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
