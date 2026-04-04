package com.adhar.kit.persistence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar Persistence module.
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   persistence:
 *     enabled: true
 *     enable-auditing: true
 *     enable-multi-tenancy: false
 *     default-batch-size: 50
 *     metrics:
 *       enabled: true
 *       slow-query-threshold-ms: 500
 *     outbox:
 *       enabled: false
 *       poll-interval-ms: 5000
 *       batch-size: 100
 *     connection-pool:
 *       maximum-pool-size: 20
 *       minimum-idle: 5
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.persistence")
public class PersistenceProperties {
    private boolean enabled = true;
    private boolean enableAuditing = true;
    private boolean enableMultiTenancy = false;
    private MultiTenancyStrategy multiTenancyStrategy = MultiTenancyStrategy.SCHEMA;
    private boolean enableSoftDelete = true;
    private int defaultBatchSize = 50;
    private Migration migration = new Migration();
    private ConnectionPool connectionPool = new ConnectionPool();
    private Metrics metrics = new Metrics();
    private Outbox outbox = new Outbox();

    public enum MultiTenancyStrategy {
        SCHEMA, DATABASE, DISCRIMINATOR
    }

    @Data
    public static class Migration {
        private boolean enabled = true;
        private boolean cleanOnValidationError = false;
        private boolean baselineOnMigrate = true;
        private String locations = "classpath:db/migration";
    }

    @Data
    public static class ConnectionPool {
        private String poolName = "AdharHikariPool";
        private int maximumPoolSize = 10;
        private int minimumIdle = 5;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
    }

    @Data
    public static class Metrics {
        private boolean enabled = true;
        private long slowQueryThresholdMs = 500;
    }

    @Data
    public static class Outbox {
        private boolean enabled = false;
        private long pollIntervalMs = 5000;
        private int batchSize = 100;
    }
}
