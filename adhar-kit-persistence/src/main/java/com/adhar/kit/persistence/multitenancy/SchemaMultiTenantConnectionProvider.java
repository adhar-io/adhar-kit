package com.adhar.kit.persistence.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Hibernate {@link MultiTenantConnectionProvider} implementing the {@code SCHEMA} multi-tenancy
 * strategy: every tenant shares the same underlying {@link DataSource} / connection pool, and
 * isolation is achieved by switching the JDBC connection's current schema
 * ({@link Connection#setSchema(String)}) to the resolved tenant identifier before handing the
 * connection to Hibernate.
 *
 * <p>Registered as {@code hibernate.multi_tenant_connection_provider} by
 * {@code PersistenceAutoConfiguration} when
 * {@code adhar.persistence.multitenancy.enabled=true} and
 * {@code adhar.persistence.multitenancy.strategy=SCHEMA}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger log = LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        String schema = resolveSchema(tenantIdentifier);
        try {
            connection.setSchema(schema);
        } catch (SQLException ex) {
            log.error("Failed to switch JDBC connection to schema '{}' for tenant '{}'", schema, tenantIdentifier, ex);
            connection.close();
            throw ex;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.equals(unwrapType)
                || SchemaMultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Cannot unwrap " + getClass().getName() + " as " + unwrapType.getName());
    }

    private String resolveSchema(String tenantIdentifier) {
        return (tenantIdentifier == null || tenantIdentifier.isBlank()) ? "public" : tenantIdentifier;
    }
}
