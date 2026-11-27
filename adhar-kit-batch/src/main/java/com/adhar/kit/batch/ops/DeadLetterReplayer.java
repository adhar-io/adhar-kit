package com.adhar.kit.batch.ops;

import com.adhar.kit.batch.fault.DeadLetterReplayRegistry;
import com.adhar.kit.batch.fault.DeadLetterReplayRegistry.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Replays the items ONE execution dead-lettered (design 0025 §5.8, A3.6): each unreplayed row of
 * {@code ADHAR_BATCH_DEAD_LETTER} for that execution goes through its step's processor (an item
 * skipped in {@code process}) and writer again, and is marked {@code REPLAYED_AT} in the same
 * transaction as the write. Rows of other executions are never read.
 *
 * <p>Each item is its own transaction, which first claims the row
 * ({@code UPDATE ... WHERE REPLAYED_AT IS NULL}): a second replay of the same execution at the same
 * time finds it claimed and leaves it, and an item that fails again rolls its claim back with its
 * writes, so it stays rejected for the next replay. An item stays rejected, and is counted so, when
 * it fails again, when its step does not dead-letter in this application (or cannot be replayed,
 * {@link DeadLetterReplayRegistry}), when it was skipped in {@code read} or could not be kept as
 * JSON (there is no item), or when its JSON no longer reads as its type.</p>
 *
 * <p><b>One write per item across a restart (2026-11-22).</b> A write-phase dead letter commits in
 * a transaction of its own while Spring Batch scans the failed chunk item by item, so a step that
 * fails before that chunk commits leaves the dead letter behind, and its restart reads the chunk
 * again and dead-letters the same item a second time, under the new execution. Replaying both
 * would write it twice. So the rows of OTHER executions of the same JobInstance with the same step
 * name and the same item (its type and its JSON value, members in any order, compared by a SHA-256
 * hash) are its twins:
 * replaying one claims, in the same transaction, the first unreplayed twin of every other
 * execution, marks it replayed with {@code REPLAY_EXECUTION_ID} set to the replaying execution and
 * counts it as a duplicate instead of writing it; and a row whose twin another execution has
 * already written (a dead letter recorded after that replay) is itself only marked. An item that
 * fails again rolls the duplicates' claims back with its own. Rows of different steps or
 * instances, and different items that merely share a key, are never twins; neither are two equal
 * rows of ONE execution, which the step skipped twice because its input held the item twice. When
 * housekeeping has deleted the execution its instance is unknown, and nothing is deduplicated.
 * {@code REPLAY_EXECUTION_ID} names the execution whose replay wrote the item: the row's own for a
 * row written, the writer's for a duplicate.</p>
 *
 * <p>Step-scoped processors and writers resolve against the original step execution, so its job
 * parameters and step context are theirs again. When housekeeping has already deleted that
 * execution, a stand-in carrying only the row's business date is used.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class DeadLetterReplayer {

    /** How many rows are read at a time. */
    static final int PAGE = 200;

    /** Writes an item's JSON one way, for {@link #hash}. */
    private static final UnaryOperator<String> CANONICAL =
            ClassUtils.isPresent("tools.jackson.databind.json.JsonMapper", DeadLetterReplayer.class.getClassLoader())
                    ? Jackson.canonicalizer() : UnaryOperator.identity();

    private final JdbcOperations jdbc;
    private final String table;
    private final DeadLetterReplayRegistry routes;
    private final TransactionTemplate transactions;
    private final JobRepository jobRepository;
    private final Deserializer deserializer;
    private final Clock clock;

    /**
     * @param jdbc               the JobRepository's database
     * @param table              the dead-letter table, schema-qualified on PostgreSQL
     * @param routes             the dead-lettering steps' processors and writers
     * @param transactionManager the JobRepository's transaction manager, which the writers share
     * @param jobRepository      the original executions, for their step scope
     * @param deserializer       reads an item back from its JSON and Java type
     * @param clock              stamps {@code REPLAYED_AT}
     */
    public DeadLetterReplayer(JdbcOperations jdbc, String table, DeadLetterReplayRegistry routes,
                              PlatformTransactionManager transactionManager, JobRepository jobRepository,
                              Deserializer deserializer, Clock clock) {
        this.jdbc = jdbc;
        this.table = table;
        this.routes = routes;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jobRepository = jobRepository;
        this.deserializer = deserializer;
        this.clock = clock;
    }

    /**
     * @param executionId an execution
     * @return the jobs whose dead letters it holds: one, or none when it dead-lettered nothing
     */
    public List<String> jobsOf(long executionId) {
        return jdbc.queryForList("SELECT DISTINCT JOB_NAME FROM " + table + " WHERE JOB_EXECUTION_ID = ?",
                String.class, executionId);
    }

    /**
     * @param executionId an execution
     * @return how many of its dead letters are not replayed yet
     */
    public long pending(long executionId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE JOB_EXECUTION_ID = ? AND REPLAYED_AT IS NULL", Long.class, executionId);
        return count == null ? 0 : count;
    }

    /**
     * Replays every unreplayed dead letter of the execution.
     *
     * @param jobName     the job, for a stand-in execution
     * @param executionId the execution whose items are replayed
     * @return how many were replayed now, and how many stay rejected
     */
    public Result replay(String jobName, long executionId) {
        Tally tally = new Tally();
        List<String> steps = jdbc.queryForList("SELECT DISTINCT STEP_NAME FROM " + table
                + " WHERE JOB_EXECUTION_ID = ? AND REPLAYED_AT IS NULL ORDER BY STEP_NAME", String.class, executionId);
        JobExecution original;
        try {
            original = jobRepository.getJobExecution(executionId);
        } catch (EmptyResultDataAccessException ex) {
            original = null;
        }
        List<Long> siblings = original == null ? List.of() : jobRepository.getJobExecutions(original.getJobInstance())
                .stream().map(JobExecution::getId).filter(id -> id != executionId).sorted().toList();
        for (String step : steps) {
            replayStep(jobName, executionId, original, siblings, step, tally);
        }
        return new Result(tally.replayed, pending(executionId), tally.duplicates);
    }

    private void replayStep(String jobName, long executionId, JobExecution original, List<Long> siblings,
                            String stepName, Tally tally) {
        Optional<Route> found = routes.find(stepName);
        String problem = found.isEmpty() ? "no step named [" + stepName + "] dead-letters in this application"
                : found.get().problem();
        if (problem != null) {
            log.warn("{} dead letter(s) of step [{}] stay rejected: {}", jdbc.queryForObject("SELECT COUNT(*) FROM "
                    + table + " WHERE JOB_EXECUTION_ID = ? AND STEP_NAME = ? AND REPLAYED_AT IS NULL", Long.class,
                    executionId, stepName), stepName, problem);
            return;
        }
        Route route = found.get();
        Map<Long, List<Long>> twins = siblings.isEmpty() ? Map.of() : twins(executionId, siblings, stepName);
        long duplicates = tally.duplicates;
        long after = 0;
        StepSynchronizationManager.register(stepExecution(jobName, executionId, original, stepName));
        try {
            for (List<Row> page = rows(executionId, stepName, after, PAGE); !page.isEmpty();
                 page = rows(executionId, stepName, after, PAGE)) {
                for (Row row : page) {
                    after = row.id;
                    Done done = replay(route, row, executionId, twins.get(row.id));
                    tally.replayed += done.written ? 1 : 0;
                    tally.duplicates += done.duplicates;
                }
            }
        } finally {
            StepSynchronizationManager.close();
        }
        if (tally.duplicates > duplicates) {
            log.info("{} dead letter(s) of step [{}] held an item another execution of the same instance also "
                    + "dead-lettered: marked replayed with its one write", tally.duplicates - duplicates, stepName);
        }
    }

    /**
     * The twins of the execution's rows of one step: for each of its rows whose item another
     * execution of the instance dead-lettered in the same step too, the ids of every row of that
     * item there and here, ascending.
     */
    private Map<Long, List<Long>> twins(long executionId, List<Long> siblings, String stepName) {
        List<Long> executions = new ArrayList<>(siblings);
        executions.add(executionId);
        Object[] arguments = new Object[executions.size() + 1];
        arguments[0] = stepName;
        for (int i = 0; i < executions.size(); i++) {
            arguments[i + 1] = executions.get(i);
        }
        Map<String, List<long[]>> byItem = new HashMap<>();
        jdbc.query("SELECT ID, JOB_EXECUTION_ID, ITEM_TYPE, ITEM FROM " + table + " WHERE STEP_NAME = ? AND "
                        + "ITEM_TYPE IS NOT NULL AND ITEM IS NOT NULL AND JOB_EXECUTION_ID IN (" + placeholders(executions)
                        + ")",
                resultSet -> {
                    byItem.computeIfAbsent(hash(resultSet.getString(3), resultSet.getString(4)),
                            key -> new ArrayList<>()).add(new long[] {resultSet.getLong(1), resultSet.getLong(2)});
                }, arguments);
        Map<Long, List<Long>> twins = new HashMap<>();
        for (List<long[]> rows : byItem.values()) {
            if (rows.stream().noneMatch(row -> row[1] == executionId)
                    || rows.stream().allMatch(row -> row[1] == executionId)) {
                continue;
            }
            List<Long> ids = Collections.unmodifiableList(rows.stream().map(row -> row[0]).sorted().toList());
            rows.stream().filter(row -> row[1] == executionId).forEach(row -> twins.put(row[0], ids));
        }
        return twins;
    }

    private static String placeholders(List<Long> values) {
        return values.stream().map(value -> "?").collect(Collectors.joining(", "));
    }

    /**
     * @param itemType the item's Java type, as stored
     * @param item     its JSON, as stored
     * @return a stable hash of the two: equal for the same item, never for different types. The
     *         same item is the same JSON VALUE ({@link #canonical}), not the same text: PostgreSQL's
     *         JSONB already reads every row back in one form, a text column of an application's own
     *         table does not, and a release that reorders an item's fields between the failed run
     *         and its restart must not turn one item into two
     */
    static String hash(String itemType, String item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(itemType.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(canonical(item).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("every JVM has SHA-256", ex);
        }
    }

    /**
     * @param item an item's JSON
     * @return the same JSON value written one way: every object's members in name order, no
     *         whitespace, decimals as written (never rounded through a double, which would make two
     *         different amounts one item); the text unchanged when it is not JSON or Jackson 3 is
     *         absent (when no item can be read back, so none is written either)
     */
    static String canonical(String item) {
        return CANONICAL.apply(item);
    }

    /**
     * @param twins the ids of every row holding the same item in this and other executions of the
     *              instance, {@code null} when no other execution dead-lettered it
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Done replay(Route route, Row row, long executionId, List<Long> twins) {
        if (row.itemType == null || row.item == null) {
            log.warn("Dead letter {} of step [{}] stays rejected: it was skipped in {} and kept no item to replay",
                    row.id, row.step, row.phase);
            return Done.NONE;
        }
        Object item;
        try {
            item = deserializer.read(row.itemType, row.item);
        } catch (Exception ex) {
            log.warn("Dead letter {} of step [{}] stays rejected: its item no longer reads as {} ({})", row.id,
                    row.step, row.itemType, ex.getClass().getName());
            return Done.NONE;
        }
        try {
            Done done = transactions.execute(status -> {
                List<Long> duplicates = List.of();
                if (twins != null) {
                    List<Twin> group = lock(twins);
                    if (group.stream().noneMatch(twin -> twin.id == row.id && !twin.replayed)) {
                        return Done.NONE;
                    }
                    Long writer = earlierWrite(group, executionId);
                    if (writer != null) {
                        log.debug("Dead letter {} of step [{}] holds an item the replay of execution {} wrote",
                                row.id, row.step, writer);
                        return claim(row.id, writer) ? new Done(false, 1) : Done.NONE;
                    }
                    duplicates = unclaimedOfOthers(group, executionId);
                }
                if (!claim(row.id, executionId)) {
                    return Done.NONE;
                }
                long marked = 0;
                for (long duplicate : duplicates) {
                    marked += claim(duplicate, executionId) ? 1 : 0;
                }
                try {
                    Object output = item;
                    if ("process".equals(row.phase) && route.processor() != null) {
                        output = ((ItemProcessor) route.processor()).process(item);
                    }
                    if (output != null) {
                        ((ItemWriter) route.writer()).write(Chunk.of(output));
                    }
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new FailedAgain(ex);
                }
                return new Done(true, marked);
            });
            return done == null ? Done.NONE : done;
        } catch (RuntimeException ex) {
            Throwable cause = ex instanceof FailedAgain ? ex.getCause() : ex;
            log.warn("Dead letter {} of step [{}] stays rejected: it failed again with {}", row.id, row.step,
                    cause.getClass().getName());
            log.debug("Dead letter {} failed again", row.id, cause);
            return Done.NONE;
        }
    }

    /**
     * Marks a row replayed by the execution whose replay wrote its item, unless another replay
     * claimed it first.
     */
    private boolean claim(long id, long writer) {
        return jdbc.update("UPDATE " + table + " SET REPLAYED_AT = ?, REPLAY_EXECUTION_ID = ? WHERE ID = ? "
                + "AND REPLAYED_AT IS NULL", Timestamp.from(clock.instant()), writer, id) > 0;
    }

    /**
     * Locks an item's rows in id order and reads them as they now are: two replays of executions
     * of one instance at the same time take turns on the item instead of both writing it, and
     * cannot deadlock on it.
     */
    private List<Twin> lock(List<Long> ids) {
        return jdbc.query("SELECT ID, JOB_EXECUTION_ID, REPLAYED_AT, REPLAY_EXECUTION_ID FROM " + table
                        + " WHERE ID IN (" + placeholders(ids) + ") ORDER BY ID FOR UPDATE",
                (resultSet, number) -> {
                    long id = resultSet.getLong(1);
                    long execution = resultSet.getLong(2);
                    boolean replayed = resultSet.getTimestamp(3) != null;
                    long by = resultSet.getLong(4);
                    return new Twin(id, execution, replayed, resultSet.wasNull() ? null : by);
                }, ids.toArray());
    }

    /**
     * @return the first other execution whose replay wrote the item more often than rows of this
     *         execution were marked as its duplicates, {@code null} when none did
     */
    private static Long earlierWrite(List<Twin> group, long executionId) {
        for (long other : new TreeSet<>(group.stream().map(Twin::executionId).toList())) {
            if (other == executionId) {
                continue;
            }
            long written = group.stream().filter(twin -> twin.executionId == other && twin.wrote()).count();
            long matched = group.stream().filter(twin -> twin.executionId == executionId && twin.replayed
                    && twin.replayedBy != null && twin.replayedBy == other).count();
            if (written > matched) {
                return other;
            }
        }
        return null;
    }

    /**
     * @return the first unreplayed row of every other execution: the duplicates of the write
     */
    private static List<Long> unclaimedOfOthers(List<Twin> group, long executionId) {
        Map<Long, Long> first = new HashMap<>();
        for (Twin twin : group) {
            if (twin.executionId != executionId && !twin.replayed) {
                first.merge(twin.executionId, twin.id, Math::min);
            }
        }
        return first.values().stream().sorted().toList();
    }

    private StepExecution stepExecution(String jobName, long executionId, JobExecution original, String stepName) {
        if (original != null) {
            Optional<StepExecution> step = original.getStepExecutions().stream()
                    .filter(candidate -> candidate.getStepName().equals(stepName))
                    .max(Comparator.comparingLong(StepExecution::getId));
            if (step.isPresent()) {
                return step.get();
            }
            return new StepExecution(0L, stepName, original);
        }
        List<LocalDate> dates = jdbc.queryForList("SELECT DISTINCT BUSINESS_DATE FROM " + table
                + " WHERE JOB_EXECUTION_ID = ? AND BUSINESS_DATE IS NOT NULL", Date.class, executionId)
                .stream().map(Date::toLocalDate).toList();
        JobParametersBuilder parameters = new JobParametersBuilder();
        if (dates.size() == 1) {
            parameters.addLocalDate("businessDate", dates.getFirst());
        }
        JobParameters stand = parameters.toJobParameters();
        return new StepExecution(0L, stepName, new JobExecution(executionId, new JobInstance(0L, jobName), stand));
    }

    private List<Row> rows(long executionId, String stepName, long after, int limit) {
        return jdbc.query("SELECT ID, STEP_NAME, PHASE, ITEM_TYPE, ITEM FROM " + table + " WHERE JOB_EXECUTION_ID = ? "
                        + "AND STEP_NAME = ? AND REPLAYED_AT IS NULL AND ID > ? ORDER BY ID LIMIT " + limit,
                (resultSet, number) -> new Row(resultSet.getLong(1), resultSet.getString(2), resultSet.getString(3),
                        resultSet.getString(4), resultSet.getString(5)),
                executionId, stepName, after);
    }

    /** A checked failure of a processor or writer, carried out of the transaction callback. */
    private static final class FailedAgain extends RuntimeException {

        FailedAgain(Exception cause) {
            super(cause);
        }
    }

    /** One unreplayed dead letter. */
    private record Row(long id, String step, String phase, String itemType, String item) {
    }

    /**
     * A row holding an item another execution of the instance dead-lettered too.
     *
     * @param replayedBy {@code REPLAY_EXECUTION_ID}, {@code null} for a row replayed before the
     *                   replay kept it
     */
    private record Twin(long id, long executionId, boolean replayed, Long replayedBy) {

        /** Whether its own replay wrote its item. */
        boolean wrote() {
            return replayed && (replayedBy == null || replayedBy == executionId);
        }
    }

    /**
     * What one row's replay did.
     *
     * @param written    whether it wrote the row's item
     * @param duplicates how many rows it marked replayed without writing them
     */
    private record Done(boolean written, long duplicates) {

        static final Done NONE = new Done(false, 0);
    }

    /** The counts of one replay. */
    private static final class Tally {

        private long replayed;
        private long duplicates;
    }

    /**
     * @param replayed   the items replayed (written) by this call
     * @param rejected   the execution's items still not replayed
     * @param duplicates the dead letters this call marked replayed without writing them, because a
     *                   dead letter of the same item, step and instance under another execution was
     *                   written: the second dead letter a restart left for one item
     */
    public record Result(long replayed, long rejected, long duplicates) {
    }

    /** Reads a dead-lettered item back. */
    @FunctionalInterface
    public interface Deserializer {

        /**
         * @param type the item's Java type, as the dead letter recorded it
         * @param json the item as JSON
         * @return the item
         * @throws Exception when it cannot be read
         */
        Object read(String type, String json) throws Exception;

        /**
         * @return Jackson 3 when the application has it (as {@code DeadLetterSkipListener} wrote the
         *         item with it), otherwise a deserializer that refuses every item
         */
        static Deserializer standard() {
            ClassLoader loader = DeadLetterReplayer.class.getClassLoader();
            if (!ClassUtils.isPresent("tools.jackson.databind.json.JsonMapper", loader)) {
                return (type, json) -> {
                    throw new IllegalStateException("Jackson 3 (tools.jackson.core:jackson-databind) is not on the "
                            + "classpath, so a dead-lettered item cannot be read back");
                };
            }
            return Jackson.deserializer(loader);
        }
    }

    /** Loaded only when Jackson 3 is on the classpath. */
    private static final class Jackson {

        static Deserializer deserializer(ClassLoader loader) {
            tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.shared();
            return (type, json) -> mapper.readValue(json, ClassUtils.forName(type, loader));
        }

        static UnaryOperator<String> canonicalizer() {
            tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                    .enable(tools.jackson.databind.cfg.JsonNodeFeature.WRITE_PROPERTIES_SORTED,
                            tools.jackson.databind.cfg.JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .build();
            return json -> {
                try {
                    return mapper.writeValueAsString(mapper.readTree(json));
                } catch (tools.jackson.core.JacksonException ex) {
                    return json;
                }
            };
        }
    }
}
