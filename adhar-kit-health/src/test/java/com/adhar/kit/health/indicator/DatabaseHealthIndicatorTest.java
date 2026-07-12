package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DatabaseHealthIndicator}.
 */
class DatabaseHealthIndicatorTest {

    private AdharHealthProperties.DatabaseConfig config() {
        return new AdharHealthProperties.DatabaseConfig();
    }

    @Test
    void getName_returnsDatabase() {
        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(mock(DataSource.class), config());
        assertThat(indicator.getName()).isEqualTo("database");
    }

    @Test
    void check_whenDisabled_returnsUnknown() {
        AdharHealthProperties.DatabaseConfig config = config();
        config.setEnabled(false);
        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(mock(DataSource.class), config);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getComponent()).isEqualTo("database");
        assertThat(health.getDetails()).containsEntry("status", "disabled");
    }

    @Test
    void check_whenConnectionValid_returnsUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.1");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(dataSource, config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("database", "PostgreSQL")
            .containsEntry("version", "16.1")
            .containsEntry("validationQuery", "SELECT 1");
        verify(statement).setQueryTimeout(5);
        verify(resultSet).close();
        verify(connection).close();
    }

    @Test
    void check_whenConnectionFails_returnsDown() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(dataSource, config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).isEqualTo("connection refused");
        assertThat(health.getDetails()).containsEntry("error", "connection refused");
    }
}
