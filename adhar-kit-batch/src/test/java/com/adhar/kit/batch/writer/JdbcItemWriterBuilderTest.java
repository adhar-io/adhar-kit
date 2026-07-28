package com.adhar.kit.batch.writer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link JdbcItemWriterBuilder}.
 *
 * <p>{@code JdbcBatchItemWriter.build()} does not open a connection, so a mocked
 * {@link DataSource} is sufficient.</p>
 */
class JdbcItemWriterBuilderTest {

    /** Simple bean used as the item type. */
    public record Order(long id, String customerName) {
    }

    private final DataSource dataSource = mock(DataSource.class);

    @Test
    @DisplayName("beanMappedWriter builds a writer from SQL")
    void beanMappedWriterBuildsFromSql() {
        JdbcBatchItemWriter<Order> writer = JdbcItemWriterBuilder
                .beanMappedWriter(dataSource,
                        "INSERT INTO orders (id, customer_name) VALUES (:id, :customerName)");

        assertThat(writer).isNotNull();
    }

    @Test
    @DisplayName("beanMappedWriter returns a builder for further customization")
    void beanMappedWriterReturnsBuilder() {
        var writer = JdbcItemWriterBuilder.<Order>beanMappedWriter(dataSource)
                .sql("INSERT INTO orders (id, customer_name) VALUES (:id, :customerName)")
                .build();

        assertThat(writer).isNotNull();
    }

    @Test
    @DisplayName("columnMappedWriter returns a builder for map items")
    void columnMappedWriterReturnsBuilder() {
        var writer = JdbcItemWriterBuilder.<Map<String, Object>>columnMappedWriter(dataSource)
                .sql("INSERT INTO orders (id, customer_name) VALUES (:id, :customerName)")
                .build();

        assertThat(writer).isNotNull();
    }
}
