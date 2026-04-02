package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.auditing.AuditorAwareImpl;
import com.adhar.kit.persistence.multitenancy.TenantIdentifierResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

import static org.junit.jupiter.api.Assertions.*;

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
}
