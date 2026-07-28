package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;

import javax.sql.DataSource;

/**
 * Utility for creating {@link JdbcBatchItemWriter} instances with sensible
 * defaults, in the same style as {@link CsvItemWriterBuilder}.
 *
 * <p>Two mapping strategies are supported for binding item properties to the SQL
 * statement's named parameters:</p>
 * <ul>
 *   <li><b>Bean-mapped</b> ({@link #beanMappedWriter(DataSource)}) - binds
 *       {@code :propertyName} placeholders from JavaBean getters.</li>
 *   <li><b>Column-mapped</b> ({@link #columnMappedWriter(DataSource)}) - binds
 *       parameters from a {@code Map} item's keys.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var writer = JdbcItemWriterBuilder.<Order>beanMappedWriter(dataSource)
 *         .sql("INSERT INTO orders (id, customer_name) VALUES (:id, :customerName)")
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class JdbcItemWriterBuilder {

    private JdbcItemWriterBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured {@link JdbcBatchItemWriterBuilder} that binds SQL
     * named parameters from JavaBean properties of each item.
     *
     * @param dataSource the target data source
     * @param <T>        the item type to write
     * @return a pre-configured builder ready for the {@code sql(...)} call
     */
    public static <T> JdbcBatchItemWriterBuilder<T> beanMappedWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<T>()
                .dataSource(dataSource)
                .beanMapped();
    }

    /**
     * Creates a pre-configured {@link JdbcBatchItemWriterBuilder} that binds SQL
     * named parameters from the keys of each {@code Map} item.
     *
     * @param dataSource the target data source
     * @param <T>        the item type to write (typically a {@code Map})
     * @return a pre-configured builder ready for the {@code sql(...)} call
     */
    public static <T> JdbcBatchItemWriterBuilder<T> columnMappedWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<T>()
                .dataSource(dataSource)
                .columnMapped();
    }

    /**
     * Creates a fully configured bean-mapped {@link JdbcBatchItemWriter} for the
     * given SQL statement.
     *
     * @param dataSource the target data source
     * @param sql        the parameterized SQL statement
     * @param <T>        the item type to write
     * @return a configured writer
     */
    public static <T> JdbcBatchItemWriter<T> beanMappedWriter(DataSource dataSource, String sql) {
        return JdbcItemWriterBuilder.<T>beanMappedWriter(dataSource)
                .sql(sql)
                .build();
    }
}
