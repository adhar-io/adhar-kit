package com.adhar.kit.health.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health status model.
 *
 * <p>Represents the health status of a component or service.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * Health health = Health.up()
 *     .withDetail("version", "1.0.0")
 *     .withDetail("uptime", uptimeSeconds)
 *     .build();
 *
 * Health health = Health.down(exception)
 *     .withDetail("error", exception.getMessage())
 *     .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Health {

    /**
     * Health status.
     */
    @Builder.Default
    private Status status = Status.UNKNOWN;

    /**
     * Component name.
     */
    private String component;

    /**
     * Health details.
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Error message (if down).
     */
    private String error;

    /**
     * Timestamp.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Health status enum.
     */
    public enum Status {
        UP,
        DOWN,
        OUT_OF_SERVICE,
        UNKNOWN
    }

    /**
     * Creates a healthy status.
     *
     * @return health builder
     */
    public static HealthBuilder up() {
        return Health.builder().status(Status.UP);
    }

    /**
     * Creates an unhealthy status.
     *
     * @return health builder
     */
    public static HealthBuilder down() {
        return Health.builder().status(Status.DOWN);
    }

    /**
     * Creates an unhealthy status with exception.
     *
     * @param exception cause of failure
     * @return health builder
     */
    public static HealthBuilder down(Exception exception) {
        return Health.builder()
            .status(Status.DOWN)
            .error(exception.getMessage());
    }

    /**
     * Creates an out-of-service status.
     *
     * @return health builder
     */
    public static HealthBuilder outOfService() {
        return Health.builder().status(Status.OUT_OF_SERVICE);
    }

    /**
     * Creates an unknown status.
     *
     * @return health builder
     */
    public static HealthBuilder unknown() {
        return Health.builder().status(Status.UNKNOWN);
    }

    /**
     * Custom builder to add withDetail method.
     */
    public static class HealthBuilder {
        private Map<String, Object> details = new HashMap<>();

        /**
         * Adds detail to health.
         *
         * @param key detail key
         * @param value detail value
         * @return this builder
         */
        public HealthBuilder withDetail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        /**
         * Adds multiple details.
         *
         * @param details details map
         * @return this builder
         */
        public HealthBuilder withDetails(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }
    }

    /**
     * Checks if health is up.
     *
     * @return true if up
     */
    public boolean isUp() {
        return status == Status.UP;
    }

    /**
     * Checks if health is down.
     *
     * @return true if down
     */
    public boolean isDown() {
        return status == Status.DOWN || status == Status.OUT_OF_SERVICE;
    }
}

