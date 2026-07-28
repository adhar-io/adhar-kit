package com.adhar.kit.batch.reader;

import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;

import javax.sql.DataSource;

/**
 * Utility for creating JDBC-based item readers with sensible defaults, in the
 * same style as {@link CsvItemReaderBuilder} and
 * {@link JpaItemReaderBuilder}.
 *
 * <p>Two access strategies are supported:</p>
 * <ul>
 *   <li><b>Cursor</b> - streams a single {@link java.sql.ResultSet} via
 *       {@link #cursorReader(DataSource, Class)}; efficient for large,
 *       forward-only reads.</li>
 *   <li><b>Paging</b> - issues paged queries via
 *       {@link #pagingReader(DataSource, Class)}; safer across long-running
 *       jobs and restarts.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var reader = JdbcItemReaderBuilder.cursorReader(dataSource, Order.class)
 *         .sql("SELECT id, customer_name FROM orders")
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class JdbcItemReaderBuilder {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private JdbcItemReaderBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured {@link JdbcCursorItemReaderBuilder} that maps each
     * row to the given type using a {@code dataRowMapper}.
     *
     * @param dataSource the data source to read from
     * @param targetType the type to map each row to
     * @param <T>        the target type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> JdbcCursorItemReaderBuilder<T> cursorReader(DataSource dataSource, Class<T> targetType) {
        return new JdbcCursorItemReaderBuilder<T>()
                .name(targetType.getSimpleName() + "JdbcCursorReader")
                .dataSource(dataSource)
                .dataRowMapper(targetType);
    }

    /**
     * Creates a fully configured {@link JdbcCursorItemReader} for the given SQL.
     *
     * @param dataSource the data source to read from
     * @param targetType the type to map each row to
     * @param sql        the SQL query to execute
     * @param <T>        the target type
     * @return a configured cursor reader
     */
    public static <T> JdbcCursorItemReader<T> cursorReader(DataSource dataSource, Class<T> targetType, String sql) {
        return cursorReader(dataSource, targetType)
                .sql(sql)
                .build();
    }

    /**
     * Creates a pre-configured {@link JdbcPagingItemReaderBuilder} with a default
     * page size, mapping each row to the given type using a {@code dataRowMapper}.
     *
     * <p>The caller must supply at least {@code selectClause}, {@code fromClause},
     * and {@code sortKeys} before calling {@code build()}.</p>
     *
     * @param dataSource the data source to read from
     * @param targetType the type to map each row to
     * @param <T>        the target type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> JdbcPagingItemReaderBuilder<T> pagingReader(DataSource dataSource, Class<T> targetType) {
        return new JdbcPagingItemReaderBuilder<T>()
                .name(targetType.getSimpleName() + "JdbcPagingReader")
                .dataSource(dataSource)
                .dataRowMapper(targetType)
                .pageSize(DEFAULT_PAGE_SIZE);
    }
}
