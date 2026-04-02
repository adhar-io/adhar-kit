package com.adhar.kit.profiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar Kit Performance Profiler.
 */
@ConfigurationProperties(prefix = "adhar.profiler")
public class PerfProfilerProperties {

    private boolean enabled = true;
    private long defaultSlowThresholdMs = 500;
    private boolean logSlowByDefault = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getDefaultSlowThresholdMs() { return defaultSlowThresholdMs; }
    public void setDefaultSlowThresholdMs(long defaultSlowThresholdMs) { this.defaultSlowThresholdMs = defaultSlowThresholdMs; }
    public boolean isLogSlowByDefault() { return logSlowByDefault; }
    public void setLogSlowByDefault(boolean logSlowByDefault) { this.logSlowByDefault = logSlowByDefault; }
}
