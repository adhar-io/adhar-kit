package com.adhar.kit.config.refresh;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manages configuration refresh.
 * Can automatically refresh configuration at intervals.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "adhar.config.refresh", name = "auto-refresh", havingValue = "true")
@RequiredArgsConstructor
public class ConfigRefreshManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshManager.class);

    private final ContextRefresher contextRefresher;

    /**
     * Auto-refresh configuration at configured interval.
     */
    @Scheduled(fixedDelayString = "${adhar.config.refresh.refresh-interval:60000}")
    public void autoRefresh() {
        try {
            log.info("Auto-refreshing configuration");
            contextRefresher.refresh();
            log.info("Configuration refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to auto-refresh configuration", e);
        }
    }

    /**
     * Manually trigger configuration refresh.
     */
    public void refresh() {
        try {
            log.info("Manually refreshing configuration");
            contextRefresher.refresh();
            log.info("Configuration refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh configuration", e);
            throw new ConfigRefreshException("Failed to refresh configuration", e);
        }
    }

    public static class ConfigRefreshException extends RuntimeException {
        public ConfigRefreshException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

