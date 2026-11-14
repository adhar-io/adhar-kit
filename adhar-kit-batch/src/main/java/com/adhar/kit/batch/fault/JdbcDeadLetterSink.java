package com.adhar.kit.batch.fault;

import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Date;
import java.util.regex.Pattern;

/**
 * Writes dead letters to the kit's {@code ADHAR_BATCH_DEAD_LETTER} table (migration
 * {@code V6_0_1}), on the JobRepository's database so a dead letter commits with its chunk.
 * Values longer than their column are cut, never refused: losing the tail of a message is better
 * than failing the chunk that is trying to set an item aside. For the same reason a NUL, which
 * PostgreSQL text and JSONB refuse, is kept as U+FFFD (2026-11-14): malformed input is what a
 * step skips.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class JdbcDeadLetterSink implements DeadLetterSink {

    /** A JSON escape of NUL (backslash, u0000) that is not itself an escaped backslash. */
    private static final Pattern JSON_NUL = Pattern.compile("(?<!\\\\)((?:\\\\\\\\)*)\\\\u0000");

    private final JdbcOperations jdbc;
    private final String sql;

    /**
     * @param jdbc  the JobRepository's database
     * @param table the table name, schema-qualified on PostgreSQL
     * @param jsonb whether {@code ITEM} is PostgreSQL {@code JSONB} (the kit's schema), which a
     *              string parameter must be cast to
     */
    public JdbcDeadLetterSink(JdbcOperations jdbc, String table, boolean jsonb) {
        this.jdbc = jdbc;
        this.sql = "INSERT INTO " + table + " (JOB_EXECUTION_ID, JOB_NAME, STEP_NAME, BUSINESS_DATE, PHASE, "
                + "ITEM_TYPE, ITEM, ERROR_CLASS, ERROR_MESSAGE) VALUES (?, ?, ?, ?, ?, ?, "
                + (jsonb ? "CAST(? AS JSONB)" : "?") + ", ?, ?)";
    }

    @Override
    public void write(Entry entry) {
        jdbc.update(sql, entry.jobExecutionId(), cut(entry.jobName(), 100), cut(entry.stepName(), 100),
                entry.businessDate() == null ? null : Date.valueOf(entry.businessDate()), entry.phase(),
                cut(entry.itemType(), 500), json(entry.item()), cut(entry.errorClass(), 500),
                cut(entry.errorMessage(), 2500));
    }

    /**
     * @return the INSERT statement
     */
    String sql() {
        return sql;
    }

    static String cut(String value, int max) {
        String text = noNul(value);
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    static String json(String item) {
        return item == null ? null : JSON_NUL.matcher(noNul(item)).replaceAll("$1\\\\ufffd");
    }

    private static String noNul(String value) {
        return value == null ? null : value.replace('\u0000', '\uFFFD');
    }
}
