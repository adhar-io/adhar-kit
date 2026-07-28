package com.adhar.kit.profiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration properties for the Adhar Kit Performance Profiler.
 */
@ConfigurationProperties(prefix = "adhar.profiler")
public class PerfProfilerProperties {

    private boolean enabled = true;
    private long defaultSlowThresholdMs = 500;
    private boolean logSlowByDefault = true;

    /** Length of the rolling aggregation window before stats roll over into history. */
    private Duration windowDuration = Duration.ofMinutes(5);

    /** Number of completed rolling windows to retain (bounded, oldest evicted first). */
    private int historyWindows = 5;

    /**
     * Fraction of calls (0.0-1.0) that are actually measured and recorded. 1.0 (default)
     * profiles every call. Lower values reduce instrumentation overhead on hot paths by
     * randomly sampling which calls are timed; the underlying method invocation always runs
     * regardless of sampling.
     */
    private double sampleRate = 1.0;

    /**
     * Maximum number of distinct method names tracked in the registry. Once reached, newly
     * seen methods are no longer recorded (a warning is logged once) to guard against unbounded
     * growth from highly dynamic method/metric names.
     */
    private int maxTrackedMethods = 1000;

    /**
     * When greater than zero, publishes a {@code SlowCallThresholdBreachedEvent} whenever a
     * method's aggregate p99 latency crosses this threshold (in milliseconds). Zero or a
     * negative value disables aggregate threshold alerting.
     */
    private long p99AlertThresholdMs = 0;

    /** Continuous JFR recording settings ({@code adhar.profiler.jfr.*}). */
    private final Jfr jfr = new Jfr();

    /** Thread-contention analytics settings ({@code adhar.profiler.contention.*}). */
    private final Contention contention = new Contention();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getDefaultSlowThresholdMs() { return defaultSlowThresholdMs; }
    public void setDefaultSlowThresholdMs(long defaultSlowThresholdMs) { this.defaultSlowThresholdMs = defaultSlowThresholdMs; }
    public boolean isLogSlowByDefault() { return logSlowByDefault; }
    public void setLogSlowByDefault(boolean logSlowByDefault) { this.logSlowByDefault = logSlowByDefault; }

    public Duration getWindowDuration() { return windowDuration; }
    public void setWindowDuration(Duration windowDuration) { this.windowDuration = windowDuration; }

    public int getHistoryWindows() { return historyWindows; }
    public void setHistoryWindows(int historyWindows) { this.historyWindows = historyWindows; }

    public double getSampleRate() { return sampleRate; }
    public void setSampleRate(double sampleRate) {
        if (sampleRate < 0.0) {
            this.sampleRate = 0.0;
        } else if (sampleRate > 1.0) {
            this.sampleRate = 1.0;
        } else {
            this.sampleRate = sampleRate;
        }
    }

    public int getMaxTrackedMethods() { return maxTrackedMethods; }
    public void setMaxTrackedMethods(int maxTrackedMethods) { this.maxTrackedMethods = maxTrackedMethods; }

    public long getP99AlertThresholdMs() { return p99AlertThresholdMs; }
    public void setP99AlertThresholdMs(long p99AlertThresholdMs) { this.p99AlertThresholdMs = p99AlertThresholdMs; }

    public Jfr getJfr() { return jfr; }
    public Contention getContention() { return contention; }

    /**
     * Settings for the continuous Java Flight Recorder recording managed by the profiler.
     */
    public static class Jfr {

        /**
         * Whether the continuous JFR recording is started automatically on startup. When false
         * (default) the recording can still be started on demand through the profiling endpoint.
         */
        private boolean enabled = false;

        /** Name of the JFR settings preset used for the recording ("default" or "profile"). */
        private String settings = "default";

        /** Maximum disk size of the recording buffer in megabytes. Zero or negative disables the limit. */
        private long maxSizeMb = 100;

        /** Maximum age of data kept in the recording buffer. Zero or negative disables the limit. */
        private Duration maxAge = Duration.ofHours(1);

        /** Directory where on-demand recording dumps are written. */
        private String dumpDirectory =
                Path.of(System.getProperty("java.io.tmpdir"), "adhar-profiler-jfr").toString();

        /**
         * Maximum number of dump files retained in the dump directory. When a new dump would
         * exceed this bound, the oldest dumps are deleted first.
         */
        private int maxDumpFiles = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getSettings() { return settings; }
        public void setSettings(String settings) { this.settings = settings; }
        public long getMaxSizeMb() { return maxSizeMb; }
        public void setMaxSizeMb(long maxSizeMb) { this.maxSizeMb = maxSizeMb; }
        public Duration getMaxAge() { return maxAge; }
        public void setMaxAge(Duration maxAge) { this.maxAge = maxAge; }
        public String getDumpDirectory() { return dumpDirectory; }
        public void setDumpDirectory(String dumpDirectory) { this.dumpDirectory = dumpDirectory; }
        public int getMaxDumpFiles() { return maxDumpFiles; }
        public void setMaxDumpFiles(int maxDumpFiles) { this.maxDumpFiles = maxDumpFiles; }
    }

    /**
     * Settings for the {@link java.lang.management.ThreadMXBean}-based contention collector.
     */
    public static class Contention {

        /**
         * Whether JVM-wide thread contention time monitoring is enabled at startup (small
         * per-monitor overhead). Blocked/waited counts are always available; blocked/waited
         * times are only reported while monitoring is enabled.
         */
        private boolean timeMonitoringEnabled = false;

        /** Default number of top contended threads reported when no explicit limit is given. */
        private int topThreads = 10;

        public boolean isTimeMonitoringEnabled() { return timeMonitoringEnabled; }
        public void setTimeMonitoringEnabled(boolean timeMonitoringEnabled) { this.timeMonitoringEnabled = timeMonitoringEnabled; }
        public int getTopThreads() { return topThreads; }
        public void setTopThreads(int topThreads) { this.topThreads = topThreads; }
    }
}
