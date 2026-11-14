package com.adhar.kit.batch.writer;

import com.adhar.kit.batch.reader.KeysetPagingItemReaderBuilder;
import com.adhar.kit.batch.support.PostgresDatabases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Phase-1 database I/O on PostgreSQL 18.4: the upsert writer is exactly-once on a unique key,
 * and the keyset reader verifies a schema-qualified table the way PostgreSQL folds names.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresUpsertItemWriterBuilderTestContainerTest {

    /** A business record. */
    public record Rec(String recordKey, BigDecimal amount, LocalDate businessDate) {
    }

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = PostgresDatabases.dataSource(PostgresDatabases.createDatabase("upsert"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA app");
        jdbc.execute("CREATE TABLE app.records (record_key TEXT PRIMARY KEY, amount NUMERIC(12,2) NOT NULL,"
                + " business_date DATE NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
    }

    private static Rec rec(String key, String amount) {
        return new Rec(key, new BigDecimal(amount), LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("a chunk written twice (a restart or a retried chunk) leaves one row per key, with the latest values")
    void writingTwiceIsExactlyOnce() throws Exception {
        var writer = PostgresUpsertItemWriterBuilder.<Rec>into(dataSource, "app.records")
                .columns("record_key", "amount", "business_date").conflictOn("record_key").build();

        writer.write(Chunk.of(rec("a", "1.00"), rec("b", "2.00"), rec("c", "3.00")));
        writer.write(Chunk.of(rec("b", "20.00"), rec("c", "30.00"), rec("d", "4.00")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.records", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT amount FROM app.records ORDER BY record_key", BigDecimal.class))
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("20.00"), new BigDecimal("30.00"),
                        new BigDecimal("4.00"));
    }

    @Test
    @DisplayName("doNothingOnConflict keeps the first row and does not count a conflict as a lost write")
    void doNothingKeepsTheFirstRow() throws Exception {
        var writer = PostgresUpsertItemWriterBuilder.<Map<String, Object>>into(dataSource, "app.records")
                .columns("record_key", "amount", "business_date").conflictOn("record_key")
                .columnMapped().doNothingOnConflict().build();
        Map<String, Object> first = new LinkedHashMap<>(Map.of("record_key", "a", "amount", 1,
                "business_date", LocalDate.of(2026, 11, 3)));
        Map<String, Object> second = new LinkedHashMap<>(first);
        second.put("amount", 99);

        writer.write(Chunk.of(first));
        writer.write(Chunk.of(second));

        assertThat(jdbc.queryForObject("SELECT amount FROM app.records", BigDecimal.class)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("the keyset reader verifies a schema-qualified PostgreSQL table and reads it in key order")
    void keysetReaderOnPostgres() throws Exception {
        for (int i = 9; i >= 1; i--) {
            jdbc.update("INSERT INTO app.records (record_key, amount, business_date) VALUES (?, ?, DATE '2026-11-03')",
                    "k" + i, i);
        }
        var reader = KeysetPagingItemReaderBuilder.of(dataSource, Rec.class)
                .select("record_key, amount, business_date").from("app.records").key("record_key").pageSize(4).build();
        List<String> keys = new ArrayList<>();
        reader.open(new ExecutionContext());
        Rec item;
        while ((item = reader.read()) != null) {
            keys.add(item.recordKey());
        }
        reader.close();

        assertThat(keys).containsExactly("k1", "k2", "k3", "k4", "k5", "k6", "k7", "k8", "k9");
        assertThatThrownBy(() -> KeysetPagingItemReaderBuilder.of(dataSource, Rec.class).from("app.records")
                .key("business_date").build().open(new ExecutionContext())).hasMessageContaining("not covered");
    }
}
