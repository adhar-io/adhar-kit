package com.adhar.kit.persistence.multitenancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaMultiTenantConnectionProvider Tests")
class SchemaMultiTenantConnectionProviderTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private SchemaMultiTenantConnectionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SchemaMultiTenantConnectionProvider(dataSource);
    }

    @Test
    @DisplayName("rejects a null DataSource")
    void rejectsNullDataSource() {
        assertThrows(NullPointerException.class, () -> new SchemaMultiTenantConnectionProvider(null));
    }

    @Test
    @DisplayName("getAnyConnection() delegates straight to the DataSource")
    void getAnyConnectionDelegates() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        Connection result = provider.getAnyConnection();

        assertSame(connection, result);
    }

    @Test
    @DisplayName("releaseAnyConnection() closes the connection")
    void releaseAnyConnectionCloses() throws SQLException {
        provider.releaseAnyConnection(connection);
        verify(connection).close();
    }

    @Test
    @DisplayName("getConnection(tenant) actually switches the JDBC schema to the tenant identifier")
    void getConnectionSwitchesSchema() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        Connection result = provider.getConnection("acme_corp");

        assertSame(connection, result);
        verify(connection).setSchema("acme_corp");
    }

    @Test
    @DisplayName("getConnection(tenant) falls back to 'public' schema for a null/blank tenant")
    void getConnectionFallsBackToPublicSchema() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        provider.getConnection(null);

        verify(connection).setSchema("public");
    }

    @Test
    @DisplayName("getConnection(tenant) closes and rethrows if setSchema fails")
    void getConnectionClosesAndRethrowsOnSchemaFailure() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        SQLException schemaFailure = new SQLException("no such schema");
        org.mockito.Mockito.doThrow(schemaFailure).when(connection).setSchema("bad_tenant");

        SQLException thrown = assertThrows(SQLException.class, () -> provider.getConnection("bad_tenant"));

        assertSame(schemaFailure, thrown);
        verify(connection).close();
    }

    @Test
    @DisplayName("releaseConnection(tenant, connection) closes the connection")
    void releaseConnectionCloses() throws SQLException {
        provider.releaseConnection("acme_corp", connection);
        verify(connection).close();
    }

    @Test
    @DisplayName("does not support aggressive release")
    void doesNotSupportAggressiveRelease() {
        assertFalse(provider.supportsAggressiveRelease());
    }

    @Test
    @DisplayName("unwrap() returns itself for a compatible type")
    void unwrapReturnsSelf() {
        assertTrue(provider.isUnwrappableAs(SchemaMultiTenantConnectionProvider.class));
        assertSame(provider, provider.unwrap(SchemaMultiTenantConnectionProvider.class));
    }

    @Test
    @DisplayName("unwrap() rejects an incompatible type")
    void unwrapRejectsIncompatibleType() {
        assertFalse(provider.isUnwrappableAs(String.class));
        assertThrows(IllegalArgumentException.class, () -> provider.unwrap(String.class));
    }
}
