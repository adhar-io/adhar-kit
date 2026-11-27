package com.adhar.kit.batch.config;

import com.adhar.kit.batch.runtime.BatchRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configuration properties of the Adhar Batch module ({@code adhar.batch.*}).
 *
 * <pre>{@code
 * adhar:
 *   batch:
 *     enabled: true                 # the module also needs at least one Job bean
 *     mode: auto                    # auto | kubernetes | embedded; auto = kubernetes when a job is named
 *     job: importJob                # Kubernetes mode: the job to run (env ADHAR_BATCH_JOB)
 *     role: single                  # single | manager | worker | indexed (env ADHAR_BATCH_ROLE)
 *     repository:
 *       schema: batch               # the JobRepository's own PostgreSQL schema
 *       table-prefix: BATCH_        # -> spring.batch.jdbc.table-prefix = batch.BATCH_
 *       migrate: true               # the kit's Flyway migrations of that schema
 *       allow-in-memory: false      # tests only
 *     recovery:                     # Kubernetes mode: the execution lease
 *       heartbeat-interval: 30s
 *       stale-after: 2m             # a STARTED execution with an older heartbeat is recovered
 *       max-wait: 10m               # how long a run waits for a terminating owner pod
 *     shutdown:
 *       timeout: 90s                # graceful stop of running jobs on shutdown
 *     exit:
 *       already-complete: success   # success (exit 0) | fail (exit 3)
 *     calendar:                     # D5: the business calendar file a scheduled, dependency or backfill run
 *                                   # is skipped by (ADHAR_BATCH_CALENDAR, set by the platform)
 *     restart:
 *       definition-check: refuse    # refuse (exit 2) | warn: a restart whose job no longer has a step it ran
 *     chunk:
 *       size: 100                   # AdharSteps.chunk(name)
 *     page:
 *       size: 500                   # the kit's paging readers
 *     retry:                        # FaultTolerance retry presets
 *       max-attempts: 3
 *       initial-backoff: 1s
 *       max-backoff: 30s
 *     dead-letter:
 *       enabled: true               # FaultTolerance.deadLetter() writes skipped items to the JDBC table
 *     retention:                    # D4 housekeeping after every COMPLETED run and on the ops verb housekeeping
 *       days:                       # unset: no housekeeping (ADHAR_BATCH_RETENTION_DAYS, the platform passes 90)
 *       keep-last: 30               # the newest instances always kept (ADHAR_BATCH_RETENTION_KEEP_LAST)
 *     telemetry:
 *       trace-items: false          # per-item spans (off: a million items would be a million spans)
 *     partition:                    # remote partitioning (roles manager, worker, indexed)
 *       transport: jdbc             # jdbc; kafka and amqp are reserved names (env ADHAR_BATCH_PARTITION_TRANSPORT)
 *       grid-size: 4                # partitions per run; the platform passes 4 x workers (ADHAR_BATCH_PARTITION_GRIDSIZE)
 *       poll-interval: 5s           # how often the manager and idle workers look at the repository
 *       timeout:                    # how long the manager waits for every partition; unset waits until
 *                                   # the pod's deadline (ADHAR_BATCH_PARTITION_TIMEOUT)
 *     distribution: partitioned     # partitioned | chunked (not in this kit: refused) (ADHAR_BATCH_DISTRIBUTION)
 *     scheduler:                    # embedded mode - not the platform path
 *       enabled: false
 *       engine: simple              # simple | quartz (design 0025 §5.20)
 *       time-zone: UTC
 *       misfire-threshold: 10m
 *       lock: jdbc                  # jdbc | none
 *       lock-lease: 2m              # renewed every lock-renew-interval while the run lasts
 *       lock-renew-interval: 30s
 *       pool-size: 2
 *       jobs:
 *         importJob: "0 0 2 * * *"
 *     quartz:                       # engine quartz only: a clustered JDBC job store in the batch schema
 *       scheduler-name: orders      # the cluster's identity; default spring.application.name
 *       cluster-checkin-interval: 15s
 *       misfire: latest             # skip | latest | all: the default per trigger
 *       max-catch-up: 24            # the most slots misfire 'all' runs after one outage
 *       jobs:
 *         importJob:
 *           name: import            # the schedule's name (naming v1's batchjob); default the bean name as a DNS label
 *       triggers:
 *         importJob:
 *           - name: nightly
 *             kind: cron            # cron | interval | daily
 *             expression: "0 0 2 * * ?"
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

    /**
     * How the application runs its jobs. {@code auto} becomes {@code kubernetes} when a job is
     * named ({@code adhar.batch.job}, environment {@code ADHAR_BATCH_JOB}), and is otherwise
     * {@code embedded}.
     */
    private Mode mode = Mode.AUTO;

    /**
     * Kubernetes mode: the job the run executes, by job or bean name. The platform sets it
     * through {@code ADHAR_BATCH_JOB}.
     */
    private String job;

    /**
     * Kubernetes mode: what this pod does in its job. The platform sets it through
     * {@code ADHAR_BATCH_ROLE}.
     */
    private BatchRole role = BatchRole.SINGLE;

    private final Repository repository = new Repository();

    private final Recovery recovery = new Recovery();

    private final Exit exit = new Exit();

    /**
     * Kubernetes mode: the business calendar (design 0025 D5), a {@code calendar.yaml} the
     * platform mounts and names through {@code ADHAR_BATCH_CALENDAR}. A run triggered by the
     * schedule, a dependency or a backfill whose business date is not a business day exits 0,
     * skipped; a calendar that is missing or invalid exits 2. Unset, every day is a business day.
     */
    private String calendar;

    private final Restart restart = new Restart();

    private final DeadLetter deadLetter = new DeadLetter();

    private final Retention retention = new Retention();

    private final Telemetry telemetry = new Telemetry();

    private final Shutdown shutdown = new Shutdown();

    private final Chunk chunk = new Chunk();

    private final Page page = new Page();

    private final Retry retry = new Retry();

    private final Scheduler scheduler = new Scheduler();

    private final Quartz quartz = new Quartz();

    private final Partition partition = new Partition();

    /**
     * Which manager and worker beans a partitioned run's role selects: {@code partitioned}
     * (remote partitioning) or {@code chunked} (remote chunking, which this kit does not implement:
     * every pod of such a run exits 2, {@code unsupported-distribution}). The platform sets it
     * through {@code ADHAR_BATCH_DISTRIBUTION}, only on a chunked run, and the mode
     * ({@code ADHAR_BATCH_MODE}) is ignored on a pod that carries that variable.
     */
    private Distribution distribution = Distribution.PARTITIONED;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public BatchRole getRole() {
        return role;
    }

    public void setRole(BatchRole role) {
        this.role = role;
    }

    public Repository getRepository() {
        return repository;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public Exit getExit() {
        return exit;
    }

    public String getCalendar() {
        return calendar;
    }

    public void setCalendar(String calendar) {
        this.calendar = calendar;
    }

    public Restart getRestart() {
        return restart;
    }

    public DeadLetter getDeadLetter() {
        return deadLetter;
    }

    public Retention getRetention() {
        return retention;
    }

    public Telemetry getTelemetry() {
        return telemetry;
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

    public Quartz getQuartz() {
        return quartz;
    }

    public Partition getPartition() {
        return partition;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
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
        String prefix = repository.tablePrefix.toUpperCase(Locale.ROOT);
        require(!prefix.startsWith("QRTZ_") && !prefix.startsWith("ADHAR_"),
                "adhar.batch.repository.table-prefix must not begin with QRTZ_ or ADHAR_, was '"
                        + repository.tablePrefix + "': the batch schema holds the Quartz store's tables (QRTZ_*) and "
                        + "the kit's own (ADHAR_*), and Spring Batch's tables would collide with them");
        require(chunk.size > 0, "adhar.batch.chunk.size must be positive, was " + chunk.size);
        require(page.size > 0, "adhar.batch.page.size must be positive, was " + page.size);
        require(retry.maxAttempts >= 1, "adhar.batch.retry.max-attempts must be at least 1, was "
                + retry.maxAttempts);
        require(isPositive(retry.initialBackoff), "adhar.batch.retry.initial-backoff must be positive");
        require(isPositive(retry.maxBackoff) && retry.maxBackoff.compareTo(retry.initialBackoff) >= 0,
                "adhar.batch.retry.max-backoff must be positive and not below initial-backoff");
        require(isPositive(shutdown.timeout), "adhar.batch.shutdown.timeout must be positive");
        require(isPositive(recovery.heartbeatInterval) && isPositive(recovery.staleAfter)
                        && recovery.heartbeatInterval.compareTo(recovery.staleAfter) < 0,
                "adhar.batch.recovery.heartbeat-interval must be positive and shorter than stale-after ("
                        + recovery.staleAfter + "), was " + recovery.heartbeatInterval);
        require(isPositive(recovery.maxWait), "adhar.batch.recovery.max-wait must be positive");
        require(isPositive(scheduler.misfireThreshold), "adhar.batch.scheduler.misfire-threshold must be positive");
        require(isPositive(scheduler.lockLease), "adhar.batch.scheduler.lock-lease must be positive");
        require(isPositive(scheduler.lockRenewInterval)
                        && scheduler.lockRenewInterval.compareTo(scheduler.lockLease) < 0,
                "adhar.batch.scheduler.lock-renew-interval must be positive and shorter than lock-lease ("
                        + scheduler.lockLease + "), was " + scheduler.lockRenewInterval);
        require(scheduler.poolSize >= 1, "adhar.batch.scheduler.pool-size must be at least 1, was "
                + scheduler.poolSize);
        require(isPositive(quartz.clusterCheckinInterval),
                "adhar.batch.quartz.cluster-checkin-interval must be positive");
        require(quartz.maxCatchUp >= 1, "adhar.batch.quartz.max-catch-up must be at least 1, was "
                + quartz.maxCatchUp);
        require(scheduler.engine != Scheduler.Engine.QUARTZ || scheduler.lock != Scheduler.Lock.NONE,
                "adhar.batch.scheduler.lock=none is refused with adhar.batch.scheduler.engine=quartz: the clustered "
                        + "job store is the lock, and a store that is not clustered would fire every trigger on every "
                        + "replica. Remove the property (jdbc), or use the engine simple");
        require(partition.gridSize == null || partition.gridSize >= 1,
                "adhar.batch.partition.grid-size must be at least 1, was " + partition.gridSize);
        require(isPositive(partition.pollInterval), "adhar.batch.partition.poll-interval must be positive");
        require(retention.days == null || retention.days >= 1,
                "adhar.batch.retention.days must be at least 1 when set, was " + retention.days);
        require(retention.keepLast >= 0, "adhar.batch.retention.keep-last must not be negative, was "
                + retention.keepLast);
        require(partition.timeout == null || isPositive(partition.timeout),
                "adhar.batch.partition.timeout must be positive when set, was " + partition.timeout);
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
     * How an application runs its jobs.
     */
    public enum Mode {
        /** {@link #KUBERNETES} when a job is named, otherwise {@link #EMBEDDED}. */
        AUTO,
        /** One run per process: the runner executes {@code adhar.batch.job} and exits with its code. */
        KUBERNETES,
        /** Jobs run inside a long-running service (operator calls, the embedded scheduler). */
        EMBEDDED
    }

    /**
     * Kubernetes mode: the lease a run holds on its execution, and when another run may take a
     * STARTED execution over.
     */
    public static class Recovery {

        /**
         * How often a running execution's lease is renewed.
         */
        private Duration heartbeatInterval = Duration.ofSeconds(30);

        /**
         * A STARTED execution whose lease is older than this is taken over (recovered and
         * restarted) even if its owner pod cannot be checked.
         */
        private Duration staleAfter = Duration.ofMinutes(2);

        /**
         * How long a run waits for the terminating pod that owns the execution before deciding.
         */
        private Duration maxWait = Duration.ofMinutes(10);

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Duration getStaleAfter() {
            return staleAfter;
        }

        public void setStaleAfter(Duration staleAfter) {
            this.staleAfter = staleAfter;
        }

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }
    }

    /**
     * Kubernetes mode: exit codes the application may choose.
     */
    public static class Exit {

        /**
         * What a run of an identity that already COMPLETED exits with: {@code success} (0) or
         * {@code fail} (3).
         */
        private AlreadyComplete alreadyComplete = AlreadyComplete.SUCCESS;

        public AlreadyComplete getAlreadyComplete() {
            return alreadyComplete;
        }

        public void setAlreadyComplete(AlreadyComplete alreadyComplete) {
            this.alreadyComplete = alreadyComplete;
        }

        /**
         * The exit of a run whose identity already completed.
         */
        public enum AlreadyComplete {
            /** Exit 0: the work is done, so the run succeeded. */
            SUCCESS,
            /** Exit 3: a second run of a completed identity is a mistake worth a failed Job. */
            FAIL
        }
    }

    /**
     * Kubernetes mode: how a restart of a FAILED or STOPPED execution is checked against the job
     * it would resume.
     */
    public static class Restart {

        /**
         * What a restart does when the job no longer has a step the identity's executions ran (a
         * step renamed or removed by a new image): {@code refuse} exits 2 without starting, so the
         * work of the old step is neither redone under the new name nor skipped; {@code warn} logs
         * and restarts, which is Spring Batch's own behaviour.
         */
        private DefinitionCheck definitionCheck = DefinitionCheck.REFUSE;

        public DefinitionCheck getDefinitionCheck() {
            return definitionCheck;
        }

        public void setDefinitionCheck(DefinitionCheck definitionCheck) {
            this.definitionCheck = definitionCheck;
        }

        /**
         * The restart guard's verdict on a changed job.
         */
        public enum DefinitionCheck {
            /** Exit 2 ({@code definition-changed}): a person decides. */
            REFUSE,
            /** Log a warning and restart as Spring Batch would. */
            WARN
        }
    }

    /**
     * The dead-letter table of fault-tolerant steps.
     */
    public static class DeadLetter {

        /**
         * Write the items a step skips to {@code <schema>.ADHAR_BATCH_DEAD_LETTER} when the step
         * declares {@code FaultTolerance.deadLetter()}.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Housekeeping of the JobRepository (design 0025 D4): after every COMPLETED run, and on the
     * ops command {@code housekeeping}, the runner deletes the job's instances whose last
     * activity is older than {@code days}. It keeps the newest {@code keep-last} instances, always
     * the newest restartable FAILED or STOPPED one, and any instance still running.
     */
    public static class Retention {

        /**
         * Delete metadata older than this many days. Unset, there is no housekeeping: the
         * platform passes the BatchJob's {@code retention.days} as ADHAR_BATCH_RETENTION_DAYS.
         */
        private Integer days;

        /**
         * The newest job instances that are always kept, whatever their age
         * (ADHAR_BATCH_RETENTION_KEEP_LAST).
         */
        private int keepLast = 30;

        public Integer getDays() {
            return days;
        }

        public void setDays(Integer days) {
            this.days = days;
        }

        public int getKeepLast() {
            return keepLast;
        }

        public void setKeepLast(int keepLast) {
            this.keepLast = keepLast;
        }
    }

    /**
     * Telemetry of batch runs.
     */
    public static class Telemetry {

        /**
         * Trace every item read, processed and written. Off, items are metrics only and a run
         * traces jobs, steps and chunks.
         */
        private boolean traceItems = false;

        public boolean isTraceItems() {
            return traceItems;
        }

        public void setTraceItems(boolean traceItems) {
            this.traceItems = traceItems;
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
     * What a partitioned run distributes (design 0025 §5.10, §5.11, D11).
     */
    public enum Distribution {
        /** Remote partitioning: a manager hands partitions to workers through the partition channel. */
        PARTITIONED,
        /** Remote chunking over a durable queue. Not in this kit (design 0025, deferred): a run that names it is refused. */
        CHUNKED
    }

    /**
     * Remote partitioning (design 0025 §5.10): a manager pod splits the input and workers claim the
     * partitions through the partition channel, a table in the JobRepository's schema. The
     * {@code BatchJob} composition sets transport, grid size and timeout on every partitioned run as
     * {@code ADHAR_BATCH_PARTITION_TRANSPORT}, {@code ADHAR_BATCH_PARTITION_GRIDSIZE} and
     * {@code ADHAR_BATCH_PARTITION_TIMEOUT}.
     */
    public static class Partition {

        /**
         * How partition requests reach the workers. {@code jdbc}, a queue in the application's own
         * database, is the only transport in Phase 2; {@code kafka} and {@code amqp} are reserved
         * names, and a run that names one is refused.
         */
        private Transport transport = Transport.JDBC;

        /**
         * Partitions a manager creates. Unset, 4: the platform always passes its own value, 4 x the
         * run's workers unless the BatchJob says otherwise. Independent of the number of workers,
         * which are interchangeable consumers.
         */
        private Integer gridSize;

        /**
         * How often the manager reads the JobRepository for finished partitions, and how often an
         * idle worker looks for a request or for the end of the run.
         */
        private Duration pollInterval = Duration.ofSeconds(5);

        /**
         * How long the manager waits for every partition to finish before it fails the run
         * (retryable) and fences the partitions still running. Unset, it waits until the pod's
         * deadline; the platform passes the run's activeDeadlineSeconds less five minutes (half the
         * deadline when that is longer), so the manager gives up before it is killed.
         */
        private Duration timeout;

        public Transport getTransport() {
            return transport;
        }

        public void setTransport(Transport transport) {
            this.transport = transport;
        }

        public Integer getGridSize() {
            return gridSize;
        }

        public void setGridSize(Integer gridSize) {
            this.gridSize = gridSize;
        }

        /**
         * @return {@link #getGridSize()}, or 4 when unset
         */
        public int effectiveGridSize() {
            return gridSize != null ? gridSize : 4;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /**
         * Partition transports.
         */
        public enum Transport {
            /** A queue in the JobRepository's database (Spring Integration's JDBC channel message store). */
            JDBC,
            /** Reserved. */
            KAFKA,
            /** Reserved: RabbitMQ arrives with remote chunking (Phase 4). */
            AMQP
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
         * Which engine fires the schedules: {@code simple} (cron schedules on the service's own
         * threads, a lock row per job) or {@code quartz} (a clustered Quartz JDBC job store in the
         * batch schema, which needs {@code spring-boot-starter-quartz}; design 0025 §5.20).
         */
        private Engine engine = Engine.SIMPLE;

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

        public Engine getEngine() {
            return engine;
        }

        public void setEngine(Engine engine) {
            this.engine = engine;
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
         * The engines of {@code BatchScheduler} (design 0025 §5.20.2, D24).
         */
        public enum Engine {
            /** Cron schedules on the service's own threads, a lock row per job (§5.16). */
            SIMPLE,
            /** A clustered Quartz JDBC job store in the batch schema (§5.20). */
            QUARTZ
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

    /**
     * The engine {@code quartz} of the embedded scheduler (design 0025 §5.20): a clustered Quartz
     * JDBC job store in the batch schema, shared by the service's replicas. The shared
     * {@code adhar.batch.scheduler.*} keys still apply: {@code time-zone} is every trigger's default
     * zone, {@code misfire-threshold} Quartz's {@code misfireThreshold}, {@code pool-size} its
     * {@code threadCount}, and {@code jobs} cron schedules.
     */
    public static class Quartz {

        /**
         * The cluster's identity, Quartz's {@code instanceName} (the {@code SCHED_NAME} column):
         * equal across one service's replicas and distinct per service. Unset, the application's
         * {@code spring.application.name}, which the engine {@code quartz} requires in any case: it
         * is the owner recorded on every Quartz job.
         */
        private String schedulerName;

        /**
         * How often a replica checks in to the cluster, and so how fast a dead replica's work is
         * re-fired on another one.
         */
        private Duration clusterCheckinInterval = Duration.ofSeconds(15);

        /**
         * What a trigger does with the slots it missed while every replica was down, unless the
         * trigger sets its own: {@code skip}, {@code latest} (one run, with the newest missed slot's
         * identity) or {@code all} (every missed slot, oldest first).
         */
        private Misfire misfire = Misfire.LATEST;

        /**
         * The most missed slots {@code misfire: all} runs after one outage; older ones are skipped
         * and counted.
         */
        private int maxCatchUp = 24;

        /**
         * Per job (by bean name): its schedule's name.
         */
        private Map<String, JobSettings> jobs = new LinkedHashMap<>();

        /**
         * Per job (by bean name): its triggers, reconciled into the trigger group
         * {@code adhar-batch-config} at every start.
         */
        private Map<String, List<TriggerSettings>> triggers = new LinkedHashMap<>();

        /**
         * Business calendars (design 0025 D5, §5.20.6): {@code calendars.<name>.file}, a mounted
         * {@code calendar.yaml}, or {@code calendars.<name>.time-zone|weekend-days|holidays|business-days};
         * and {@code calendars.refresh-interval}.
         */
        private Calendars calendars = new Calendars();

        /**
         * The schedule report the Console and CLI read (design 0025 §5.20.8).
         */
        private Report report = new Report();

        public String getSchedulerName() {
            return schedulerName;
        }

        public void setSchedulerName(String schedulerName) {
            this.schedulerName = schedulerName;
        }

        public Duration getClusterCheckinInterval() {
            return clusterCheckinInterval;
        }

        public void setClusterCheckinInterval(Duration clusterCheckinInterval) {
            this.clusterCheckinInterval = clusterCheckinInterval;
        }

        public Misfire getMisfire() {
            return misfire;
        }

        public void setMisfire(Misfire misfire) {
            this.misfire = misfire;
        }

        public int getMaxCatchUp() {
            return maxCatchUp;
        }

        public void setMaxCatchUp(int maxCatchUp) {
            this.maxCatchUp = maxCatchUp;
        }

        public Map<String, JobSettings> getJobs() {
            return jobs;
        }

        public void setJobs(Map<String, JobSettings> jobs) {
            this.jobs = jobs;
        }

        public Map<String, List<TriggerSettings>> getTriggers() {
            return triggers;
        }

        public void setTriggers(Map<String, List<TriggerSettings>> triggers) {
            this.triggers = triggers;
        }

        public Calendars getCalendars() {
            return calendars;
        }

        public void setCalendars(Calendars calendars) {
            this.calendars = calendars;
        }

        public Report getReport() {
            return report;
        }

        public void setReport(Report report) {
            this.report = report;
        }

        /**
         * {@code adhar.batch.quartz.calendars}: the named calendars sit beside
         * {@code refresh-interval} under the same prefix, so they are bound by name
         * ({@code com.adhar.kit.batch.scheduler.quartz.CalendarRegistry}) rather than as a map here,
         * which would read {@code refresh-interval} as a calendar.
         */
        public static class Calendars {

            /**
             * How often a calendar file is re-read: a mounted ConfigMap changes in place.
             */
            private Duration refreshInterval = Duration.ofMinutes(5);

            public Duration getRefreshInterval() {
                return refreshInterval;
            }

            public void setRefreshInterval(Duration refreshInterval) {
                this.refreshInterval = refreshInterval;
            }
        }

        /**
         * One calendar, {@code adhar.batch.quartz.calendars.<name>.*}: a {@code file}, or D5's keys.
         */
        public static class CalendarSettings {

            /**
             * A mounted {@code calendar.yaml} (D5's shape), re-read every {@code refresh-interval}.
             */
            private String file;

            /**
             * The zone its dates are taken in. Unset, {@code adhar.batch.scheduler.time-zone}.
             */
            private String timeZone;

            /**
             * The days of the week that are not business days. Unset, Saturday and Sunday.
             */
            private List<String> weekendDays;

            /**
             * ISO dates that are not business days.
             */
            private List<String> holidays = new ArrayList<>();

            /**
             * ISO dates that are business days, overriding the weekend (a working Saturday).
             */
            private List<String> businessDays = new ArrayList<>();

            public String getFile() {
                return file;
            }

            public void setFile(String file) {
                this.file = file;
            }

            public String getTimeZone() {
                return timeZone;
            }

            public void setTimeZone(String timeZone) {
                this.timeZone = timeZone;
            }

            public List<String> getWeekendDays() {
                return weekendDays;
            }

            public void setWeekendDays(List<String> weekendDays) {
                this.weekendDays = weekendDays;
            }

            public List<String> getHolidays() {
                return holidays;
            }

            public void setHolidays(List<String> holidays) {
                this.holidays = holidays;
            }

            public List<String> getBusinessDays() {
                return businessDays;
            }

            public void setBusinessDays(List<String> businessDays) {
                this.businessDays = businessDays;
            }
        }

        /**
         * The schedule report: {@code ConfigMap <name>} in the service's namespace, version 1.
         */
        public static class Report {

            /**
             * Whether to write it. Unset, only when running in Kubernetes (a mounted
             * ServiceAccount token and {@code KUBERNETES_SERVICE_HOST}).
             */
            private Boolean enabled;

            /**
             * The ConfigMap's name. Unset, {@code <scheduler-name>-batch-schedules}.
             */
            private String name;

            /**
             * The least time between two writes; a write asked for sooner is deferred.
             */
            private Duration minInterval = Duration.ofSeconds(10);

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Duration getMinInterval() {
                return minInterval;
            }

            public void setMinInterval(Duration minInterval) {
                this.minInterval = minInterval;
            }
        }

        /**
         * Misfire policies (design 0025 §5.20.6).
         */
        public enum Misfire {
            /** Nothing runs; the next regular slot does. */
            SKIP,
            /** One run now, with the identity of the newest missed slot. */
            LATEST,
            /** Every missed slot, oldest first, each with its own identity, up to {@code max-catch-up}. */
            ALL
        }

        /**
         * One job's schedule settings.
         */
        public static class JobSettings {

            /**
             * The schedule's name: the {@code <batchjob>} of the run names (naming v1) and the
             * Quartz JobDetail's key. Unset, the bean name as a DNS-1035 label of at most 40
             * characters ({@code importJob} becomes {@code import-job}).
             */
            private String name;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        /**
         * One configured trigger (design 0025 §5.20.6).
         */
        public static class TriggerSettings {

            /**
             * The trigger's name within its job; the Quartz trigger is {@code <scheduleName>.<name>}.
             */
            private String name;

            /**
             * {@code cron} (a Quartz cron expression), {@code interval} (every {@code every} on the
             * grid {@code start + n x every}) or {@code daily} ({@code every} between {@code from} and
             * {@code to} on {@code days}).
             */
            private Kind kind = Kind.CRON;

            /**
             * {@code cron}: a Quartz cron expression, seconds first, optional year.
             */
            private String expression;

            /**
             * {@code interval} and {@code daily}: the period, at least one second.
             */
            private Duration every;

            /**
             * {@code interval}: the grid's anchor. Unset, the Unix epoch.
             */
            private Instant start;

            /**
             * {@code daily}: the first time of day of the window.
             */
            private LocalTime from;

            /**
             * {@code daily}: the last time of day of the window.
             */
            private LocalTime to;

            /**
             * {@code daily}: the days of the week. Unset, every day.
             */
            private List<DayOfWeek> days = new ArrayList<>();

            /**
             * The trigger's time zone. Unset, {@code adhar.batch.scheduler.time-zone}.
             */
            private ZoneId timeZone;

            /**
             * The trigger's calendar (design 0025 §5.20.6), by name.
             */
            private String calendar;

            /**
             * The trigger's misfire policy. Unset, {@code adhar.batch.quartz.misfire}.
             */
            private Misfire misfire;

            /**
             * Quartz's priority among triggers due at the same time.
             */
            private int priority = 5;

            /**
             * Values for the job's declared INPUT parameters.
             */
            private Map<String, String> parameters = new LinkedHashMap<>();

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Kind getKind() {
                return kind;
            }

            public void setKind(Kind kind) {
                this.kind = kind;
            }

            public String getExpression() {
                return expression;
            }

            public void setExpression(String expression) {
                this.expression = expression;
            }

            public Duration getEvery() {
                return every;
            }

            public void setEvery(Duration every) {
                this.every = every;
            }

            public Instant getStart() {
                return start;
            }

            public void setStart(Instant start) {
                this.start = start;
            }

            public LocalTime getFrom() {
                return from;
            }

            public void setFrom(LocalTime from) {
                this.from = from;
            }

            public LocalTime getTo() {
                return to;
            }

            public void setTo(LocalTime to) {
                this.to = to;
            }

            public List<DayOfWeek> getDays() {
                return days;
            }

            public void setDays(List<DayOfWeek> days) {
                this.days = days;
            }

            public ZoneId getTimeZone() {
                return timeZone;
            }

            public void setTimeZone(ZoneId timeZone) {
                this.timeZone = timeZone;
            }

            public String getCalendar() {
                return calendar;
            }

            public void setCalendar(String calendar) {
                this.calendar = calendar;
            }

            public Misfire getMisfire() {
                return misfire;
            }

            public void setMisfire(Misfire misfire) {
                this.misfire = misfire;
            }

            public int getPriority() {
                return priority;
            }

            public void setPriority(int priority) {
                this.priority = priority;
            }

            public Map<String, String> getParameters() {
                return parameters;
            }

            public void setParameters(Map<String, String> parameters) {
                this.parameters = parameters;
            }

            /**
             * Trigger kinds.
             */
            public enum Kind {
                /** A Quartz {@code CronTrigger}. */
                CRON,
                /** A repeating {@code SimpleTrigger} on the kit's grid. */
                INTERVAL,
                /** A {@code DailyTimeIntervalTrigger}. */
                DAILY
            }
        }
    }
}
