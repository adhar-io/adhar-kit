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

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        long replayed = 0;
        List<String> steps = jdbc.queryForList("SELECT DISTINCT STEP_NAME FROM " + table
                + " WHERE JOB_EXECUTION_ID = ? AND REPLAYED_AT IS NULL ORDER BY STEP_NAME", String.class, executionId);
        JobExecution original;
        try {
            original = jobRepository.getJobExecution(executionId);
        } catch (EmptyResultDataAccessException ex) {
            original = null;
        }
        for (String step : steps) {
            replayed += replayStep(jobName, executionId, original, step);
        }
        return new Result(replayed, pending(executionId));
    }

    private long replayStep(String jobName, long executionId, JobExecution original, String stepName) {
        Optional<Route> found = routes.find(stepName);
        String problem = found.isEmpty() ? "no step named [" + stepName + "] dead-letters in this application"
                : found.get().problem();
        if (problem != null) {
            log.warn("{} dead letter(s) of step [{}] stay rejected: {}", jdbc.queryForObject("SELECT COUNT(*) FROM "
                    + table + " WHERE JOB_EXECUTION_ID = ? AND STEP_NAME = ? AND REPLAYED_AT IS NULL", Long.class,
                    executionId, stepName), stepName, problem);
            return 0;
        }
        Route route = found.get();
        long replayed = 0;
        long after = 0;
        StepSynchronizationManager.register(stepExecution(jobName, executionId, original, stepName));
        try {
            for (List<Row> page = rows(executionId, stepName, after, PAGE); !page.isEmpty();
                 page = rows(executionId, stepName, after, PAGE)) {
                for (Row row : page) {
                    after = row.id;
                    if (replay(route, row)) {
                        replayed++;
                    }
                }
            }
        } finally {
            StepSynchronizationManager.close();
        }
        return replayed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean replay(Route route, Row row) {
        if (row.itemType == null || row.item == null) {
            log.warn("Dead letter {} of step [{}] stays rejected: it was skipped in {} and kept no item to replay",
                    row.id, row.step, row.phase);
            return false;
        }
        Object item;
        try {
            item = deserializer.read(row.itemType, row.item);
        } catch (Exception ex) {
            log.warn("Dead letter {} of step [{}] stays rejected: its item no longer reads as {} ({})", row.id,
                    row.step, row.itemType, ex.getClass().getName());
            return false;
        }
        try {
            Boolean done = transactions.execute(status -> {
                int claimed = jdbc.update("UPDATE " + table + " SET REPLAYED_AT = ? WHERE ID = ? AND REPLAYED_AT IS NULL",
                        Timestamp.from(clock.instant()), row.id);
                if (claimed == 0) {
                    return false;
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
                return true;
            });
            return Boolean.TRUE.equals(done);
        } catch (RuntimeException ex) {
            Throwable cause = ex instanceof FailedAgain ? ex.getCause() : ex;
            log.warn("Dead letter {} of step [{}] stays rejected: it failed again with {}", row.id, row.step,
                    cause.getClass().getName());
            log.debug("Dead letter {} failed again", row.id, cause);
            return false;
        }
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
     * @param replayed the items replayed by this call
     * @param rejected the execution's items still not replayed
     */
    public record Result(long replayed, long rejected) {
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
    }
}
