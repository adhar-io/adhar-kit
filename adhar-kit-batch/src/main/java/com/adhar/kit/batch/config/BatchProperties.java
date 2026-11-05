package com.adhar.kit.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configuration properties of the Adhar Batch module ({@code adhar.batch.*}).
 *
 * <pre>{@code
 * adhar:
 *   batch:
 *     enabled: true                 # the module also needs at least one Job bean
 *     repository:
 *       schema: batch               # the JobRepository's own PostgreSQL schema
 *       table-prefix: BATCH_        # -> spring.batch.jdbc.table-prefix = batch.BATCH_
 *       migrate: true               # the kit's Flyway migrations of that schema
 *       allow-in-memory: false      # tests only
 *     shutdown:
 *       timeout: 90s                # graceful stop of running jobs on shutdown
 *     chunk:
 *       size: 100                   # AdharSteps.chunk(name)
 *     page:
 *       size: 500                   # the kit's paging readers
 *     retry:                        # FaultTolerance retry presets
 *       max-attempts: 3
 *       initial-backoff: 1s
 *       max-backoff: 30s
 *     scheduler:                    # embedded mode - not the platform path
 *       enabled: false
 *       time-zone: UTC
 *       misfire-threshold: 10m
 *       lock: jdbc
 *       jobs:
 *         importJob: "0 0 2 * * *"
 * }</pre>
 *
 * <p>Earlier keys are gone, not aliased: {@code table-prefix} is {@code repository.table-prefix},
 * {@code default-chunk-size} is {@code chunk.size}, {@code default-page-size} is
 * {@code page.size}, {@code max-retries} is {@code retry.max-attempts};
 * {@code max-concurrent-jobs} (the asynchronous launcher) and {@code retry-on-failure} were
 * removed.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "adhar.batch")
public class BatchProperties {

