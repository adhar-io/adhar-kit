package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

/**
 * Disk space health indicator.
 *
 * <p>Monitors available disk space and alerts when thresholds are breached.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(
 *     diskSpaceConfig
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DiskSpaceHealthIndicator implements AdharHealthIndicator {

    private final long threshold;
    private final String path;

    /**
     * Creates disk space health indicator.
     *
     * @param threshold minimum free space in bytes
     * @param path path to monitor
     */
    public DiskSpaceHealthIndicator(long threshold, String path) {
        this.threshold = threshold;
        this.path = path;
    }

    @Override
    public Health check() {
        try {
            java.io.File file = new java.io.File(path);
            long freeSpace = file.getFreeSpace();
            long totalSpace = file.getTotalSpace();
            long usableSpace = file.getUsableSpace();

            boolean isHealthy = freeSpace >= threshold;

            if (isHealthy) {
                return Health.up()
                    .component(getName())
                    .withDetail("total", formatBytes(totalSpace))
                    .withDetail("free", formatBytes(freeSpace))
                    .withDetail("usable", formatBytes(usableSpace))
                    .withDetail("threshold", formatBytes(threshold))
                    .withDetail("path", path)
                    .build();
            } else {
                return Health.down()
                    .component(getName())
                    .withDetail("total", formatBytes(totalSpace))
                    .withDetail("free", formatBytes(freeSpace))
                    .withDetail("threshold", formatBytes(threshold))
                    .withDetail("message", "Free disk space below threshold")
                    .build();
            }
        } catch (Exception e) {
            log.error("Disk space health check failed", e);
            return Health.down(e)
                .component(getName())
                .build();
        }
    }

    @Override
    public String getName() {
        return "diskSpace";
    }

    /**
     * Formats bytes to human-readable format.
     */
    private String formatBytes(long bytes) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;

        if (bytes >= gb) {
            return String.format("%.2f GB", (double) bytes / gb);
        } else if (bytes >= mb) {
            return String.format("%.2f MB", (double) bytes / mb);
        } else if (bytes >= kb) {
            return String.format("%.2f KB", (double) bytes / kb);
        } else {
            return bytes + " bytes";
        }
    }
}

