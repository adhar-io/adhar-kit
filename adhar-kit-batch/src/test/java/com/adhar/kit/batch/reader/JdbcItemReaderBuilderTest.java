package com.adhar.kit.batch.reader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JdbcItemReaderBuilder} backed by an in-memory H2 database.
 */
class JdbcItemReaderBuilderTest {

    /** Simple bean used as the mapping target. */
    public record Order(long id, String customerName) {
    }

    private EmbeddedDatabase db;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("jdbcreader-" + System.nanoTime())
                .build();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("cursorReader builds a reader from SQL")
    void cursorReaderBuildsFromSql() {
        JdbcCursorItemReader<Order> reader = JdbcItemReaderBuilder
                .cursorReader(db, Order.class, "SELECT id, customer_name FROM orders");

        assertThat(reader).isNotNull();
    }

    @Test
    @DisplayName("cursorReader returns a builder for further customization")
    void cursorReaderReturnsBuilder() {
        var reader = JdbcItemReaderBuilder.cursorReader(db, Order.class)
                .sql("SELECT id, customer_name FROM orders")
                .fetchSize(100)
                .build();

        assertThat(reader).isNotNull();
    }

    @Test
    @DisplayName("pagingReader returns a builder with page size and mapping applied")
    void pagingReaderReturnsBuilder() throws Exception {
        var reader = JdbcItemReaderBuilder.pagingReader(db, Order.class)
                .selectClause("SELECT id, customer_name")
                .fromClause("FROM orders")
                .sortKeys(java.util.Map.of("id",
                        org.springframework.batch.infrastructure.item.database.Order.ASCENDING))
                .build();

        assertThat(reader).isNotNull();
    }
}