    /** A lower-case SQL identifier, which PostgreSQL never needs to quote. */
    static final Pattern SCHEMA_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    /** Letters, digits and underscores: the prefix becomes part of unquoted table names. */
    static final Pattern TABLE_PREFIX = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,29}");

    /**
     * Enable the batch module. The auto-configuration also requires at least one {@code Job}
     * bean, so a service without jobs gets none of the module's beans.
     */
    private boolean enabled = true;

    private final Repository repository = new Repository();

    private final Shutdown shutdown = new Shutdown();

    private final Chunk chunk = new Chunk();

    private final Page page = new Page();

    private final Retry retry = new Retry();

    private final Scheduler scheduler = new Scheduler();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Repository getRepository() {
        return repository;
    }

    public Shutdown getShutdown() {
        return shutdown;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public Page getPage() {
        return page;
    }

    public Retry getRetry() {
        return retry;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Checks the values a misconfiguration would otherwise turn into a confusing failure much
     * later (a schema name that needs quoting, a renewal slower than the lease it renews).
     *
     * @throws IllegalStateException naming the offending property and the accepted values
     */
    public void validate() {
        require(repository.schema != null && SCHEMA_NAME.matcher(repository.schema).matches(),
                "adhar.batch.repository.schema must be a lower-case SQL identifier ([a-z_][a-z0-9_]*, at most "
                        + "63 characters), was '" + repository.schema + "'");
        require(repository.tablePrefix != null && TABLE_PREFIX.matcher(repository.tablePrefix).matches(),
                "adhar.batch.repository.table-prefix must be letters, digits and underscores (at most 30), was '"
                        + repository.tablePrefix + "'");
        require(chunk.size > 0, "adhar.batch.chunk.size must be positive, was " + chunk.size);
        require(page.size > 0, "adhar.batch.page.size must be positive, was " + page.size);
        require(retry.maxAttempts >= 1, "adhar.batch.retry.max-attempts must be at least 1, was "
                + retry.maxAttempts);
        require(isPositive(retry.initialBackoff), "adhar.batch.retry.initial-backoff must be positive");
        require(isPositive(retry.maxBackoff) && retry.maxBackoff.compareTo(retry.initialBackoff) >= 0,
                "adhar.batch.retry.max-backoff must be positive and not below initial-backoff");
        require(isPositive(shutdown.timeout), "adhar.batch.shutdown.timeout must be positive");
        require(isPositive(scheduler.misfireThreshold), "adhar.batch.scheduler.misfire-threshold must be positive");
        require(isPositive(scheduler.lockLease), "adhar.batch.scheduler.lock-lease must be positive");
        require(isPositive(scheduler.lockRenewInterval)
                        && scheduler.lockRenewInterval.compareTo(scheduler.lockLease) < 0,
                "adhar.batch.scheduler.lock-renew-interval must be positive and shorter than lock-lease ("
                        + scheduler.lockLease + "), was " + scheduler.lockRenewInterval);
        require(scheduler.poolSize >= 1, "adhar.batch.scheduler.pool-size must be at least 1, was "
                + scheduler.poolSize);
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Where the JobRepository lives. On PostgreSQL the kit owns the schema: it migrates it with
     * its own Flyway history and points Spring Batch's table prefix at it.
     */
    public static class Repository {

        /**
         * PostgreSQL schema of the Spring Batch tables and of the kit's own tables (scheduler
         * lock, execution lease, dead letters). A lower-case SQL identifier.
         */
        private String schema = "batch";

        /**
         * Prefix of the Spring Batch tables inside {@code schema}. Spring Batch's
         * {@code spring.batch.jdbc.table-prefix} becomes {@code <schema>.<table-prefix>}; it may
         * be left unset or set to exactly that. Choose it once: tables created under one prefix
         * are not renamed.
         */
        private String tablePrefix = "BATCH_";

        /**
         * Run the kit's Flyway migrations of {@code schema} before the JobRepository is used.
         * When false the schema must already hold every table; it is verified at startup.
         */
        private boolean migrate = true;

        /**
         * Allow an in-memory (resourceless) JobRepository. Nothing survives a restart with one,
         * so outside tests the application refuses to start with it.
         */
        private boolean allowInMemory = false;

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }

        public boolean isMigrate() {
            return migrate;
        }

        public void setMigrate(boolean migrate) {
            this.migrate = migrate;
        }

        public boolean isAllowInMemory() {
            return allowInMemory;
        }

        public void setAllowInMemory(boolean allowInMemory) {
            this.allowInMemory = allowInMemory;
        }
    }

    /**
     * Graceful stop of running jobs when the application shuts down.
     */
    public static class Shutdown {

        /**
         * How long shutdown waits for a running job to stop at its next chunk boundary and record
         * STOPPED. Keep it below the pod's termination grace period.
         */
        private Duration timeout = Duration.ofSeconds(90);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Chunk defaults of the kit's step builders ({@code AdharSteps}).
     */
    public static class Chunk {

        /**
         * Items per chunk (the commit interval) when a step does not set its own.
         */
        private int size = 100;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    /**
     * Page defaults of the kit's paging readers.
     */
    public static class Page {

        /**
         * Rows per page for the kit's paging readers when a reader does not set its own.
         */
        private int size = 500;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    /**
     * Defaults of the kit's retry presets ({@code FaultTolerance}).
     */
    public static class Retry {

        /**
         * Attempts for one item, the first one included (3 = the first try and two retries).
         */
        private int maxAttempts = 3;

        /**
         * Delay before the first retry; each further retry doubles it.
         */
        private Duration initialBackoff = Duration.ofSeconds(1);

        /**
         * Upper bound of the delay between two attempts.
         */
        private Duration maxBackoff = Duration.ofSeconds(30);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    /**
     * Embedded scheduler: cron schedules inside a long-running service. The platform path is a
     * Kubernetes CronJob per job ({@code BatchJob}); this mode is for a service that also runs
     * a small job.
     */
    public static class Scheduler {

        /**
         * Enable the embedded scheduler ({@code BatchScheduler}). Spring Boot's
         * run-every-job-at-startup ({@code spring.batch.job.enabled}) then defaults to false.
         */
        private boolean enabled = false;

        /**
         * Time zone of the cron expressions.
         */
        private ZoneId timeZone = ZoneId.of("UTC");

        /**
         * A fire that starts later than this after its nominal time is skipped and logged:
         * after a pause, or when the previous run of the job overran several slots.
         */
        private Duration misfireThreshold = Duration.ofMinutes(10);

        /**
         * How the instances of a service agree on who runs a slot: {@code jdbc} (a row in the
         * kit's lock table, on the JobRepository's database) or {@code none} (one instance only).
         * A {@code SchedulerLock} bean of the application's own replaces either.
         */
        private Lock lock = Lock.JDBC;

        /**
         * How long a lock is held without renewal. A running fire renews it every
         * {@code lock-renew-interval} for as long as the job runs; a crashed holder's lock
         * expires after this.
         */
        private Duration lockLease = Duration.ofMinutes(2);

        /**
         * How often a running fire renews its lock. Must be shorter than {@code lock-lease}.
         */
        private Duration lockRenewInterval = Duration.ofSeconds(30);

        /**
         * Threads of the scheduler: how many scheduled jobs can run at the same time (a run is
         * synchronous on its thread).
         */
        private int poolSize = 2;

        /**
         * Schedules to register at startup: job name to cron expression (six fields, seconds
         * first, in {@code time-zone}).
         */
        private Map<String, String> jobs = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public ZoneId getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(ZoneId timeZone) {
            this.timeZone = timeZone;
        }

        public Duration getMisfireThreshold() {
            return misfireThreshold;
        }

        public void setMisfireThreshold(Duration misfireThreshold) {
            this.misfireThreshold = misfireThreshold;
        }

        public Lock getLock() {
            return lock;
        }

        public void setLock(Lock lock) {
            this.lock = lock;
        }

        public Duration getLockLease() {
            return lockLease;
        }

        public void setLockLease(Duration lockLease) {
            this.lockLease = lockLease;
        }

        public Duration getLockRenewInterval() {
            return lockRenewInterval;
        }

        public void setLockRenewInterval(Duration lockRenewInterval) {
            this.lockRenewInterval = lockRenewInterval;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public Map<String, String> getJobs() {
            return jobs;
        }

        public void setJobs(Map<String, String> jobs) {
            this.jobs = jobs;
        }

        /**
         * Scheduler lock implementations.
         */
        public enum Lock {
            /** A row per job in the kit's lock table, on the JobRepository's database. */
            JDBC,
            /** No lock: only the JobInstance identity of a slot prevents a double run. */
            NONE
        }
    }
}
