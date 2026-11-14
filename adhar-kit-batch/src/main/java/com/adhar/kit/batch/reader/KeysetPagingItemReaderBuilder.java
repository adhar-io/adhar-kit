package com.adhar.kit.batch.reader;

import com.adhar.kit.batch.BatchFacade;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.infrastructure.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A paging reader that pages by KEY ({@code WHERE (key) > (last key) ORDER BY key}), never by
 * offset, on a key the database guarantees unique and non-null.
 *
 * <p>Restart rests on this: Spring Batch stores the last key read in the step's execution context,
 * and a restarted step continues after it. With an offset, rows inserted or deleted between the
 * attempts shift every later page; with a key that is not unique, rows sharing the key at a page
 * boundary are skipped or read twice. The reader therefore checks the table's primary key and
 * unique indexes and refuses a key that is not covered by one (opt out with
 * {@link #verifyUniqueKey(boolean)} for a join or a view the metadata cannot describe). The check
 * runs when the step opens the reader.</p>
 *
 * <pre>{@code
 * JdbcPagingItemReader<InputRecord> reader = KeysetPagingItemReaderBuilder.of(dataSource, InputRecord.class)
 *         .from("public.inbound_records")
 *         .where("business_date = :businessDate")
 *         .parameter("businessDate", businessDate)
 *         .key("record_key")
 *         .build();
 * }</pre>
 *
 * <p>The page size defaults to {@code adhar.batch.page.size} (500).</p>
 *
 * @param <T> the item type
 * @since 0.1.0
 */
public final class KeysetPagingItemReaderBuilder<T> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final DataSource dataSource;
    private final RowMapper<T> rowMapper;
    private String name;
    private String select = "*";
    private String from;
    private String where;
    private final List<String> keys = new ArrayList<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private Integer pageSize;
    private boolean verifyUniqueKey = true;

    private KeysetPagingItemReaderBuilder(DataSource dataSource, RowMapper<T> rowMapper, String name) {
        this.dataSource = dataSource;
        this.rowMapper = rowMapper;
        this.name = name;
    }

    /**
     * @param dataSource the database to read
     * @param type       a record or bean, mapped by column name ({@code record_key} → {@code recordKey})
     * @param <T>        the item type
     * @return the builder
     */
    public static <T> KeysetPagingItemReaderBuilder<T> of(DataSource dataSource, Class<T> type) {
        return new KeysetPagingItemReaderBuilder<>(dataSource, new DataClassRowMapper<>(type),
                type.getSimpleName() + "KeysetReader");
    }

    /**
     * @param dataSource the database to read
     * @param rowMapper  maps one row
     * @param <T>        the item type
     * @return the builder
     */
    public static <T> KeysetPagingItemReaderBuilder<T> of(DataSource dataSource, RowMapper<T> rowMapper) {
        return new KeysetPagingItemReaderBuilder<>(dataSource, rowMapper, "keysetReader");
    }

    /**
     * @param name the reader's name, which prefixes its keys in the execution context
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param select the selected columns (default {@code *}); the key columns must be among them
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> select(String select) {
        this.select = select;
        return this;
    }

    /**
     * @param from the table, optionally schema-qualified, or any FROM clause with
     *             {@code verifyUniqueKey(false)}
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> from(String from) {
        this.from = from;
        return this;
    }

    /**
     * @param where a condition with {@code :named} parameters
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> where(String where) {
        this.where = where;
        return this;
    }

    /**
     * @param name  a parameter of the condition
     * @param value its value
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> parameter(String name, Object value) {
        this.parameters.put(name, value);
        return this;
    }

    /**
     * @param columns the key, most significant column first; read in ascending order
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> key(String... columns) {
        this.keys.clear();
        this.keys.addAll(List.of(columns));
        return this;
    }

    /**
     * @param pageSize rows per query
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /**
     * @param verify whether the reader checks, when opened, that the key is unique and non-null (default true)
     * @return this builder
     */
    public KeysetPagingItemReaderBuilder<T> verifyUniqueKey(boolean verify) {
        this.verifyUniqueKey = verify;
        return this;
    }

    /**
     * The definition is checked here; the table's key is checked when the step first opens the
     * reader, so a reader that is a singleton bean does not need the business schema before the
     * application's own migrations have created it.
     *
     * @return the reader
     * @throws IllegalStateException when the definition is incomplete or malformed
     */
    public JdbcPagingItemReader<T> build() {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(name + ": from(...) is required");
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException(name + ": key(...) is required - keyset paging needs a unique key");
        }
        keys.forEach(column -> {
            if (!IDENTIFIER.matcher(column).matches()) {
                throw new IllegalStateException(name + ": key column '" + column + "' is not a plain column name");
            }
        });
        if (verifyUniqueKey && !TABLE.matcher(from.trim()).matches()) {
            throw new IllegalStateException(name + ": cannot verify that the key of '" + from
                    + "' is unique; name a table, or declare verifyUniqueKey(false) for a join or a view");
        }
        Map<String, Order> sortKeys = new LinkedHashMap<>();
        keys.forEach(column -> sortKeys.put(column, Order.ASCENDING));
        SqlPagingQueryProviderFactoryBean provider = new SqlPagingQueryProviderFactoryBean();
        provider.setDataSource(dataSource);
        provider.setSelectClause(select);
        provider.setFromClause(from);
        provider.setWhereClause(where);
        provider.setSortKeys(sortKeys);
        KeysetReader reader = new KeysetReader(provider);
        reader.setName(name);
        reader.setRowMapper(rowMapper);
        reader.setParameterValues(new HashMap<>(parameters));
        reader.setPageSize(pageSize != null ? pageSize : BatchFacade.getInstance().getDefaultPageSize());
        reader.setSaveState(true);
        return reader;
    }

    /** Checks the key and builds the paging queries on first open, not when the bean is created. */
    private final class KeysetReader extends JdbcPagingItemReader<T> {

        private final SqlPagingQueryProviderFactoryBean provider;
        private boolean initialised;

        private KeysetReader(SqlPagingQueryProviderFactoryBean provider) {
            super(dataSource, new PostgresPagingQueryProvider());
            this.provider = provider;
        }

        @Override
        public void afterPropertiesSet() {
            // Deferred to open(): see build().
        }

        @Override
        public synchronized void open(ExecutionContext executionContext) {
            if (!initialised) {
                if (verifyUniqueKey) {
                    verifyUniqueKey();
                }
                try {
                    setQueryProvider(provider.getObject());
                    super.afterPropertiesSet();
                } catch (Exception ex) {
                    throw new IllegalStateException(name + ": could not build the paging queries: " + ex.getMessage(), ex);
                }
                initialised = true;
            }
            super.open(executionContext);
        }
    }

    private void verifyUniqueKey() {
        String table = from.trim();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String schema = table.contains(".") ? table.substring(0, table.indexOf('.')) : null;
            String relation = table.contains(".") ? table.substring(table.indexOf('.') + 1) : table;
            schema = schema == null ? null : fold(metadata, schema);
            relation = fold(metadata, relation);

            Set<String> key = new LinkedHashSet<>();
            keys.forEach(column -> key.add(column.toLowerCase(Locale.ROOT)));
            List<Set<String>> unique = uniqueColumnSets(metadata, schema, relation);
            if (unique.isEmpty() && !tableExists(metadata, schema, relation)) {
                throw new IllegalStateException(name + ": table '" + from + "' does not exist");
            }
            boolean covered = unique.stream().anyMatch(key::containsAll);
            if (!covered) {
                throw new IllegalStateException(name + ": key " + keys + " of '" + from + "' is not covered by its"
                        + " primary key or a unique index, so rows sharing a key at a page boundary would be"
                        + " skipped or read twice on restart");
            }
            for (String column : nullableColumns(metadata, schema, relation)) {
                if (key.contains(column)) {
                    throw new IllegalStateException(name + ": key column '" + column + "' of '" + from
                            + "' is nullable; NULL never compares greater than the last key, so those rows"
                            + " would never be read");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(name + ": could not read the metadata of '" + from + "'", ex);
        }
    }

    private static String fold(DatabaseMetaData metadata, String identifier) throws SQLException {
        if (metadata.storesUpperCaseIdentifiers()) {
            return identifier.toUpperCase(Locale.ROOT);
        }
        return metadata.storesLowerCaseIdentifiers() ? identifier.toLowerCase(Locale.ROOT) : identifier;
    }

    private static List<Set<String>> uniqueColumnSets(DatabaseMetaData metadata, String schema, String table)
            throws SQLException {
        Map<String, Set<String>> byIndex = new LinkedHashMap<>();
        try (ResultSet primaryKey = metadata.getPrimaryKeys(null, schema, table)) {
            while (primaryKey.next()) {
                byIndex.computeIfAbsent("<primary key>", k -> new LinkedHashSet<>())
                        .add(primaryKey.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        try (ResultSet indexes = metadata.getIndexInfo(null, schema, table, true, true)) {
            while (indexes.next()) {
                String index = indexes.getString("INDEX_NAME");
                String column = indexes.getString("COLUMN_NAME");
                if (index != null && column != null && !indexes.getBoolean("NON_UNIQUE")) {
                    byIndex.computeIfAbsent(index, k -> new LinkedHashSet<>()).add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(byIndex.values());
    }

    private static Set<String> nullableColumns(DatabaseMetaData metadata, String schema, String table)
            throws SQLException {
        Set<String> nullable = new LinkedHashSet<>();
        try (ResultSet columns = metadata.getColumns(null, schema, table, null)) {
            while (columns.next()) {
                if (columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) {
                    nullable.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return nullable;
    }

    private static boolean tableExists(DatabaseMetaData metadata, String schema, String table) throws SQLException {
        try (ResultSet tables = metadata.getTables(null, schema, table, null)) {
            return tables.next();
        }
    }
}
