package com.adhar.kit.test.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DatabaseSeeder}.
 *
 * <p>SQL-building helpers ({@code buildInsertSql}, {@code splitStatements},
 * {@code readClasspathResource}) are pure functions and are tested directly with no
 * database involved. The JDBC-facing methods are tested against a fully mocked
 * {@link DataSource}/{@link Connection}/{@link Statement} stack - no real database or
 * Docker container is required.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("DatabaseSeeder Tests")
class DatabaseSeederTest {

    // ---- SQL building helpers -----------------------------------------------------------

    @Test
    @DisplayName("buildInsertSql should build a parameterized INSERT preserving column order")
    void testBuildInsertSql() {
        Set<String> columns = new LinkedHashSet<>(List.of("id", "name", "email"));

        String sql = DatabaseSeeder.buildInsertSql("users", columns);

        assertEquals("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", sql);
    }

    @Test
    @DisplayName("buildInsertSql should throw for an empty column set")
    void testBuildInsertSqlEmptyColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseSeeder.buildInsertSql("users", Set.of()));
    }

    @Test
    @DisplayName("splitStatements should split on semicolons and drop blank statements")
    void testSplitStatementsBasic() {
        List<String> statements = DatabaseSeeder.splitStatements(
                "INSERT INTO t VALUES (1); INSERT INTO t VALUES (2);;   ");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES (1)", statements.get(0));
        assertEquals("INSERT INTO t VALUES (2)", statements.get(1));
    }

    @Test
    @DisplayName("splitStatements should strip line comments")
    void testSplitStatementsWithComments() {
        String script = "-- header comment\n"
                + "INSERT INTO t VALUES (1); -- trailing comment\n"
                + "\n"
                + "DELETE FROM t WHERE id = 2;\n";

        List<String> statements = DatabaseSeeder.splitStatements(script);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES (1)", statements.get(0));
        assertEquals("DELETE FROM t WHERE id = 2", statements.get(1));
    }

    @Test
    @DisplayName("splitStatements should return an empty list for null or blank input")
    void testSplitStatementsBlank() {
        assertTrue(DatabaseSeeder.splitStatements(null).isEmpty());
        assertTrue(DatabaseSeeder.splitStatements("   \n  ").isEmpty());
        assertTrue(DatabaseSeeder.splitStatements("-- only a comment").isEmpty());
    }

    @Test
    @DisplayName("readClasspathResource should read a UTF-8 text resource")
    void testReadClasspathResource() {
        String content = DatabaseSeeder.readClasspathResource("seed/sample.sql");

        assertNotNull(content);
        assertTrue(content.contains("INSERT INTO users (id, name) VALUES (1, 'Alice')"));
    }

    @Test
    @DisplayName("readClasspathResource should throw for a missing resource")
    void testReadClasspathResourceMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseSeeder.readClasspathResource("seed/does-not-exist.sql"));
    }

    @Test
    @DisplayName("runScriptFromClasspath should execute every statement in the loaded script")
    void testRunScriptFromClasspath() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        DatabaseSeeder.forDataSource(dataSource).runScriptFromClasspath("seed/sample.sql");

        verify(statement, times(3)).execute(anyString());
        verify(statement).execute("INSERT INTO users (id, name) VALUES (1, 'Alice')");
        verify(statement).execute("INSERT INTO users (id, name) VALUES (2, 'Bob')");
        verify(statement).execute("DELETE FROM users WHERE id = 3");
    }

    // ---- execute -------------------------------------------------------------------------

    @Test
    @DisplayName("execute should run the given SQL through a Statement and return itself")
    void testExecute() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);
        DatabaseSeeder result = seeder.execute("DELETE FROM users");

        assertSame(seeder, result);
        verify(statement).execute("DELETE FROM users");
    }

    @Test
    @DisplayName("execute should wrap SQLException in IllegalStateException")
    void testExecuteWrapsSqlException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("boom"));

        DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.execute("DELETE FROM users"));
        assertInstanceOf(SQLException.class, ex.getCause());
    }

    // ---- insert / insertAll --------------------------------------------------------------

    @Test
    @DisplayName("insert should build and run a parameterized INSERT for the row map")
    void testInsert() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("name", "Alice");

        DatabaseSeeder.forDataSource(dataSource).insert("users", row);

        verify(connection).prepareStatement("INSERT INTO users (id, name) VALUES (?, ?)");
        verify(preparedStatement).setObject(1, 1);
        verify(preparedStatement).setObject(2, "Alice");
        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("insert should wrap SQLException in IllegalStateException")
    void testInsertWrapsSqlException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        doThrow(new SQLException("boom")).when(preparedStatement).executeUpdate();

        DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);
        Map<String, Object> row = Map.of("id", 1);

        assertThrows(IllegalStateException.class, () -> seeder.insert("users", row));
    }

    @Test
    @DisplayName("insertAll should insert every row in order")
    void testInsertAll() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3));

        DatabaseSeeder.forDataSource(dataSource).insertAll("users", rows);

        verify(preparedStatement, times(3)).executeUpdate();
    }

    // ---- countRows -------------------------------------------------------------------------

    @Test
    @DisplayName("countRows should return the count from the result set")
    void testCountRows() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT COUNT(*) FROM users")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(42);

        int count = DatabaseSeeder.forDataSource(dataSource).countRows("users");

        assertEquals(42, count);
    }

    @Test
    @DisplayName("countRows should return zero when the result set is empty")
    void testCountRowsEmptyResultSet() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        int count = DatabaseSeeder.forDataSource(dataSource).countRows("users");

        assertEquals(0, count);
    }

    @Test
    @DisplayName("countRows should wrap SQLException in IllegalStateException")
    void testCountRowsWrapsSqlException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("boom"));

        DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);
        assertThrows(IllegalStateException.class, () -> seeder.countRows("users"));
    }

    // ---- truncate --------------------------------------------------------------------------

    @Test
    @DisplayName("truncate should delete rows from every table given, in order")
    void testTruncate() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        DatabaseSeeder.forDataSource(dataSource).truncate("users", "orders");

        verify(statement).execute("DELETE FROM users");
        verify(statement).execute("DELETE FROM orders");
    }

    // ---- runScript ---------------------------------------------------------------------------

    @Test
    @DisplayName("runScript should execute each split statement and return itself for chaining")
    void testRunScript() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);
        DatabaseSeeder result = seeder.runScript("INSERT INTO t VALUES (1); INSERT INTO t VALUES (2);");

        assertSame(seeder, result);
        verify(statement, times(2)).execute(anyString());
    }

    @Test
    @DisplayName("chained calls should all operate against the same underlying data source")
    void testChaining() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        DatabaseSeeder.forDataSource(dataSource)
                .execute("DELETE FROM users")
                .insert("users", Map.of("id", 1))
                .truncate("orders");

        verify(statement).execute("DELETE FROM users");
        verify(statement).execute("DELETE FROM orders");
        verify(preparedStatement).executeUpdate();
    }
}
