package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.auditing.AuditorAwareImpl;
import com.adhar.kit.persistence.metrics.PersistenceMetricsCollector;
import com.adhar.kit.persistence.multitenancy.CurrentTenantIdentifierResolverImpl;
import com.adhar.kit.persistence.multitenancy.SchemaMultiTenantConnectionProvider;
import com.adhar.kit.persistence.multitenancy.TenantIdentifierResolver;
import com.adhar.kit.persistence.multitenancy.TenantWebFilter;
import com.adhar.kit.persistence.outbox.ApplicationEventOutboxRelay;
import com.adhar.kit.persistence.outbox.DomainEventOutboxBridge;
import com.adhar.kit.persistence.outbox.OutboxPublisher;
import com.adhar.kit.persistence.outbox.OutboxRelay;
import com.adhar.kit.persistence.outbox.OutboxRepository;
import com.adhar.kit.persistence.repository.SoftDeleteRepositoryImpl;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Auto-configuration for Adhar Persistence module.
 *
 * <p>Provides JPA configuration with auditing, multi-tenancy support,
 * optimized connection pooling via HikariCP, persistence metrics,
 * and the transactional outbox pattern.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.adhar", repositoryBaseClass = SoftDeleteRepositoryImpl.class)
@EnableConfigurationProperties(PersistenceProperties.class)
@ConditionalOnProperty(prefix = "adhar.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
public class PersistenceAutoConfiguration {

    @PostConstruct
    public void logPersistenceConfiguration() {
        log.info("Adhar Persistence module initialized with JPA support");
    }

    /**
     * Persistence metrics collector.
     * Uses MeterRegistry when available (Micrometer on classpath), otherwise operates standalone.
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistenceMetricsCollector persistenceMetricsCollector(
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            PersistenceProperties properties) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        long threshold = properties.getMetrics().getSlowQueryThresholdMs();
        if (registry != null) {
            log.info("Persistence metrics enabled with Micrometer (slowQueryThresholdMs={})", threshold);
        } else {
            log.info("Persistence metrics enabled without Micrometer (slowQueryThresholdMs={})", threshold);
        }
        return new PersistenceMetricsCollector(registry, threshold);
    }

    @Slf4j
    @Configuration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    @ConditionalOnProperty(prefix = "adhar.persistence", name = "enable-auditing", havingValue = "true", matchIfMissing = true)
    public static class AuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AuditorAware<String> auditorAware() {
            return new AuditorAwareImpl();
        }
    }

    /**
     * Multi-tenancy configuration.
     *
     * <p>The legacy {@code tenantIdentifierResolver()} bean below is gated by the flat
     * {@code adhar.persistence.enable-multi-tenancy} property and kept only for backward
     * compatibility -- it is a plain Spring bean, not wired into Hibernate.</p>
     *
     * <p>The real Hibernate wiring -- a {@link CurrentTenantIdentifierResolverImpl} and a
     * {@link SchemaMultiTenantConnectionProvider} registered as
     * {@code hibernate.tenant_identifier_resolver} / {@code hibernate.multi_tenant_connection_provider}
     * via a {@link HibernatePropertiesCustomizer} -- is gated by the nested
     * {@code adhar.persistence.multitenancy.enabled} + {@code adhar.persistence.multitenancy.strategy}
     * properties and only supports the {@code SCHEMA} strategy today.</p>
     */
    @Slf4j
    @Configuration
    @RequiredArgsConstructor
    public static class MultiTenancyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.persistence", name = "enable-multi-tenancy", havingValue = "true")
        public TenantIdentifierResolver tenantIdentifierResolver() {
            return new TenantIdentifierResolver();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.persistence.multitenancy", name = "enabled", havingValue = "true")
        @ConditionalOnProperty(prefix = "adhar.persistence.multitenancy", name = "strategy", havingValue = "SCHEMA", matchIfMissing = true)
        public HibernatePropertiesCustomizer schemaMultiTenancyCustomizer(DataSource dataSource) {
            log.info("Schema-based Hibernate multi-tenancy enabled");
            SchemaMultiTenantConnectionProvider connectionProvider = new SchemaMultiTenantConnectionProvider(dataSource);
            CurrentTenantIdentifierResolverImpl identifierResolver = new CurrentTenantIdentifierResolverImpl();
            return hibernateProperties -> {
                hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
                hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, identifierResolver);
            };
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnClass(name = "jakarta.servlet.Filter")
        @ConditionalOnProperty(prefix = "adhar.persistence.multitenancy", name = "enabled", havingValue = "true")
        public TenantWebFilter tenantWebFilter() {
            return new TenantWebFilter();
        }
    }

    /**
     * Outbox pattern configuration -- enabled when {@code adhar.persistence.outbox.enabled=true}.
     */
    @Slf4j
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "adhar.persistence.outbox", name = "enabled", havingValue = "true")
    public static class OutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxRelay outboxRelay(ApplicationEventPublisher eventPublisher) {
            return new ApplicationEventOutboxRelay(eventPublisher);
        }

        @Bean
        @ConditionalOnMissingBean
        public OutboxPublisher outboxPublisher(OutboxRepository outboxRepository,
                                               OutboxRelay relay,
                                               PersistenceProperties properties) {
            log.info("Outbox publisher enabled (pollIntervalMs={}, batchSize={})",
                    properties.getOutbox().getPollIntervalMs(),
                    properties.getOutbox().getBatchSize());
            return new OutboxPublisher(outboxRepository, relay, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.persistence.outbox", name = "bridge-enabled", havingValue = "true")
        public DomainEventOutboxBridge domainEventOutboxBridge(OutboxRepository outboxRepository) {
            log.info("Domain event -> outbox bridge enabled");
            return new DomainEventOutboxBridge(outboxRepository);
        }
    }
}
