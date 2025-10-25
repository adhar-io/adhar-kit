package com.adhar.kit.config.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client for Spring Cloud Config Server.
 * Provides programmatic access to configuration properties.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "adhar.config.cloud-config", name = "enabled", havingValue = "true")
public class ConfigServerClient {

    private static final Logger log = LoggerFactory.getLogger(ConfigServerClient.class);

    private final RestTemplate restTemplate;

    @Autowired
    public ConfigServerClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch configuration from config server.
     */
    public Map<String, Object> fetchConfig(String application, String profile, String label) {
        String url = String.format("/config/%s/%s/%s", application, profile, label);

        try {
            log.debug("Fetching config from: {}", url);
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Failed to fetch config from server", e);
            throw new ConfigFetchException("Failed to fetch configuration", e);
        }
    }

    /**
     * Refresh configuration.
     */
    public void refreshConfig() {
        try {
            log.info("Refreshing configuration");
            restTemplate.postForObject("/actuator/refresh", null, String.class);
        } catch (Exception e) {
            log.error("Failed to refresh config", e);
        }
    }

    public static class ConfigFetchException extends RuntimeException {
        public ConfigFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

