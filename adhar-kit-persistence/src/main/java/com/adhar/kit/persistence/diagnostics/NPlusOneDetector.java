package com.adhar.kit.persistence.diagnostics;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Hibernate {@link StatementInspector} that heuristically detects N+1 query patterns.
 *
 * <p>Every SQL statement Hibernate is about to prepare passes through
 * {@link #inspect(String)}. This inspector keeps a per-thread tally of how many times each
 * identical statement has been seen since the last {@link #reset()} (a thread typically maps to a
 * single transaction / request). Once the same statement has executed more than the configured
 * {@code threshold} times, a single warning is logged naming the repeated statement -- the classic
 * signature of a lazy association being loaded row-by-row inside a loop.</p>
 *
 * <p>The inspector never rewrites the SQL: {@link #inspect(String)} always returns its argument
 * unchanged. It is disabled by default and intended for use in development / test environments.</p>
 *
 * <p>Thread-safety: state is held in a {@link ThreadLocal}, so concurrent requests are counted
 * independently without contention.</p>
 *
 * @author Adhar Platform Team
 * @since 1.4.0
 */
public class NPlusOneDetector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(NPlusOneDetector.class);

    private final int threshold;

    /** Per-thread map of normalized-statement -> execution count since the last reset. */
    private final ThreadLocal<Map<String, Integer>> counts = ThreadLocal.withInitial(HashMap::new);
    /** Per-thread set (reused map) of statements already warned about, to avoid log spam. */
    private final ThreadLocal<Map<String, Boolean>> warned = ThreadLocal.withInitial(HashMap::new);

    /**
     * @param threshold repetitions of an identical statement (on one thread) that trigger a
     *                  warning; values below 1 are treated as 1
     */
    public NPlusOneDetector(int threshold) {
        this.threshold = Math.max(1, threshold);
        log.info("NPlusOneDetector initialized (threshold={})", this.threshold);
    }

    @Override
    public String inspect(String sql) {
        if (sql == null) {
            return null;
        }
        String normalized = sql.trim();
        Map<String, Integer> map = counts.get();
        int count = map.merge(normalized, 1, Integer::sum);

        if (count > threshold && !warned.get().containsKey(normalized)) {
            warned.get().put(normalized, Boolean.TRUE);
            log.warn("Possible N+1 query detected: the following statement has executed {} times "
                            + "on this thread (threshold {}). Consider a JOIN FETCH, an entity graph, "
                            + "or batch fetching. Statement: {}",
                    count, threshold, normalized);
        }
        return sql;
    }

    /**
     * Clears the per-thread counters. Call this at a transaction / request boundary so counts do
     * not leak across units of work (e.g. from a {@code TransactionSynchronization} or a servlet
     * filter). Safe to call even if nothing was recorded.
     */
    public void reset() {
        counts.get().clear();
        warned.get().clear();
    }

    /**
     * Fully removes this thread's state (counters and warning set). Prefer this over
     * {@link #reset()} on pooled threads to avoid retaining empty maps.
     */
    public void remove() {
        counts.remove();
        warned.remove();
    }

    /**
     * Returns the current execution count for the given statement on the calling thread.
     * Exposed primarily for testing.
     *
     * @param sql the statement (trimmed for comparison)
     * @return the number of times the statement has been inspected since the last reset
     */
    public int currentCount(String sql) {
        if (sql == null) {
            return 0;
        }
        return counts.get().getOrDefault(sql.trim(), 0);
    }

    /**
     * @return the configured repetition threshold
     */
    public int getThreshold() {
        return threshold;
    }
}
