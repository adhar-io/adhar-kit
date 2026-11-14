package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.database.ItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A writer of {@code INSERT … ON CONFLICT (<keys>) DO UPDATE} on PostgreSQL: writing the same item
 * twice leaves one row with its latest values. That is what makes a chunk's output exactly-once when
 * a restart, or a retried chunk, writes it again.
 *
 * <pre>{@code
 * JdbcBatchItemWriter<Record> writer = PostgresUpsertItemWriterBuilder.<Record>into(dataSource, "public.records")
 *         .columns("record_key", "amount", "business_date")
 *         .conflictOn("record_key")
 *         .build();
 * }</pre>
 *
 * <p>Items are bean-mapped by default: column {@code record_key} binds the property
 * {@code recordKey}, the same convention {@code KeysetPagingItemReaderBuilder} reads with;
 * {@link #column(String, String)} names another property. {@link #columnMapped()} binds
 * {@code Map} items by column name. Every column not in the conflict key is updated unless
 * {@link #update(String...)} or {@link #doNothingOnConflict()} says otherwise.</p>
 *
 * @param <T> the item type
 * @since 0.1.0
 */
public final class PostgresUpsertItemWriterBuilder<T> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final DataSource dataSource;
    private final String table;
    private final Map<String, String> columns = new LinkedHashMap<>();
    private final Set<String> conflictKey = new LinkedHashSet<>();
    private List<String> updateColumns;
    private boolean doNothing;
    private boolean columnMapped;
    private ItemSqlParameterSourceProvider<T> parameterSourceProvider;

    private PostgresUpsertItemWriterBuilder(DataSource dataSource, String table) {
        this.dataSource = dataSource;
        this.table = table;
    }

    /**
     * @param dataSource the PostgreSQL database
     * @param table      the target table, optionally schema-qualified
     * @param <T>        the item type
     * @return the builder
     */
    public static <T> PostgresUpsertItemWriterBuilder<T> into(DataSource dataSource, String table) {
        return new PostgresUpsertItemWriterBuilder<>(dataSource, table);
    }

    /**
     * @param names columns written, each bound from the property of its camel-case name
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> columns(String... names) {
        for (String name : names) {
            column(name, null);
        }
        return this;
    }

    /**
     * @param name     a column written
     * @param property the bean property it is bound from ({@code null}: the camel-case column name)
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> column(String name, String property) {
        columns.put(name, property);
        return this;
    }

    /**
     * @param keys the columns of the unique constraint or index that identifies a row
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> conflictOn(String... keys) {
        conflictKey.addAll(List.of(keys));
        return this;
    }

    /**
     * @param names the columns a conflicting row takes from the new item (default: every non-key column)
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> update(String... names) {
        this.updateColumns = List.of(names);
        return this;
    }

    /**
     * Keeps the existing row on a conflict ({@code DO NOTHING}): first write wins.
     *
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> doNothingOnConflict() {
        this.doNothing = true;
        return this;
    }

    /**
     * Binds {@code Map} items by column name instead of bean properties.
     *
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> columnMapped() {
        this.columnMapped = true;
        return this;
    }

    /**
     * @param provider binds each item's parameters, named by {@link #parameterName(String)} of each column
     * @return this builder
     */
    public PostgresUpsertItemWriterBuilder<T> parameterSourceProvider(ItemSqlParameterSourceProvider<T> provider) {
        this.parameterSourceProvider = provider;
        return this;
    }

    /**
     * @return the statement the writer runs
     * @throws IllegalStateException when the definition is incomplete or names an unsafe identifier
     */
    public String sql() {
        if (!TABLE.matcher(table == null ? "" : table).matches()) {
            throw new IllegalStateException("upsert: '" + table + "' is not a plain, optionally schema-qualified table name");
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("upsert into " + table + ": columns(...) is required");
        }
        if (conflictKey.isEmpty()) {
            throw new IllegalStateException("upsert into " + table + ": conflictOn(...) is required");
        }
        columns.keySet().forEach(this::requireIdentifier);
        conflictKey.forEach(this::requireIdentifier);
        for (String key : conflictKey) {
            if (!columns.containsKey(key)) {
                throw new IllegalStateException("upsert into " + table + ": conflict column '" + key + "' is not written");
            }
        }
        List<String> updated = doNothing ? List.of() : updateColumns != null ? updateColumns
                : columns.keySet().stream().filter(column -> !conflictKey.contains(column)).toList();
        for (String column : updated) {
            requireIdentifier(column);
            if (!columns.containsKey(column) || conflictKey.contains(column)) {
                throw new IllegalStateException("upsert into " + table + ": update column '" + column
                        + "' must be a written, non-key column");
            }
        }
        List<String> values = new ArrayList<>();
        columns.keySet().forEach(column -> values.add(":" + parameterName(column)));
        String insert = "INSERT INTO " + table + " (" + String.join(", ", columns.keySet()) + ") VALUES ("
                + String.join(", ", values) + ") ON CONFLICT (" + String.join(", ", conflictKey) + ")";
        if (updated.isEmpty()) {
            return insert + " DO NOTHING";
        }
        return insert + " DO UPDATE SET " + String.join(", ", updated.stream()
                .map(column -> column + " = EXCLUDED." + column).toList());
    }

    /**
     * @param column a written column
     * @return the named parameter it is bound to
     */
    public String parameterName(String column) {
        if (columnMapped) {
            return column;
        }
        String property = columns.get(column);
        return property != null ? property : JdbcUtils.convertUnderscoreNameToPropertyName(column);
    }

    /**
     * @return the writer, initialised
     */
    public JdbcBatchItemWriter<T> build() {
        String sql = sql();
        ItemSqlParameterSourceProvider<T> provider = parameterSourceProvider != null ? parameterSourceProvider
                : columnMapped ? item -> new MapSqlParameterSource(asMap(item)) : BeanPropertySqlParameterSource::new;
        JdbcBatchItemWriter<T> writer = new JdbcBatchItemWriterBuilder<T>()
                .dataSource(dataSource)
                .sql(sql)
                .itemSqlParameterSourceProvider(provider)
                // DO NOTHING leaves a conflicting row untouched: 0 rows updated is success, not a lost item.
                .assertUpdates(!sql.endsWith("DO NOTHING"))
                .build();
        writer.afterPropertiesSet();
        return writer;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> asMap(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("a column-mapped upsert writes Map items, not " + item.getClass().getName());
        }
        return (Map<String, ?>) map;
    }

    private void requireIdentifier(String column) {
        if (column == null || !IDENTIFIER.matcher(column).matches()) {
            throw new IllegalStateException("upsert into " + table + ": '" + column + "' is not a plain column name");
        }
    }
}
