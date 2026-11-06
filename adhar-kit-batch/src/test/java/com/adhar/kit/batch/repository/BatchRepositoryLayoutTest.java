package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.config.BatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.MetaDataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchRepositoryLayout}: the kit manages the schema on PostgreSQL only.
 */
class BatchRepositoryLayoutTest {

    private final BatchProperties.Repository repository = new BatchProperties.Repository();

    private final BatchRepositoryLayout managed = new BatchRepositoryLayout(true, "PostgreSQL", "batch", "BATCH_");
    private final BatchRepositoryLayout unmanaged = new BatchRepositoryLayout(false, "H2", "batch", "BATCH_");

    static DataSource reporting(String product) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(product);
        return dataSource;
    }

    @Test
    @DisplayName("PostgreSQL is managed, in any spelling of its name, with the configured schema and prefix")
    void postgresIsManaged() throws Exception {
        repository.setSchema("etl");
        repository.setTablePrefix("RUN_");

        BatchRepositoryLayout layout = BatchRepositoryLayout.detect(reporting("PostgreSQL"), repository);

        assertThat(layout).isEqualTo(new BatchRepositoryLayout(true, "PostgreSQL", "etl", "RUN_"));
        assertThat(BatchRepositoryLayout.detect(reporting("POSTGRESQL"), repository).managed()).isTrue();
    }

    @Test
    @DisplayName("any other database is not managed: a real H2 reports itself")
    void otherDatabasesAreNotManaged() throws Exception {
        EmbeddedDatabase h2 = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("layout-" + System.nanoTime()).build();
        try {
            BatchRepositoryLayout layout = BatchRepositoryLayout.detect(h2, repository);

            assertThat(layout.managed()).isFalse();
            assertThat(layout.databaseProduct()).isEqualTo("H2");
        } finally {
            h2.shutdown();
        }
        assertThat(BatchRepositoryLayout.detect(reporting("MySQL"), repository).managed()).isFalse();
    }

    @Test
    @DisplayName("a database that cannot be reached is a connectivity failure, not a layout")
    void unreachableDatabase() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        assertThatThrownBy(() -> BatchRepositoryLayout.detect(dataSource, repository))
                .isInstanceOf(MetaDataAccessException.class);
    }

    @Test
    @DisplayName("the Spring Batch prefix qualifies the configured prefix with the schema")
    void qualifiedPrefix() {
        assertThat(managed.qualifiedTablePrefix()).isEqualTo("batch.BATCH_");
    }

    @Test
    @DisplayName("the kit's tables are schema-qualified only when managed")
    void kitTables() {
        assertThat(managed.kitTable(BatchRepositoryLayout.SCHEDULER_LOCK_TABLE)).isEqualTo("batch.ADHAR_SCHEDULER_LOCK");
        assertThat(unmanaged.kitTable(BatchRepositoryLayout.SCHEDULER_LOCK_TABLE)).isEqualTo("ADHAR_SCHEDULER_LOCK");
    }

    @Test
    @DisplayName("a Spring Batch prefix is accepted when unset or equal ignoring case, and refused otherwise")
    void acceptsPrefix() {
        assertThat(managed.acceptsSpringBatchPrefix(null)).isTrue();
        assertThat(managed.acceptsSpringBatchPrefix("batch.BATCH_")).isTrue();
        assertThat(managed.acceptsSpringBatchPrefix("BATCH.batch_")).isTrue();
        assertThat(managed.acceptsSpringBatchPrefix("BATCH_")).isFalse();
        assertThat(managed.acceptsSpringBatchPrefix("public.BATCH_")).isFalse();
    }
}
