package com.adhar.kit.batch.reader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KeysetPagingItemReaderBuilder} on H2: key order, restart after the last key, and the
 * unique-key check that keeps a restart from skipping or re-reading rows.
 */
class KeysetPagingItemReaderBuilderTest {

    /** A row of {@code records}. */
    public record Rec(String region, long seq, String payload) {
    }

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("keyset-" + System.nanoTime()).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE records (region VARCHAR(8) NOT NULL, seq BIGINT NOT NULL, payload VARCHAR(20),"
                + " PRIMARY KEY (region, seq))");
        jdbc.execute("CREATE TABLE loose (id BIGINT NOT NULL, payload VARCHAR(20))");
        jdbc.execute("CREATE TABLE nullable_key (code VARCHAR(8), payload VARCHAR(20), CONSTRAINT uq_code UNIQUE (code))");
        jdbc.execute("CREATE TABLE indexed (id BIGINT NOT NULL, payload VARCHAR(20))");
        jdbc.execute("CREATE UNIQUE INDEX ux_indexed ON indexed (id)");
        for (String region : List.of("eu", "us")) {
            for (long seq = 1; seq <= 7; seq++) {
                jdbc.update("INSERT INTO records VALUES (?, ?, ?)", region, seq, region + "-" + seq);
            }
        }
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private static <T> List<T> readAll(JdbcPagingItemReader<T> reader, ExecutionContext context, int max) throws Exception {
        List<T> items = new ArrayList<>();
        reader.open(context);
        T item;
        while (items.size() < max && (item = reader.read()) != null) {
            items.add(item);
        }
        reader.update(context);
        reader.close();
        return items;
    }

    private KeysetPagingItemReaderBuilder<Rec> records() {
        return KeysetPagingItemReaderBuilder.of(db, Rec.class).from("records").key("region", "seq").pageSize(3);
    }

    @Test
    @DisplayName("reads every row once, in key order, across pages of a composite key")
    void keyOrderAcrossPages() throws Exception {
        List<Rec> all = readAll(records().build(), new ExecutionContext(), Integer.MAX_VALUE);

        assertThat(all).hasSize(14);
        assertThat(all.getFirst()).isEqualTo(new Rec("eu", 1, "eu-1"));
        assertThat(all.get(7)).isEqualTo(new Rec("us", 1, "us-1"));
        assertThat(all).extracting(Rec::payload).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a restart resumes after the last key read, even when rows were deleted meanwhile")
    void restartResumesAfterTheLastKey() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Rec> first = readAll(records().build(), context, 5);
        // An offset would now be off by two; a key is not.
        jdbc.update("DELETE FROM records WHERE region = 'eu' AND seq IN (1, 2)");

        List<Rec> rest = readAll(records().build(), context, Integer.MAX_VALUE);

        assertThat(first).extracting(Rec::payload).containsExactly("eu-1", "eu-2", "eu-3", "eu-4", "eu-5");
        assertThat(rest).extracting(Rec::payload).containsExactly("eu-6", "eu-7",
                "us-1", "us-2", "us-3", "us-4", "us-5", "us-6", "us-7");
    }

    @Test
    @DisplayName("a where clause with named parameters narrows the rows")
    void whereWithParameters() throws Exception {
        var reader = records().where("region = :region AND seq > :after").parameter("region", "us")
                .parameter("after", 4L).build();

        assertThat(readAll(reader, new ExecutionContext(), 99)).extracting(Rec::payload)
                .containsExactly("us-5", "us-6", "us-7");
    }

    @Test
    @DisplayName("a key that is only part of the primary key is refused")
    void partialPrimaryKeyRefused() {
        assertThatThrownBy(() -> open(KeysetPagingItemReaderBuilder.of(db, Rec.class).from("records").key("seq")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not covered by its primary key or a unique index");
    }

    @Test
    @DisplayName("a table with no unique key is refused")
    void noUniqueKeyRefused() {
        assertThatThrownBy(() -> open(KeysetPagingItemReaderBuilder.of(db, Rec.class).from("loose").key("id")))
                .hasMessageContaining("not covered");
    }

    @Test
    @DisplayName("a nullable unique column is refused: its NULL rows would never be read")
    void nullableKeyRefused() {
        assertThatThrownBy(() -> open(KeysetPagingItemReaderBuilder.of(db, Rec.class).from("nullable_key").key("code")))
                .hasMessageContaining("nullable");
    }

    @Test
    @DisplayName("a unique index is a unique key, and a superset of a unique key is too")
    void uniqueIndexAccepted() {
        open(KeysetPagingItemReaderBuilder.of(db, (rs, i) -> rs.getLong(1)).from("indexed").key("id"));
        jdbc.execute("CREATE TABLE wide (id BIGINT NOT NULL PRIMARY KEY, part INT NOT NULL)");
        open(KeysetPagingItemReaderBuilder.of(db, (rs, i) -> rs.getLong(1)).from("wide").key("part", "id"));
    }

    @Test
    @DisplayName("a join cannot be verified and must opt out explicitly")
    void joinNeedsAnExplicitOptOut() throws Exception {
        assertThatThrownBy(() -> records().from("records r JOIN indexed i ON i.id = r.seq").build())
                .hasMessageContaining("verifyUniqueKey(false)");

        var reader = KeysetPagingItemReaderBuilder.of(db, (rs, i) -> rs.getString("payload"))
                .select("r.payload, r.region, r.seq").from("records r").key("region", "seq").verifyUniqueKey(false)
                .pageSize(4).build();
        assertThat(readAll(reader, new ExecutionContext(), 99)).hasSize(14);
    }

    @Test
    @DisplayName("a missing table, a missing key and an unsafe key column are refused")
    void definitionErrors() {
        assertThatThrownBy(() -> open(KeysetPagingItemReaderBuilder.of(db, Rec.class).from("nope").key("id")))
                .hasMessageContaining("does not exist");
        assertThatThrownBy(() -> KeysetPagingItemReaderBuilder.of(db, Rec.class).from("records").build())
                .hasMessageContaining("key(...) is required");
        assertThatThrownBy(() -> KeysetPagingItemReaderBuilder.of(db, Rec.class).key("id").build())
                .hasMessageContaining("from(...) is required");
        assertThatThrownBy(() -> records().key("seq; DROP TABLE records").build())
                .hasMessageContaining("not a plain column name");
    }

    @Test
    @DisplayName("the key is checked when the reader opens, not when it is built: a bean may precede the schema")
    void theKeyIsCheckedOnOpen() throws Exception {
        var reader = KeysetPagingItemReaderBuilder.of(db, Rec.class).from("created_later").key("id").build();
        reader.afterPropertiesSet();
        jdbc.execute("CREATE TABLE created_later (id BIGINT NOT NULL PRIMARY KEY, region VARCHAR(4), seq BIGINT,"
                + " payload VARCHAR(20))");

        assertThat(readAll(reader, new ExecutionContext(), 9)).isEmpty();
    }

    private static void open(KeysetPagingItemReaderBuilder<?> builder) {
        var reader = builder.build();
        reader.open(new ExecutionContext());
        reader.close();
    }

    @Test
    @DisplayName("the page size defaults to adhar.batch.page.size")
    void pageSizeDefault() {
        var reader = KeysetPagingItemReaderBuilder.of(db, Rec.class).from("records").key("region", "seq").name("r").build();

        assertThat(ReflectionTestUtils.getField(reader, "pageSize")).isEqualTo(500);
        assertThat(reader.getName()).isEqualTo("r");
    }
}
