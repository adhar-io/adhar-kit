package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource metrics model.
 *
 * <p>Contains CPU and memory usage metrics for pods and containers.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceMetrics {

    /**
     * Pod name.
     */
    private String podName;

    /**
     * Namespace.
     */
    private String namespace;

    /**
     * CPU usage in millicores (e.g., 500 = 0.5 CPU cores).
     */
    private long cpuUsageMillicores;

    /**
     * CPU request in millicores.
     */
    private long cpuRequestMillicores;

    /**
     * CPU limit in millicores.
     */
    private long cpuLimitMillicores;

    /**
     * Memory usage in bytes.
     */
    private long memoryUsageBytes;

    /**
     * Memory request in bytes.
     */
    private long memoryRequestBytes;

    /**
     * Memory limit in bytes.
     */
    private long memoryLimitBytes;

    /**
     * Gets CPU usage as percentage of request.
     *
     * @return percentage (0-100+)
     */
    public int getCpuUsagePercentage() {
        if (cpuRequestMillicores == 0) {
            return 0;
        }
        return (int) ((cpuUsageMillicores * 100) / cpuRequestMillicores);
    }

    /**
     * Gets memory usage as percentage of request.
     *
     * @return percentage (0-100+)
     */
    public int getMemoryUsagePercentage() {
        if (memoryRequestBytes == 0) {
            return 0;
        }
        return (int) ((memoryUsageBytes * 100) / memoryRequestBytes);
    }

    /**
     * Gets CPU usage in cores.
     *
     * @return CPU cores (e.g., 0.5, 1.0, 2.0)
     */
    public double getCpuUsageCores() {
        return cpuUsageMillicores / 1000.0;
    }

    /**
     * Gets memory usage in megabytes.
     *
     * @return memory in MB
     */
    public long getMemoryUsageMB() {
        return memoryUsageBytes / (1024 * 1024);
    }

    /**
     * Gets memory usage in gigabytes.
     *
     * @return memory in GB
     */
    public double getMemoryUsageGB() {
        return memoryUsageBytes / (1024.0 * 1024.0 * 1024.0);
    }

    /**
     * Checks if pod is using high CPU.
     *
     * @param threshold threshold percentage
     * @return true if exceeds threshold
     */
    public boolean isHighCpuUsage(int threshold) {
        return getCpuUsagePercentage() > threshold;
    }

    /**
     * Checks if pod is using high memory.
     *
     * @param threshold threshold percentage
     * @return true if exceeds threshold
     */
    public boolean isHighMemoryUsage(int threshold) {
        return getMemoryUsagePercentage() > threshold;
    }
}

