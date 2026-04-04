package com.adhar.kit.metrics.auto;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects JVM-level metrics using {@link ManagementFactory} MXBeans and publishes
 * them to a Micrometer {@link MeterRegistry} on a fixed 15-second schedule.
 * <p>
 * While Spring Boot Actuator / Micrometer binders provide similar metrics out of the box,
 * this collector uses Adhar-prefixed metric names ({@code adhar.jvm.*}) so that all
 * platform metrics live under a single namespace for unified dashboards.
 * </p>
 *
 * <p><b>Metrics collected:</b></p>
 * <ul>
 *   <li>Heap memory -- used, committed, max</li>
 *   <li>Non-heap memory -- used, committed</li>
 *   <li>GC pause times -- per collector</li>
 *   <li>Thread counts -- total, daemon, peak</li>
 *   <li>Class loading -- loaded, unloaded, total loaded</li>
 *   <li>CPU usage -- system load average, available processors</li>
 *   <li>File descriptors -- open, max (on supported JVMs)</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class JvmMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(JvmMetricsCollector.class);
    private static final long COLLECTION_INTERVAL_SECONDS = 15;

    private final MeterRegistry registry;
    private final ScheduledExecutorService scheduler;

    // Gauge holders
    private final AtomicLong heapUsed = new AtomicLong();
    private final AtomicLong heapCommitted = new AtomicLong();
    private final AtomicLong heapMax = new AtomicLong();
    private final AtomicLong nonHeapUsed = new AtomicLong();
    private final AtomicLong nonHeapCommitted = new AtomicLong();
    private final AtomicLong threadCount = new AtomicLong();
    private final AtomicLong daemonThreadCount = new AtomicLong();
    private final AtomicLong peakThreadCount = new AtomicLong();
    private final AtomicLong loadedClassCount = new AtomicLong();
    private final AtomicLong unloadedClassCount = new AtomicLong();
    private final AtomicLong totalLoadedClassCount = new AtomicLong();
    private final AtomicLong availableProcessors = new AtomicLong();
    private final AtomicLong systemLoadAverage = new AtomicLong(); // stored as millis for precision
    private final AtomicLong openFileDescriptors = new AtomicLong();
    private final AtomicLong maxFileDescriptors = new AtomicLong();

    // MXBeans
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final ClassLoadingMXBean classLoadingBean;
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    // Track previous GC times for delta computation
    private long[] prevGcTimes;
    private long[] prevGcCounts;

    /**
     * Constructs a JvmMetricsCollector that registers gauges and starts the
     * scheduled collection loop.
     *
     * @param registry the Micrometer MeterRegistry
     */
    public JvmMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.classLoadingBean = ManagementFactory.getClassLoadingMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        this.prevGcTimes = new long[gcBeans.size()];
        this.prevGcCounts = new long[gcBeans.size()];

        registerGauges();
        collectOnce(); // initial snapshot

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "adhar-jvm-metrics-collector");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::collectSafely,
                COLLECTION_INTERVAL_SECONDS, COLLECTION_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("JvmMetricsCollector started -- collecting every {} seconds", COLLECTION_INTERVAL_SECONDS);
    }

    /**
     * Stops the scheduled collection. Called during application shutdown.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("JvmMetricsCollector stopped");
    }

    // -------------------------------------------------------------------------
    // Gauge Registration
    // -------------------------------------------------------------------------

    private void registerGauges() {
        // Memory
        gauge("adhar.jvm.memory.heap.used", "Heap memory used (bytes)", heapUsed);
        gauge("adhar.jvm.memory.heap.committed", "Heap memory committed (bytes)", heapCommitted);
        gauge("adhar.jvm.memory.heap.max", "Heap memory max (bytes)", heapMax);
        gauge("adhar.jvm.memory.nonheap.used", "Non-heap memory used (bytes)", nonHeapUsed);
        gauge("adhar.jvm.memory.nonheap.committed", "Non-heap memory committed (bytes)", nonHeapCommitted);

        // Threads
        gauge("adhar.jvm.threads.live", "Current thread count", threadCount);
        gauge("adhar.jvm.threads.daemon", "Daemon thread count", daemonThreadCount);
        gauge("adhar.jvm.threads.peak", "Peak thread count", peakThreadCount);

        // Class loading
        gauge("adhar.jvm.classes.loaded", "Currently loaded classes", loadedClassCount);
        gauge("adhar.jvm.classes.unloaded", "Total unloaded classes", unloadedClassCount);
        gauge("adhar.jvm.classes.loaded.total", "Total loaded classes since JVM start", totalLoadedClassCount);

        // CPU
        gauge("adhar.jvm.cpu.processors", "Available processors", availableProcessors);
        Gauge.builder("adhar.jvm.cpu.system_load_average", systemLoadAverage, v -> v.doubleValue() / 1000.0)
                .description("System load average")
                .register(registry);

        // File descriptors
        gauge("adhar.jvm.file_descriptors.open", "Open file descriptors", openFileDescriptors);
        gauge("adhar.jvm.file_descriptors.max", "Max file descriptors", maxFileDescriptors);
    }

    private void gauge(String name, String description, AtomicLong holder) {
        Gauge.builder(name, holder, AtomicLong::doubleValue)
                .description(description)
                .register(registry);
    }

    // -------------------------------------------------------------------------
    // Collection
    // -------------------------------------------------------------------------

    private void collectSafely() {
        try {
            collectOnce();
        } catch (Exception e) {
            log.warn("Error collecting JVM metrics", e);
        }
    }

    private void collectOnce() {
        collectMemory();
        collectThreads();
        collectClassLoading();
        collectCpu();
        collectGc();
        collectFileDescriptors();
    }

    private void collectMemory() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        heapUsed.set(heap.getUsed());
        heapCommitted.set(heap.getCommitted());
        heapMax.set(heap.getMax());

        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        nonHeapUsed.set(nonHeap.getUsed());
        nonHeapCommitted.set(nonHeap.getCommitted());
    }

    private void collectThreads() {
        threadCount.set(threadBean.getThreadCount());
        daemonThreadCount.set(threadBean.getDaemonThreadCount());
        peakThreadCount.set(threadBean.getPeakThreadCount());
    }

    private void collectClassLoading() {
        loadedClassCount.set(classLoadingBean.getLoadedClassCount());
        unloadedClassCount.set(classLoadingBean.getUnloadedClassCount());
        totalLoadedClassCount.set(classLoadingBean.getTotalLoadedClassCount());
    }

    private void collectCpu() {
        availableProcessors.set(osBean.getAvailableProcessors());
        double loadAvg = osBean.getSystemLoadAverage();
        systemLoadAverage.set(loadAvg >= 0 ? (long) (loadAvg * 1000) : -1000);
    }

    private void collectGc() {
        for (int i = 0; i < gcBeans.size(); i++) {
            GarbageCollectorMXBean gc = gcBeans.get(i);
            long currentTime = gc.getCollectionTime();
            long currentCount = gc.getCollectionCount();

            long deltaTime = currentTime - prevGcTimes[i];
            long deltaCount = currentCount - prevGcCounts[i];

            if (deltaCount > 0 && deltaTime > 0) {
                long avgPauseMs = deltaTime / deltaCount;
                Timer.builder("adhar.jvm.gc.pause")
                        .description("GC pause duration")
                        .tag("collector", gc.getName())
                        .register(registry)
                        .record(Duration.ofMillis(avgPauseMs));
            }

            prevGcTimes[i] = currentTime;
            prevGcCounts[i] = currentCount;
        }
    }

    private void collectFileDescriptors() {
        try {
            if (osBean instanceof com.sun.management.UnixOperatingSystemMXBean unixBean) {
                openFileDescriptors.set(unixBean.getOpenFileDescriptorCount());
                maxFileDescriptors.set(unixBean.getMaxFileDescriptorCount());
            }
        } catch (Exception e) {
            // Not on a Unix-like OS or com.sun.management not available -- silently skip
        }
    }
}
