package com.adhar.kit.persistence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "adhar.persistence")
public class PersistenceProperties {
    private boolean enabled = true;
    private boolean enableAuditing = true;
    private boolean enableMultiTenancy = false;
    private MultiTenancyStrategy multiTenancyStrategy = MultiTenancyStrategy.SCHEMA;
    private boolean enableSoftDelete = true;
    private Migration migration = new Migration();
    private ConnectionPool connectionPool = new ConnectionPool();

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
}

