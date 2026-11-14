package com.adhar.kit.batch.partitioner;

import org.springframework.batch.core.partition.PartitionNameProvider;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Splits a table into key ranges, one per partition. Each partition's context carries
 * {@value #MIN_KEY} and {@value #MAX_KEY} (both inclusive), plus {@value #PARTITION_INDEX} and
 * {@value #PARTITION_COUNT}; the worker's reader selects {@code key BETWEEN :minKey AND :maxKey}.
 *
 * <ul>
 *   <li>{@link Strategy#NTILE} (default): equal row counts, from {@code NTILE(n)} over the DISTINCT
 *       keys, so no key value lands in two partitions even when the column is not unique.</li>
 *   <li>{@link Strategy#MIN_MAX}: equal-width ranges of an integer key between {@code MIN} and
 *       {@code MAX}; one query on an index, but gaps in the key make the partitions uneven.</li>
 * </ul>
 *
 * <p>It always creates exactly {@code gridSize} partitions, named {@code partition0…}: a range
 * with no rows is a partition with no rows ({@value #EMPTY} = true). That keeps the names
 * stable, which is what a restart needs: {@link PartitionNameProvider} returns them without querying
 * again, and Spring Batch resumes each unfinished partition with the range it recorded, however the
 * table has changed since.</p>
 *
 * @since 0.1.0
 */
public final class SqlKeyRangePartitioner implements Partitioner, PartitionNameProvider {

    /** Lowest key of the partition, inclusive. */
    public static final String MIN_KEY = "minKey";
    /** Highest key of the partition, inclusive. */
    public static final String MAX_KEY = "maxKey";
    /** The partition's index, 0-based. */
    public static final String PARTITION_INDEX = "partitionIndex";
    /** How many partitions there are. */
    public static final String PARTITION_COUNT = "partitionCount";
    /** True for a partition with no rows (its keys are absent). */
    public static final String EMPTY = "empty";
    /** Prefix of every partition's name. */
    public static final String NAME_PREFIX = "partition";

    /** How the ranges are computed. */
    public enum Strategy { NTILE, MIN_MAX }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final NamedParameterJdbcTemplate jdbc;
    private final String table;
    private final String key;
    private final String where;
    private final Map<String, Object> parameters;
    private final Strategy strategy;

    private SqlKeyRangePartitioner(Builder builder) {
        this.jdbc = new NamedParameterJdbcTemplate(builder.dataSource);
        this.table = builder.table;
        this.key = builder.key;
        this.where = builder.where;
        this.parameters = Map.copyOf(builder.parameters);
        this.strategy = builder.strategy;
    }

    /**
     * @param dataSource the database
     * @param table      the table, optionally schema-qualified
     * @param key        the key column
     * @return a builder
     */
    public static Builder of(DataSource dataSource, String table, String key) {
        return new Builder(dataSource, table, key);
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<Object[]> ranges = strategy == Strategy.NTILE ? ntile(gridSize) : minMax(gridSize);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        for (int index = 0; index < gridSize; index++) {
            ExecutionContext context = new ExecutionContext();
            context.putInt(PARTITION_INDEX, index);
            context.putInt(PARTITION_COUNT, gridSize);
            Object[] range = index < ranges.size() ? ranges.get(index) : null;
            context.put(EMPTY, range == null);
            if (range != null) {
                context.put(MIN_KEY, range[0]);
                context.put(MAX_KEY, range[1]);
            }
            partitions.put(NAME_PREFIX + index, context);
        }
        return partitions;
    }

    @Override
    public List<String> getPartitionNames(int gridSize) {
        return IntStream.range(0, gridSize).mapToObj(index -> NAME_PREFIX + index).toList();
    }

    private String condition() {
        return where == null || where.isBlank() ? "" : " WHERE " + where;
    }

    private List<Object[]> ntile(int gridSize) {
        String sql = "SELECT bucket, MIN(k) AS lo, MAX(k) AS hi FROM (SELECT k, NTILE(:adharGridSize) OVER (ORDER BY k) AS bucket"
                + " FROM (SELECT DISTINCT " + key + " AS k FROM " + table + condition() + ") keys) buckets"
                + " GROUP BY bucket ORDER BY bucket";
        MapSqlParameterSource source = new MapSqlParameterSource(parameters).addValue("adharGridSize", gridSize);
        return jdbc.query(sql, source, (rs, row) -> new Object[] {rs.getObject("lo"), rs.getObject("hi")});
    }

    private List<Object[]> minMax(int gridSize) {
        String sql = "SELECT MIN(" + key + ") AS lo, MAX(" + key + ") AS hi FROM " + table + condition();
        Object[] bounds = jdbc.queryForObject(sql, parameters, (rs, row) -> new Object[] {rs.getObject("lo"), rs.getObject("hi")});
        List<Object[]> ranges = new ArrayList<>();
        if (bounds == null || bounds[0] == null) {
            return ranges;
        }
        if (!(bounds[0] instanceof Number low) || !(bounds[1] instanceof Number high)) {
            throw new IllegalStateException("MIN_MAX partitions an integer key; " + table + "." + key + " is "
                    + bounds[0].getClass().getSimpleName() + " (use NTILE)");
        }
        long min = low.longValue();
        long max = high.longValue();
        long width = Math.floorDiv(Math.subtractExact(max, min), gridSize) + 1;
        for (long start = min; start <= max && ranges.size() < gridSize; start += width) {
            long end = ranges.size() == gridSize - 1 ? max : Math.min(max, start + width - 1);
            ranges.add(new Object[] {start, end});
            if (end == max) {
                break;
            }
        }
        return ranges;
    }

    /** Builds a {@link SqlKeyRangePartitioner}. */
    public static final class Builder {

        private final DataSource dataSource;
        private final String table;
        private final String key;
        private String where;
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private Strategy strategy = Strategy.NTILE;

        private Builder(DataSource dataSource, String table, String key) {
            this.dataSource = dataSource;
            this.table = table;
            this.key = key;
        }

        /**
         * @param where a condition with {@code :named} parameters, selecting the rows to partition
         * @return this builder
         */
        public Builder where(String where) {
            this.where = where;
            return this;
        }

        /**
         * @param name  a parameter of the condition
         * @param value its value
         * @return this builder
         */
        public Builder parameter(String name, Object value) {
            parameters.put(name, value);
            return this;
        }

        /**
         * @param strategy how the ranges are computed
         * @return this builder
         */
        public Builder strategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * @return the partitioner
         */
        public SqlKeyRangePartitioner build() {
            if (table == null || !TABLE.matcher(table).matches()) {
                throw new IllegalStateException("'" + table + "' is not a plain, optionally schema-qualified table name");
            }
            if (key == null || !IDENTIFIER.matcher(key).matches()) {
                throw new IllegalStateException("'" + key + "' is not a plain column name");
            }
            return new SqlKeyRangePartitioner(this);
        }
    }
}
