package com.adhar.kit.profiler;

import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.model.MemorySnapshot;
import com.adhar.kit.profiler.model.MethodProfile;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilerFacadeTest {

    private ProfilingRegistry registry;
    private MemoryProfiler memoryProfiler;
    private ProfilerFacade facade;

    @BeforeEach
    void setUp() {
        registry = new ProfilingRegistry();
        memoryProfiler = new MemoryProfiler();
        facade = new ProfilerFacade(registry, memoryProfiler);
    }

    @Test
    @DisplayName("getInstance() returns same singleton instance on repeated calls")
    void getInstanceReturnsSameInstance() {
        ProfilerFacade first = ProfilerFacade.getInstance();
        ProfilerFacade second = ProfilerFacade.getInstance();

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("profile(name, supplier) records timing and returns result")
    void profileSupplierRecordsTiming() {
        String result = facade.profile("testOp", () -> {
            sleep(10);
            return "hello";
        });

        assertThat(result).isEqualTo("hello");

        ProfilingReport report = facade.getReport();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
        assertThat(report.methodCallCounts()).containsKey("manual.testOp");
    }

    @Test
    @DisplayName("profile(name, runnable) executes the operation and records it")
    void profileRunnableExecutesAndRecords() {
        AtomicBoolean executed = new AtomicBoolean(false);

        facade.profile("voidOp", () -> {
            sleep(5);
            executed.set(true);
        });

        assertThat(executed).isTrue();

        ProfilingReport report = facade.getReport();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("profile propagates exceptions from supplier")
    void profilePropagatesSupplierException() {
        assertThatThrownBy(() ->
                facade.profile("failOp", () -> {
                    throw new IllegalStateException("test error");
                })
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("test error");

        // The failed call should still be recorded
        ProfilingReport report = facade.getReport();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
        assertThat(report.errorRates().get("manual.failOp")).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("profile propagates exceptions from runnable")
    void profilePropagatesRunnableException() {
        assertThatThrownBy(() ->
                facade.profile("failRunnable", (Runnable) () -> {
                    throw new RuntimeException("runnable error");
                })
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("runnable error");
    }

    @Test
    @DisplayName("getReport returns a non-null ProfilingReport")
    void getReportReturnsNonNull() {
        ProfilingReport report = facade.getReport();

        assertThat(report).isNotNull();
        assertThat(report.windowStart()).isNotNull();
        assertThat(report.windowEnd()).isNotNull();
        assertThat(report.topSlowestMethods()).isNotNull();
    }

    @Test
    @DisplayName("getHotspots returns top N slowest methods")
    void getHotspotsReturnsTopN() {
        facade.profile("fast", () -> "fast");
        facade.profile("slow", () -> {
            sleep(50);
            return "slow";
        });
        facade.profile("medium", () -> {
            sleep(20);
            return "medium";
        });

        List<MethodProfile> hotspots = facade.getHotspots(2);

        assertThat(hotspots).hasSizeLessThanOrEqualTo(2);
        assertThat(hotspots).isNotEmpty();
        // The first hotspot should be the slowest
        assertThat(hotspots.getFirst().averageTimeMs())
                .isGreaterThanOrEqualTo(hotspots.getLast().averageTimeMs());
    }

    @Test
    @DisplayName("getMemorySnapshot returns a non-null snapshot")
    void getMemorySnapshotReturnsNonNull() {
        MemorySnapshot snapshot = facade.getMemorySnapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.capturedAt()).isNotNull();
    }

    @Test
    @DisplayName("getHeapUsageMb returns a positive value")
    void getHeapUsageMbReturnsPositive() {
        double heapMb = facade.getHeapUsageMb();

        assertThat(heapMb).isPositive();
    }

    @Test
    @DisplayName("getThreadCount returns a positive value")
    void getThreadCountReturnsPositive() {
        int threadCount = facade.getThreadCount();

        assertThat(threadCount).isPositive();
    }

    @Test
    @DisplayName("reset clears all profiling data")
    void resetClearsAllData() {
        facade.profile("op1", () -> "a");
        facade.profile("op2", () -> "b");

        assertThat(facade.getReport().totalProfiledCalls()).isEqualTo(2);

        facade.reset();

        ProfilingReport report = facade.getReport();
        assertThat(report.totalProfiledCalls()).isZero();
        assertThat(report.methodCallCounts()).isEmpty();
    }

    @Test
    @DisplayName("isEnabled returns true by default")
    void isEnabledDefaultsToTrue() {
        assertThat(facade.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("setEnabled(false) disables profiling - calls still execute but are not recorded")
    void setEnabledControlsProfiling() {
        facade.setEnabled(false);
        assertThat(facade.isEnabled()).isFalse();

        // Operation should still execute
        String result = facade.profile("skipped", () -> "value");
        assertThat(result).isEqualTo("value");

        // But it should not be recorded
        assertThat(facade.getReport().totalProfiledCalls()).isZero();

        // Re-enable
        facade.setEnabled(true);
        assertThat(facade.isEnabled()).isTrue();

        facade.profile("recorded", () -> "v2");
        assertThat(facade.getReport().totalProfiledCalls()).isEqualTo(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
