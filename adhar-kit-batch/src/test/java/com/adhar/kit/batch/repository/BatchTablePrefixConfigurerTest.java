package com.adhar.kit.batch.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchTablePrefixConfigurer}, on Spring Boot's real properties object.
 */
class BatchTablePrefixConfigurerTest {

    private final BatchRepositoryLayout managed = new BatchRepositoryLayout(true, "PostgreSQL", "batch", "BATCH_");
    private final BatchRepositoryLayout unmanaged = new BatchRepositoryLayout(false, "H2", "batch", "BATCH_");
    private final BatchJdbcProperties properties = new BatchJdbcProperties();

    @Test
    @DisplayName("an unset prefix becomes <schema>.<table-prefix> on a managed layout")
    void setsThePrefix() {
        properties.setTablePrefix(null);

        BatchTablePrefixConfigurer.configure(properties, managed);

        assertThat(properties.getTablePrefix()).isEqualTo("batch.BATCH_");
    }

    @Test
    @DisplayName("a prefix that agrees is accepted and normalised")
    void acceptsAnAgreeingPrefix() {
        properties.setTablePrefix("BATCH.batch_");

        BatchTablePrefixConfigurer.configure(properties, managed);

        assertThat(properties.getTablePrefix()).isEqualTo("batch.BATCH_");
    }

    @Test
    @DisplayName("a prefix contradicting the managed schema fails the startup with exit code 2, naming both")
    void refusesAContradictingPrefix() {
        properties.setTablePrefix("public.BATCH_");

        assertThatThrownBy(() -> BatchTablePrefixConfigurer.configure(properties, managed))
                .isInstanceOf(BatchRepositoryException.class)
                .hasMessageContaining("spring.batch.jdbc.table-prefix=public.BATCH_")
                .hasMessageContaining("batch.BATCH_")
                .satisfies(failure -> assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(2));
    }

    @Test
    @DisplayName("initialize-schema=always is refused on a managed layout")
    void refusesInitializeSchemaAlways() {
        properties.setInitializeSchema(DatabaseInitializationMode.ALWAYS);

        assertThatThrownBy(() -> BatchTablePrefixConfigurer.configure(properties, managed))
                .isInstanceOf(BatchRepositoryException.class)
                .hasMessageContaining("spring.batch.jdbc.initialize-schema=always")
                .hasMessageContaining("'batch'");
    }

    @Test
    @DisplayName("an unmanaged layout leaves Spring Boot's properties exactly as configured")
    void leavesAnUnmanagedLayoutAlone() {
        properties.setTablePrefix("whatever_");
        properties.setInitializeSchema(DatabaseInitializationMode.ALWAYS);

        BatchTablePrefixConfigurer.configure(properties, unmanaged);

        assertThat(properties.getTablePrefix()).isEqualTo("whatever_");
        assertThat(properties.getInitializeSchema()).isEqualTo(DatabaseInitializationMode.ALWAYS);
    }

    @Test
    @DisplayName("as a post-processor it configures the properties bean, resolving the layout only then")
    @SuppressWarnings("unchecked")
    void postProcessesTheProperties() {
        ObjectProvider<BatchRepositoryLayout> layout = mock(ObjectProvider.class);
        var configurer = new BatchTablePrefixConfigurer(layout);

        Object other = new Object();
        assertThat(configurer.postProcessAfterInitialization(other, "other")).isSameAs(other);
        verifyNoInteractions(layout);

        when(layout.getObject()).thenReturn(managed);
        properties.setTablePrefix(null);

        assertThat(configurer.postProcessAfterInitialization(properties, "properties")).isSameAs(properties);
        assertThat(properties.getTablePrefix()).isEqualTo("batch.BATCH_");
        assertThat(configurer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
