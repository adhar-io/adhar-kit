package com.adhar.kit.batch.writer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The statement {@link PostgresUpsertItemWriterBuilder} writes; its behaviour on PostgreSQL 18 is
 * {@link PostgresUpsertItemWriterBuilderTestContainerTest}.
 */
class PostgresUpsertItemWriterBuilderTest {

    private final DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:unused");

    private PostgresUpsertItemWriterBuilder<Object> records() {
        return PostgresUpsertItemWriterBuilder.into(dataSource, "public.records")
                .columns("record_key", "amount", "business_date")
                .conflictOn("record_key");
    }

    @Test
    @DisplayName("every non-key column is updated from EXCLUDED, bound from camel-case properties")
    void updateEveryNonKeyColumn() {
        assertThat(records().sql()).isEqualTo("INSERT INTO public.records (record_key, amount, business_date)"
                + " VALUES (:recordKey, :amount, :businessDate) ON CONFLICT (record_key)"
                + " DO UPDATE SET amount = EXCLUDED.amount, business_date = EXCLUDED.business_date");
    }

    @Test
    @DisplayName("update(...) narrows the updated columns; doNothingOnConflict keeps the first row")
    void narrowedAndDoNothing() {
        assertThat(records().update("amount").sql()).endsWith("DO UPDATE SET amount = EXCLUDED.amount");
        assertThat(records().doNothingOnConflict().sql()).endsWith("ON CONFLICT (record_key) DO NOTHING");
        assertThat(PostgresUpsertItemWriterBuilder.into(dataSource, "keys_only").columns("id").conflictOn("id").sql())
                .endsWith("ON CONFLICT (id) DO NOTHING");
    }

    @Test
    @DisplayName("column(...) names the property; columnMapped binds Map keys by column name")
    void parameterNames() {
        var builder = PostgresUpsertItemWriterBuilder.into(dataSource, "t").column("ext_id", "externalId")
                .columns("note").conflictOn("ext_id");
        assertThat(builder.sql()).contains("VALUES (:externalId, :note)");

        var mapped = PostgresUpsertItemWriterBuilder.into(dataSource, "t").columns("ext_id", "note")
                .conflictOn("ext_id").columnMapped();
        assertThat(mapped.sql()).contains("VALUES (:ext_id, :note)");
    }

    @Test
    @DisplayName("an incomplete definition or an unsafe identifier is refused")
    void definitionErrors() {
        assertThatThrownBy(() -> PostgresUpsertItemWriterBuilder.into(dataSource, "t").conflictOn("id").sql())
                .hasMessageContaining("columns(...) is required");
        assertThatThrownBy(() -> PostgresUpsertItemWriterBuilder.into(dataSource, "t").columns("id").sql())
                .hasMessageContaining("conflictOn(...) is required");
        assertThatThrownBy(() -> records().conflictOn("other").sql())
                .hasMessageContaining("conflict column 'other' is not written");
        assertThatThrownBy(() -> records().update("record_key").sql())
                .hasMessageContaining("must be a written, non-key column");
        assertThatThrownBy(() -> PostgresUpsertItemWriterBuilder.into(dataSource, "t; DROP TABLE x").columns("id")
                .conflictOn("id").sql()).hasMessageContaining("not a plain");
        assertThatThrownBy(() -> PostgresUpsertItemWriterBuilder.into(dataSource, "t").columns("id", "a b")
                .conflictOn("id").sql()).hasMessageContaining("'a b' is not a plain column name");
    }
}
