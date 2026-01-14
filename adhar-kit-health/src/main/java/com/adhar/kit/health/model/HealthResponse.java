package com.adhar.kit.health.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated health response model.
 *
 * <p>Contains overall health status and individual component statuses.</p>
 *
 * <p><b>Example JSON:</b></p>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "timestamp": "2025-11-02T10:30:00",
 *   "components": {
 *     "database": {
 *       "status": "UP",
 *       "details": {"connection": "active"}
 *     },
 *     "redis": {
 *       "status": "UP",
 *       "details": {"ping": "PONG"}
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponse {

    /**
     * Overall status.
     */
    @Builder.Default
    private Health.Status status = Health.Status.UNKNOWN;

    /**
     * Timestamp.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Component health statuses.
     */
    @Builder.Default
    private Map<String, Health> components = new HashMap<>();

    /**
     * Overall details.
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Adds component health.
     *
     * @param name component name
     * @param health health status
     * @return this response
     */
    public HealthResponse addComponent(String name, Health health) {
        components.put(name, health);
        return this;
    }

    /**
     * Adds detail.
     *
     * @param key detail key
     * @param value detail value
     * @return this response
     */
    public HealthResponse addDetail(String key, Object value) {
        details.put(key, value);
        return this;
    }

    /**
     * Checks if all components are healthy.
     *
     * @return true if all up
     */
    public boolean isHealthy() {
        return status == Health.Status.UP;
    }
}

