package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.auditing.AuditorAwareImpl;
import com.adhar.kit.persistence.diagnostics.NPlusOneDetector;
import com.adhar.kit.persistence.multitenancy.TenantIdentifierResolver;
import com.adhar.kit.persistence.outbox.KafkaOutboxRelay;
import com.adhar.kit.persistence.outbox.OutboxRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.data.domain.AuditorAware;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("PersistenceAutoConfiguration Tests")
class PersistenceAutoConfigurationTest {

    @Test
    @DisplayName("Should create PersistenceAutoConfiguration")
    void testAutoConfiguration() {
        PersistenceAutoConfiguration config = new PersistenceAutoConfiguration();
        assertNotNull(config);
    }

    @Test
    @DisplayName("Should log configuration on PostConstruct")
    void testLogPersistenceConfiguration() {
        PersistenceAutoConfiguration config = new PersistenceAutoConfiguration();
        assertDoesNotThrow(config::logPersistenceConfiguration);
    }

    @Test
    @DisplayName("Should create AuditingConfiguration")
    void testAuditingConfiguration() {
        PersistenceAutoConfiguration.AuditingConfiguration auditingConfig =
            new PersistenceAutoConfiguration.AuditingConfiguration();
        assertNotNull(auditingConfig);
    }

    @Test
    @DisplayName("Should create auditorAware bean")
    void testAuditorAwareBean() {
        PersistenceAutoConfiguration.AuditingConfiguration auditingConfig =
            new PersistenceAutoConfiguration.AuditingConfiguration();

        AuditorAware<String> auditorAware = auditingConfig.auditorAware();

        assertNotNull(auditorAware);
        assertInstanceOf(AuditorAwareImpl.class, auditorAware);
    }

    @Test
    @DisplayName("Should create MultiTenancyConfiguration")
    void testMultiTenancyConfiguration() {
        PersistenceAutoConfiguration.MultiTenancyConfiguration multiTenancyConfig =
            new PersistenceAutoConfiguration.MultiTenancyConfiguration();
        assertNotNull(multiTenancyConfig);
    }

    @Test
    @DisplayName("Should create tenantIdentifierResolver bean")
    void testTenantIdentifierResolverBean() {
        PersistenceAutoConfiguration.MultiTenancyConfiguration multiTenancyConfig =
            new PersistenceAutoConfiguration.MultiTenancyConfiguration();

        TenantIdentifierResolver resolver = multiTenancyConfig.tenantIdentifierResolver();

        assertNotNull(resolver);
        assertInstanceOf(TenantIdentifierResolver.class, resolver);
    }

    @Test
    @DisplayName("KafkaOutboxConfiguration builds a KafkaOutboxRelay for the configured topic")
    @SuppressWarnings("unchecked")
    void testKafkaOutboxRelayBean() {
        PersistenceAutoConfiguration.KafkaOutboxConfiguration config =
                new PersistenceAutoConfiguration.KafkaOutboxConfiguration();
        PersistenceProperties properties = new PersistenceProperties();
        properties.getOutbox().getKafka().setTopic("custom.topic");

        OutboxRelay relay = config.kafkaOutboxRelay(mock(KafkaTemplate.class), properties);

        assertNotNull(relay);
        assertInstanceOf(KafkaOutboxRelay.class, relay);
    }

    @Test
    @DisplayName("NPlusOneDetectionConfiguration builds a detector and statement-inspector customizer")
    void testNPlusOneBeans() {
        PersistenceAutoConfiguration.NPlusOneDetectionConfiguration config =
                new PersistenceAutoConfiguration.NPlusOneDetectionConfiguration();
        PersistenceProperties properties = new PersistenceProperties();
        properties.getDiagnostics().getNPlusOne().setThreshold(7);

        NPlusOneDetector detector = config.nPlusOneDetector(properties);
        assertNotNull(detector);
        assertEquals(7, detector.getThreshold());

        HibernatePropertiesCustomizer customizer = config.nPlusOneStatementInspectorCustomizer(detector);
        Map<String, Object> hibernateProps = new HashMap<>();
        customizer.customize(hibernateProps);
        assertSame(detector, hibernateProps.get(org.hibernate.cfg.AvailableSettings.STATEMENT_INSPECTOR));
    }

    @Test
    @DisplayName("EnversConfiguration can be instantiated")
    void testEnversConfiguration() {
        assertNotNull(new PersistenceAutoConfiguration.EnversConfiguration());
    }
}
