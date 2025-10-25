package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.auditing.AuditorAwareImpl;
import com.adhar.kit.persistence.multitenancy.TenantIdentifierResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Auto-configuration for Adhar Persistence module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.adhar")
@EnableConfigurationProperties(PersistenceProperties.class)
@ConditionalOnProperty(prefix = "adhar.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PersistenceAutoConfiguration {

    @Slf4j
    @Configuration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    @ConditionalOnProperty(prefix = "adhar.persistence", name = "enable-auditing", havingValue = "true", matchIfMissing = true)
    public static class AuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AuditorAware<String> auditorAware() {
            // JPA Auditing initialized
            return new AuditorAwareImpl();
        }
    }

    @Slf4j
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.persistence", name = "enable-multi-tenancy", havingValue = "true")
    @RequiredArgsConstructor
    public static class MultiTenancyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public TenantIdentifierResolver tenantIdentifierResolver() {
            // Multi-Tenancy Support initialized
            return new TenantIdentifierResolver();
        }
    }

    // Custom DataSource configuration commented out - use Spring Boot auto-configuration instead
    // Uncomment and add required dependencies if custom HikariCP configuration is needed
    /*
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.persistence.connection-pool", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSource dataSource(PersistenceProperties properties,
                                 org.springframework.boot.autoconfigure.jdbc.DataSourceProperties dataSourceProperties) {
        log.info("Configuring HikariCP connection pool");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceProperties.getUrl());
        config.setUsername(dataSourceProperties.getUsername());
        config.setPassword(dataSourceProperties.getPassword());
        config.setDriverClassName(dataSourceProperties.getDriverClassName());

        PersistenceProperties.ConnectionPool poolConfig = properties.getConnectionPool();
        config.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
        config.setMinimumIdle(poolConfig.getMinimumIdle());
        config.setConnectionTimeout(poolConfig.getConnectionTimeout());
        config.setIdleTimeout(poolConfig.getIdleTimeout());
        config.setMaxLifetime(poolConfig.getMaxLifetime());
        config.setPoolName(poolConfig.getPoolName());

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(config);
    }
    */
}

