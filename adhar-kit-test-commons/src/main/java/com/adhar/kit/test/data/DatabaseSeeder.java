package com.adhar.kit.test.data;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper for seeding a JDBC {@link DataSource} with test data, either from SQL scripts on the
 * classpath or from row builders ({@link Map}s of column name to value).
 *
 * <p>Every method returns {@code this} so calls can be chained. SQL failures are wrapped in
 * {@link IllegalStateException} so seeder code does not force every test method to declare
 * {@code throws SQLException}.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);
 * seeder.runScriptFromClasspath("seed/users.sql");
 * seeder.insert("users", Map.of("id", 1, "email", "a@b.com"));
 * int count = seeder.countRows("users");
 * seeder.truncate("users");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class DatabaseSeeder {

    private final DataSource dataSource;

    private DatabaseSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Create a seeder for the given data source.
     */
    public static DatabaseSeeder forDataSource(DataSource dataSource) {
        return new DatabaseSeeder(dataSource);
    }

    /**
     * Execute a single SQL statement.
     */
    public DatabaseSeeder execute(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute SQL: " + sql, e);
        }
        return this;
    }

    /**
     * Split a SQL script into individual statements (on {@code ;}, ignoring blank lines and
     * {@code --} line comments) and execute each one in order.
     */
    public DatabaseSeeder runScript(String script) {
        for (String statement : splitStatements(script)) {
            execute(statement);
        }
        return this;
    }

    /**
     * Load a SQL script from the classpath and run it via {@link #runScript(String)}.
     */
    public DatabaseSeeder runScriptFromClasspath(String resourcePath) {
        return runScript(readClasspathResource(resourcePath));
    }

    /**
     * Insert a single row built from a column-name-to-value map.
     */
    public DatabaseSeeder insert(String table, Map<String, Object> row) {
        Set<String> columns = new LinkedHashSet<>(row.keySet());
        String sql = buildInsertSql(table, columns);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String column : columns) {
                statement.setObject(index++, row.get(column));
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert row into " + table, e);
        }
        return this;
    }

    /**
     * Insert every row in the list, in order.
     */
    public DatabaseSeeder insertAll(String table, List<Map<String, Object>> rows) {
        rows.forEach(row -> insert(table, row));
        return this;
    }

    /**
     * Count the rows currently in a table.
     */
    public int countRows(String table) {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count rows in " + table, e);
        }
    }

    /**
     * Delete every row from the given tables (in the order given).
     */
    public DatabaseSeeder truncate(String... tables) {
        for (String table : tables) {
            execute("DELETE FROM " + table);
        }
        return this;
    }

    // ---- SQL building helpers (pure, unit-testable without a real connection) --------------

    /**
     * Build an {@code INSERT INTO table (col1, col2) VALUES (?, ?)} statement for the given
     * table and columns, preserving column order.
     */
    static String buildInsertSql(String table, Set<String> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Cannot build an INSERT statement with no columns");
        }
        String columnList = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";
    }

    /**
     * Split a SQL script into individual statements. Splits on {@code ;}, drops {@code --}
     * line comments, and skips blank statements.
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return statements;
        }
        String withoutComments = script.lines()
                .map(DatabaseSeeder::stripLineComment)
                .collect(Collectors.joining("\n"));

        for (String candidate : withoutComments.split(";")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    private static String stripLineComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    /**
     * Read a UTF-8 text resource from the classpath.
     */
    static String readClasspathResource(String path) {
        try (InputStream in = DatabaseSeeder.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read classpath resource: " + path, e);
        }
    }
}
