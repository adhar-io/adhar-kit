package com.adhar.kit.persistence.multitenancy;

/**
 * Hibernate {@link org.hibernate.context.spi.CurrentTenantIdentifierResolver} that is actually
 * wired into Hibernate's {@code hibernate.tenant_identifier_resolver} setting by
 * {@code PersistenceAutoConfiguration} (via a {@code HibernatePropertiesCustomizer}) when
 * {@code adhar.persistence.multitenancy.enabled=true}.
 *
 * <p>Behaviorally identical to the legacy {@link TenantIdentifierResolver} (kept for backward
 * compatibility as a plain Spring bean), but under its own name since it now carries a real
 * runtime responsibility: every Hibernate session asks this resolver which tenant identifier to
 * scope its {@link org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider}
 * connection to.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class CurrentTenantIdentifierResolverImpl extends TenantIdentifierResolver {
}
