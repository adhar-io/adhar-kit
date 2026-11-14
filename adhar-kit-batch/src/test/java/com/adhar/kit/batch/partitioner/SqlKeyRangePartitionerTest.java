package com.adhar.kit.batch.partitioner;

import com.adhar.kit.batch.partitioner.SqlKeyRangePartitioner.Strategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SqlKeyRangePartitioner} on H2: every key in exactly one partition, a fixed partition count,
 * and both strategies.
 */
class SqlKeyRangePartitionerTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("ranges-" + System.nanoTime()).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE items (id BIGINT NOT NULL PRIMARY KEY, grp INT NOT NULL, region VARCHAR(4) NOT NULL)");
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private void seed(long from, long to) {
        for (long id = from; id <= to; id++) {
            jdbc.update("INSERT INTO items VALUES (?, ?, ?)", id, id % 3, id % 2 == 0 ? "eu" : "us");
        }
    }

    /** How many partitions contain each id, by running each partition's BETWEEN query. */
    private List<Long> idsByPartition(Map<String, ExecutionContext> partitions, String column) {
        List<Long> ids = new ArrayList<>();
        partitions.values().stream().filter(ctx -> !((Boolean) ctx.get(SqlKeyRangePartitioner.EMPTY))).forEach(ctx ->
                ids.addAll(jdbc.queryForList("SELECT id FROM items WHERE " + column + " BETWEEN ? AND ?", Long.class,
                        ctx.get(SqlKeyRangePartitioner.MIN_KEY), ctx.get(SqlKeyRangePartitioner.MAX_KEY))));
        return ids;
    }

    @Test
    @DisplayName("NTILE: equal row counts, every id in exactly one partition, keys inclusive")
    void ntileEqualCounts() {
        seed(1, 40);
        jdbc.update("DELETE FROM items WHERE id BETWEEN 5 AND 14");

        var partitions = SqlKeyRangePartitioner.of(db, "items", "id").build().partition(3);

        assertThat(partitions.keySet()).containsExactly("partition0", "partition1", "partition2");
        assertThat(partitions.values()).extracting(ctx -> ctx.getInt(SqlKeyRangePartitioner.PARTITION_COUNT))
                .containsOnly(3);
        List<Long> ids = idsByPartition(partitions, "id");
        assertThat(ids).hasSize(30).doesNotHaveDuplicates();
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM items WHERE id BETWEEN ? AND ?", Integer.class,
                partitions.get("partition0").get("minKey"), partitions.get("partition0").get("maxKey"))).containsExactly(10);
    }

    @Test
    @DisplayName("NTILE over a non-unique column never puts one value in two partitions")
    void ntileNonUniqueColumn() {
        seed(1, 30);

        var partitions = SqlKeyRangePartitioner.of(db, "items", "grp").build().partition(2);

        assertThat(idsByPartition(partitions, "grp")).hasSize(30).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("MIN_MAX: contiguous equal-width integer ranges covering MIN..MAX")
    void minMaxContiguous() {
        seed(1, 40);

        var partitions = SqlKeyRangePartitioner.of(db, "items", "id").strategy(Strategy.MIN_MAX).build().partition(4);

        assertThat(partitions.values()).extracting(ctx -> ctx.getLong("minKey") + "-" + ctx.getLong("maxKey"))
                .containsExactly("1-10", "11-20", "21-30", "31-40");
        assertThat(idsByPartition(partitions, "id")).hasSize(40).doesNotHaveDuplicates();
        seed(41, 43);
        assertThat(idsByPartition(SqlKeyRangePartitioner.of(db, "items", "id").strategy(Strategy.MIN_MAX).build()
                .partition(4), "id")).hasSize(43).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("there are always gridSize partitions with stable names; ranges with no rows are empty")
    void alwaysGridSizePartitions() {
        seed(1, 2);
        var partitioner = SqlKeyRangePartitioner.of(db, "items", "id").build();

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(4);
        assertThat(partitions.values()).extracting(ctx -> ((Boolean) ctx.get(SqlKeyRangePartitioner.EMPTY)))
                .containsExactly(false, false, true, true);
        assertThat(partitions.get("partition3").containsKey("minKey")).isFalse();
        assertThat(partitioner.getPartitionNames(4)).containsExactlyElementsOf(partitions.keySet());
        assertThat(SqlKeyRangePartitioner.of(db, "items", "id").strategy(Strategy.MIN_MAX).build().partition(4)
                .values()).extracting(ctx -> ((Boolean) ctx.get(SqlKeyRangePartitioner.EMPTY)))
                .containsExactly(false, false, true, true);
        jdbc.update("DELETE FROM items");
        assertThat(partitioner.partition(2).values()).allSatisfy(ctx -> assertThat(((Boolean) ctx.get("empty"))).isTrue());
    }

    @Test
    @DisplayName("a where clause with named parameters selects the rows partitioned")
    void whereClause() {
        seed(1, 40);

        var partitions = SqlKeyRangePartitioner.of(db, "items", "id").where("region = :region").parameter("region", "eu")
                .build().partition(2);

        assertThat(partitions.get("partition0").getLong("minKey")).isEqualTo(2);
        assertThat(partitions.get("partition1").getLong("maxKey")).isEqualTo(40);
    }

    @Test
    @DisplayName("MIN_MAX refuses a non-integer key, and unsafe identifiers are refused")
    void definitionErrors() {
        seed(1, 3);
        assertThatThrownBy(() -> SqlKeyRangePartitioner.of(db, "items", "region").strategy(Strategy.MIN_MAX).build()
                .partition(2)).hasMessageContaining("use NTILE");
        assertThatThrownBy(() -> SqlKeyRangePartitioner.of(db, "items; DROP TABLE items", "id").build())
                .hasMessageContaining("not a plain");
        assertThatThrownBy(() -> SqlKeyRangePartitioner.of(db, "items", "id)").build())
                .hasMessageContaining("not a plain column name");
    }
}
