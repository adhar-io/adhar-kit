package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.config.BatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AdharBatchRepositoryConfiguration}: the repository's DataSource is the
 * dedicated batch one when there is one, else the primary.
 */
class AdharBatchRepositoryConfigurationTest {

    private final AdharBatchRepositoryConfiguration configuration = new AdharBatchRepositoryConfiguration();
    private final BatchProperties properties = new BatchProperties();

    private static ObjectProvider<DataSource> dedicated(DataSource dataSource) {
        return new StaticListableBeanFactory(Map.of("batchDataSource", dataSource)).getBeanProvider(DataSource.class);
    }

    private static ObjectProvider<DataSource> noDedicated() {
        return new StaticListableBeanFactory().getBeanProvider(DataSource.class);
    }

    @Test
    @DisplayName("the layout is detected on the primary DataSource without a dedicated one")
    void layoutOnThePrimary() throws Exception {
        DataSource primary = BatchRepositoryLayoutTest.reporting("PostgreSQL");

        assertThat(configuration.batchRepositoryLayout(primary, noDedicated(), properties).managed()).isTrue();
    }

    @Test
    @DisplayName("a dedicated batch DataSource wins: the primary is not even asked")
    void layoutOnTheDedicatedDataSource() throws Exception {
        DataSource primary = mock(DataSource.class);
        DataSource batch = BatchRepositoryLayoutTest.reporting("PostgreSQL");

        assertThat(configuration.batchRepositoryLayout(primary, dedicated(batch), properties).managed()).isTrue();

        verifyNoInteractions(primary);
    }

    @Test
    @DisplayName("the migrator works on that DataSource, with adhar.batch.repository.migrate")
    void migrator() {
        DataSource primary = mock(DataSource.class);
        DataSource batch = mock(DataSource.class);
        var layout = new BatchRepositoryLayout(false, "H2", "batch", "BATCH_");
        properties.getRepository().setMigrate(false);

        BatchSchemaMigrator migrator = configuration.batchSchemaMigrator(primary, dedicated(batch), layout, properties);

        assertThat(migrator.getLayout()).isSameAs(layout);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(migrator, "dataSource")).isSameAs(batch);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(migrator, "migrate")).isEqualTo(false);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(
                configuration.batchSchemaMigrator(primary, noDedicated(), layout, properties), "dataSource"))
                .isSameAs(primary);
    }

    @Test
    @DisplayName("the table prefix post-processor is created without touching the layout")
    @SuppressWarnings("unchecked")
    void tablePrefixConfigurer() {
        ObjectProvider<BatchRepositoryLayout> layout = mock(ObjectProvider.class);

        assertThat(AdharBatchRepositoryConfiguration.batchTablePrefixConfigurer(layout)).isNotNull();

        verifyNoInteractions(layout);
    }
}
