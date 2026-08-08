package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.PersistenceFacade;
import com.adhar.kit.persistence.api.PersistenceService;
import com.adhar.kit.persistence.auditing.AuditorAwareImpl;
import com.adhar.kit.persistence.spring.SpringPersistenceAdapter;
import com.adhar.kit.persistence.diagnostics.NPlusOneDetector;
import com.adhar.kit.persistence.envers.RevisionHistoryReader;
import com.adhar.kit.persistence.metrics.PersistenceMetricsCollector;
import com.adhar.kit.persistence.multitenancy.CurrentTenantIdentifierResolverImpl;
import com.adhar.kit.persistence.multitenancy.SchemaMultiTenantConnectionProvider;
import com.adhar.kit.persistence.multitenancy.TenantIdentifierResolver;
import com.adhar.kit.persistence.multitenancy.TenantWebFilter;
import com.adhar.kit.persistence.outbox.ApplicationEventOutboxRelay;
import com.adhar.kit.persistence.outbox.DomainEventOutboxBridge;
import com.adhar.kit.persistence.outbox.KafkaOutboxRelay;
import com.adhar.kit.persistence.outbox.OutboxPublisher;
import com.adhar.kit.persistence.outbox.OutboxRelay;
import com.adhar.kit.persistence.outbox.OutboxRepository;
import com.adhar.kit.persistence.repository.SoftDeleteRepositoryImpl;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.envers.AuditReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
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

    /**
     * The real JPA-backed {@link PersistenceService}. Registered whenever JPA
     * infrastructure (an {@link EntityManagerFactory} and a transaction manager)
     * is present, so the facade delegates to a live {@code EntityManager} instead
     * of failing.
     */
    @Bean
    @ConditionalOnMissingBean(PersistenceService.class)
    @ConditionalOnBean({EntityManagerFactory.class, PlatformTransactionManager.class})
    public SpringPersistenceAdapter springPersistenceAdapter(
            PersistenceMetricsCollector persistenceMetricsCollector,
            PlatformTransactionManager transactionManager,
            PersistenceProperties properties) {
        log.info("Registering SpringPersistenceAdapter as the PersistenceService implementation");
        return new SpringPersistenceAdapter(persistenceMetricsCollector, transactionManager, properties);
    }

    /**
     * Dapr state-store backed key-value repository, wired only when the optional
     * {@code adhar-kit-dapr} module is on the classpath and Dapr is explicitly
     * enabled ({@code adhar.dapr.enabled=true}).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.adhar.kit.dapr.DaprFacade")
    @ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true")
    public static class DaprStateRepositoryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(com.adhar.kit.dapr.DaprFacade.class)
        @ConditionalOnProperty(prefix = "adhar.persistence.dapr", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        public com.adhar.kit.persistence.dapr.DaprStateRepository daprStateRepository(
                com.adhar.kit.dapr.DaprFacade daprFacade, PersistenceProperties properties) {
            log.info("Registering Dapr state-store repository (store '{}')",
                    properties.getDapr().getStateStore());
            return new com.adhar.kit.persistence.dapr.DaprStateRepository(
                    daprFacade, properties.getDapr().getStateStore());
        }
    }

    /**
     * The {@link PersistenceFacade} singleton, linked to the
     * {@link PersistenceService} implementation when one is available. Without a
     * delegate the facade fails loudly on use instead of faking success.
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistenceFacade persistenceFacade(ObjectProvider<PersistenceService> serviceProvider) {
        PersistenceFacade facade = PersistenceFacade.getInstance();
        PersistenceService service = serviceProvider.getIfAvailable();
        if (service != null) {
            facade.setDelegate(service);
        } else {
            log.warn("No PersistenceService available - PersistenceFacade operations will throw "
                    + "until a delegate is configured");
        }
        return facade;
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
        @ConditionalOnProperty(prefix = "adhar.persistence.outbox", name = "relay",
                havingValue = "application-event", matchIfMissing = true)
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

    /**
     * Kafka-backed outbox relay -- active only when {@code spring-kafka} is on the classpath, the
     * outbox is enabled, a {@link KafkaTemplate} bean exists, and
     * {@code adhar.persistence.outbox.relay=kafka}. Supplies a {@link KafkaOutboxRelay} in place of
     * the default {@link ApplicationEventOutboxRelay}; the {@link OutboxPublisher} then relays due
     * events onto Kafka. Isolated in its own {@code @ConditionalOnClass}-gated configuration so the
     * module still loads when {@code spring-kafka} is absent.
     */
    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "adhar.persistence.outbox", name = "enabled", havingValue = "true")
    public static class KafkaOutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean(OutboxRelay.class)
        @ConditionalOnBean(KafkaTemplate.class)
        @ConditionalOnProperty(prefix = "adhar.persistence.outbox", name = "relay", havingValue = "kafka")
        public OutboxRelay kafkaOutboxRelay(KafkaTemplate<String, String> kafkaTemplate,
                                            PersistenceProperties properties) {
            String topic = properties.getOutbox().getKafka().getTopic();
            log.info("Kafka outbox relay enabled (topic={})", topic);
            return new KafkaOutboxRelay(kafkaTemplate, topic);
        }
    }

    /**
     * Hibernate Envers revision-history support -- active only when {@code hibernate-envers} is on
     * the classpath and {@code adhar.persistence.envers.enabled} is not {@code false}. Envers
     * auto-registers itself with Hibernate; this configuration only exposes a
     * {@link RevisionHistoryReader} helper for reading that history. Isolated in its own
     * {@code @ConditionalOnClass}-gated configuration so the module still loads when Envers is
     * absent.
     */
    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuditReader.class)
    @ConditionalOnProperty(prefix = "adhar.persistence.envers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class EnversConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(EntityManagerFactory.class)
        public RevisionHistoryReader revisionHistoryReader(EntityManagerFactory entityManagerFactory) {
            log.info("Hibernate Envers revision-history reader enabled");
            return new RevisionHistoryReader(
                    SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
        }
    }

    /**
     * N+1 query detector -- disabled by default, enabled via
     * {@code adhar.persistence.diagnostics.n-plus-one.enabled=true}. Registers a
     * {@link NPlusOneDetector} as Hibernate's {@code StatementInspector} through a
     * {@link HibernatePropertiesCustomizer} so it observes every statement Hibernate prepares.
     */
    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.persistence.diagnostics.n-plus-one", name = "enabled", havingValue = "true")
    public static class NPlusOneDetectionConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public NPlusOneDetector nPlusOneDetector(PersistenceProperties properties) {
            int threshold = properties.getDiagnostics().getNPlusOne().getThreshold();
            log.info("N+1 query detector enabled (threshold={})", threshold);
            return new NPlusOneDetector(threshold);
        }

        @Bean
        public HibernatePropertiesCustomizer nPlusOneStatementInspectorCustomizer(NPlusOneDetector detector) {
            return hibernateProperties ->
                    hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, detector);
        }
    }
}
