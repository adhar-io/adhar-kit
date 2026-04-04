package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.auditing.AuditorAwareImpl;
import com.adhar.kit.persistence.metrics.PersistenceMetricsCollector;
import com.adhar.kit.persistence.multitenancy.TenantIdentifierResolver;
import com.adhar.kit.persistence.outbox.OutboxPublisher;
import com.adhar.kit.persistence.outbox.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
@EnableJpaRepositories(basePackages = "com.adhar")
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

    @Slf4j
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.persistence", name = "enable-multi-tenancy", havingValue = "true")
    @RequiredArgsConstructor
    public static class MultiTenancyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public TenantIdentifierResolver tenantIdentifierResolver() {
            return new TenantIdentifierResolver();
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
        public OutboxPublisher outboxPublisher(OutboxRepository outboxRepository,
                                               ApplicationEventPublisher eventPublisher,
                                               PersistenceProperties properties) {
            log.info("Outbox publisher enabled (pollIntervalMs={}, batchSize={})",
                    properties.getOutbox().getPollIntervalMs(),
                    properties.getOutbox().getBatchSize());
            return new OutboxPublisher(outboxRepository, eventPublisher, properties);
        }
    }
}
