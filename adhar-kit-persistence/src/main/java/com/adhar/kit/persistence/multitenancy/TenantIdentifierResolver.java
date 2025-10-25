package com.adhar.kit.persistence.multitenancy;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the current tenant identifier for Hibernate multi-tenancy.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getTenant();
    }

    @Override
    public boolean isRoot(String tenantId) {
        return false;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
