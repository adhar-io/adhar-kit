package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.QuartzContexts;
import com.adhar.kit.batch.support.QuartzJobs;
import com.adhar.kit.batch.support.TcpProxy;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * KIT-Q.3 against a real clustered store (design 0025 §5.20.5, §5.20.6, §5.20.10): replicas of one
 * service, each a real Spring Boot application on the engine {@code quartz}, sharing one PostgreSQL
 * 18 database. What only Quartz's own JDBC store can show, and what the kit's reading of it
 * ({@code slotOf}, the owner check, the recovery fire, the resume trigger) rests on (R12):
 *
 * <ul>
 *   <li>AQ.3, FQ1: a replica killed mid-chunk is re-fired by Quartz on another one as a recovery, and
 *       the run completes from the last commit with every row written once; the dead replica's chunk,
 *       had it only been slow, commits nothing (INV-4).</li>
 *   <li>FQ6: while the owner still checks in, another fire of its identity is {@code skipped(duplicate)}.</li>
 *   <li>A graceful shutdown stores a resume trigger, and another replica restarts the same JobInstance.</li>
 *   <li>AQ.4, FQ2: every replica down across three slots, for cron and interval triggers under each
 *       misfire policy, on a grid of {@value #PERIOD} seconds.</li>
 *   <li>FQ3: the database unreachable across three slots; on its return the same policies apply.</li>
 * </ul>
 *
 * <p>The decisions themselves, on exact instants, are {@link BatchLaunchJobTest}'s and
 * {@link QuartzSchedulerEngineTest}'s, which need no database server.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class QuartzClusterTestContainerTest {

    /** The tick triggers' period: divides 60, and is longer than the misfire threshold (2 s). */
    private static final int PERIOD = 4;

    private static final int ROWS = 35;

    private String url;
    private JdbcTemplate jdbc;
    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();

    @BeforeEach
    void database() {
        url = PostgresDatabases.createDatabase("quartz_cluster");
        jdbc = new JdbcTemplate(PostgresDatabases.dataSource(url));
    }

    @AfterEach
    void closeContexts() throws Exception {
        contexts.forEach(context -> {
            if (context.isActive()) {
                context.getBean(QuartzJobs.Gate.class).release();
            }
        });
        contexts.forEach(ConfigurableApplicationContext::close);
        for (AutoCloseable resource : resources) {
            resource.close();
        }
    }

    private ConfigurableApplicationContext replica(String name, Map<String, Object> extra) {
        return replica(url, name, extra);
    }

    private ConfigurableApplicationContext replica(String database, String name, Map<String, Object> extra) {
        Map<String, Object> properties = QuartzContexts.properties(database, name);
        properties.putAll(extra);
        ConfigurableApplicationContext context = QuartzContexts.start(properties, QuartzJobs.class);
        contexts.add(context);
        return context;
    }

    private static String instanceId(ConfigurableApplicationContext replica) {
        return replica.getBean(QuartzSchedulerEngine.class).instanceId();
    }

    /** An execution of the chunk job, as the JobRepository's tables hold it. */
    private record Execution(long id, long instance, String status, Map<String, String> parameters) {
    }

    private List<Execution> executions(String job) {
        return jdbc.query("select e.job_execution_id, e.job_instance_id, e.status from batch.batch_job_execution e "
                        + "join batch.batch_job_instance i on i.job_instance_id = e.job_instance_id "
                        + "where i.job_name = ? order by e.job_execution_id",
                (rs, row) -> {
                    Map<String, String> parameters = new LinkedHashMap<>();
                    jdbc.query("select parameter_name, parameter_value from batch.batch_job_execution_params "
                            + "where job_execution_id = ?", params -> {
                        parameters.put(params.getString(1), params.getString(2));
                    }, rs.getLong(1));
                    return new Execution(rs.getLong(1), rs.getLong(2), rs.getString(3), parameters);
                }, job);
    }

    private int targetRows(String column) {
        return jdbc.queryForObject("select count(" + column + ") from TARGET_ITEMS", Integer.class);
    }

    /** Starts the chunk job on a replica and holds it mid-chunk: ten rows committed, ten written. */
    private void startHeld(ConfigurableApplicationContext replica, String run) throws Exception {
        TestJobs.seed(jdbc, ROWS);
        QuartzJobs.Gate gate = replica.getBean(QuartzJobs.Gate.class);
        gate.holdAt(15);
        QuartzContexts.fireNow(replica, QuartzJobs.CHUNK_JOB, "adhoc-" + run, "{\"run\":\"" + run + "\"}");
        assertThat(gate.awaitHeld(60)).as("the run is held in its second chunk").isTrue();
        assertThat(targetRows("*")).as("the first chunk committed").isEqualTo(QuartzJobs.CHUNK);
    }

    // ------------------------------------------------------------------
    // AQ.3, FQ1, FQ6
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AQ.3, FQ6: a live owner's identity is skipped(duplicate); killed mid-chunk, another replica recovers it from the last commit with no duplicate rows")
    void aKilledReplicaIsRecoveredByAnother() throws Exception {
        ConfigurableApplicationContext first = replica("a", Map.of());
        startHeld(first, "r1");
        ConfigurableApplicationContext second = replica("b", Map.of());
        QuartzSchedulerEngine engine = second.getBean(QuartzSchedulerEngine.class);
        assertThat(instanceId(second)).isNotEqualTo(instanceId(first));
        assertThat(jdbc.queryForList("select owner_pod from batch.adhar_batch_execution_lease", String.class))
                .as("the run's lease names the Quartz instance that runs it").containsExactly(instanceId(first));

        // FQ6: the same identity fires on the other replica while its owner checks in.
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        data.put(QuartzSchedulerEngine.PARAMS, "{\"run\":\"r1\"}");
        Trigger again = QuartzSchedulerEngine.oneShot(new TriggerKey("again", QuartzSchedulerEngine.RUNTIME_GROUP),
                engine.jobKey(QuartzJobs.CHUNK_JOB), data, Instant.now());
        assertThat(new BatchLaunchJob(engine).launch(fire(second.getBean(Scheduler.class), again, engine)))
                .isEqualTo(SlotOutcome.DUPLICATE);
        assertThat(executions(QuartzJobs.CHUNK_JOB)).extracting(Execution::status).containsExactly("STARTED");

        // FQ1: the owner dies. Quartz notices its missing check-in and re-fires its work here.
        QuartzContexts.kill(first);
        await().atMost(90, TimeUnit.SECONDS).until(() -> {
            List<Execution> all = executions(QuartzJobs.CHUNK_JOB);
            return all.size() == 2 && all.get(1).status().equals("COMPLETED");
        });

        List<Execution> all = executions(QuartzJobs.CHUNK_JOB);
        assertThat(all.get(0).status()).as("the dead replica's execution, recovered").isEqualTo("FAILED");
        assertThat(all.get(1).instance()).as("the same JobInstance").isEqualTo(all.get(0).instance());
        assertThat(all.get(1).parameters()).containsEntry(QuartzSchedulerEngine.INSTANCE_PARAMETER, instanceId(second))
                .containsEntry(QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER, QuartzSchedulerEngine.RUNTIME_GROUP + ".adhoc-r1")
                .containsEntry(QuartzSchedulerEngine.RUN_PARAMETER, RunIdentity.runName("chunk-job", Map.of("run", "r1")));
        assertThat(targetRows("*")).isEqualTo(ROWS);
        assertThat(targetRows("distinct ID")).isEqualTo(ROWS);

        // INV-4: had the first replica only been slow, its chunk commits nothing after the recovery.
        first.getBean(QuartzJobs.Gate.class).release();
        await().atMost(60, TimeUnit.SECONDS).until(() -> first.getBean(QuartzSchedulerEngine.class).runningJobs().isEmpty());
        assertThat(targetRows("*")).as("the zombie's rows rolled back with its chunk").isEqualTo(ROWS);
        assertThat(targetRows("distinct ID")).isEqualTo(ROWS);
        assertThat(executions(QuartzJobs.CHUNK_JOB)).extracting(Execution::status).containsExactly("FAILED", "COMPLETED");
    }

    private static JobExecutionContext fire(Scheduler scheduler, Trigger trigger, QuartzSchedulerEngine engine)
            throws Exception {
        JobDetail detail = scheduler.getJobDetail(trigger.getJobKey());
        Date now = new Date();
        TriggerFiredBundle bundle = new TriggerFiredBundle(detail, (OperableTrigger) trigger, null, false, now, now,
                null, null);
        return new JobExecutionContextImpl(scheduler, bundle, new BatchLaunchJob(engine));
    }

    // ------------------------------------------------------------------
    // Shutdown and resume
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a replica shutting down stops its run at the chunk boundary and stores a resume trigger; another replica restarts the same JobInstance")
    void aShutdownIsResumedByAnotherReplica() throws Exception {
        ConfigurableApplicationContext first = replica("a", Map.of());
        startHeld(first, "r2");
        String firstInstance = instanceId(first);
        ConfigurableApplicationContext second = replica("b", Map.of());

        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(first::close);
        // Spring Batch records the stop request at once; the run is still in its chunk.
        await().atMost(60, TimeUnit.SECONDS).until(() -> !executions(QuartzJobs.CHUNK_JOB).get(0).status().equals("STARTED"));
        first.getBean(QuartzJobs.Gate.class).release();
        shutdown.get(60, TimeUnit.SECONDS);

        await().atMost(60, TimeUnit.SECONDS).until(() -> {
            List<Execution> all = executions(QuartzJobs.CHUNK_JOB);
            return all.size() == 2 && all.get(1).status().equals("COMPLETED");
        });
        List<Execution> all = executions(QuartzJobs.CHUNK_JOB);
        String runName = RunIdentity.runName("chunk-job", Map.of("run", "r2"));
        assertThat(all.get(0).status()).isEqualTo("STOPPED");
        assertThat(all.get(0).parameters()).containsEntry(QuartzSchedulerEngine.INSTANCE_PARAMETER, firstInstance);
        assertThat(all.get(1).instance()).as("the same JobInstance").isEqualTo(all.get(0).instance());
        assertThat(all.get(1).parameters())
                .containsEntry(QuartzSchedulerEngine.INSTANCE_PARAMETER, instanceId(second))
                .containsEntry(QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER, QuartzSchedulerEngine.RUNTIME_GROUP + "." + runName)
                .containsEntry(QuartzSchedulerEngine.RUN_PARAMETER, runName)
                .containsEntry("run", "r2");
        assertThat(targetRows("*")).isEqualTo(ROWS);
        assertThat(targetRows("distinct ID")).isEqualTo(ROWS);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(second.getBean(Scheduler.class)
                .getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzSchedulerEngine.RUNTIME_GROUP)))
                .as("the one-shot triggers are gone once they fired").isEmpty());
    }

    // ------------------------------------------------------------------
    // AQ.4, FQ2, FQ3: misfire policies on a real store
    // ------------------------------------------------------------------

    /** A slot a tick job ran, and when its execution was created. */
    private record Run(Instant slot, Instant created, long executionId) {
    }

    private List<Run> runs(String job) {
        return jdbc.query("select p.parameter_value, e.create_time, e.job_execution_id from batch.batch_job_execution e "
                        + "join batch.batch_job_instance i on i.job_instance_id = e.job_instance_id "
                        + "join batch.batch_job_execution_params p on p.job_execution_id = e.job_execution_id "
                        + "and p.parameter_name = ? where i.job_name = ? order by e.job_execution_id",
                (rs, row) -> new Run(Instant.parse(rs.getString(1)),
                        rs.getTimestamp(2).toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), rs.getLong(3)),
                BatchScheduler.SCHEDULED_TIME_PARAMETER, job);
    }

    private Instant lastSlot(String job) {
        return runs(job).stream().map(Run::slot).max(Instant::compareTo).orElse(Instant.EPOCH);
    }

    private void awaitEveryTickJobRanASlotAfter(Instant time) {
        await().atMost(120, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).until(() ->
                QuartzJobs.TICK_JOBS.stream().allMatch(job -> lastSlot(job).isAfter(time)));
    }

    /** The grid's slots strictly between two instants. */
    private static List<Instant> slotsBetween(Instant after, Instant before) {
        List<Instant> slots = new ArrayList<>();
        long first = Math.floorDiv(after.getEpochSecond(), PERIOD) * PERIOD + PERIOD;
        for (long second = first; Instant.ofEpochSecond(second).isBefore(before); second += PERIOD) {
            slots.add(Instant.ofEpochSecond(second));
        }
        return slots;
    }

    /**
     * What each misfire policy made of an outage: {@code missed} are slots no replica could fire,
     * all of them older than the misfire threshold when the outage ended.
     */
    private void assertMisfirePolicies(Map<String, List<Instant>> missedByJob) {
        for (String job : QuartzJobs.TICK_JOBS) {
            List<Run> runs = runs(job);
            List<Instant> slots = runs.stream().map(Run::slot).toList();
            List<Instant> missed = missedByJob.get(job);
            assertThat(missed).as(job + ": the outage spans three slots").hasSizeGreaterThanOrEqualTo(3);
            assertThat(slots).as(job + ": once per slot across the replicas").doesNotHaveDuplicates();
            assertThat(slots).as(job + ": every identity is a slot of the grid")
                    .allMatch(slot -> slot.getEpochSecond() % PERIOD == 0 && slot.getNano() == 0);
            Instant newestMissed = missed.get(missed.size() - 1);
            if (job.endsWith("Skip")) {
                assertThat(slots).as(job + ": skip runs none of the missed slots").doesNotContainAnyElementsOf(missed);
            } else if (job.endsWith("Latest")) {
                // One run for the whole outage, with the identity of the newest slot it missed: the
                // latest grid slot at or before the moment the misfire was handled.
                Run caughtUp = runs.stream().filter(run -> !run.slot().isBefore(missed.get(0))).findFirst().orElseThrow();
                assertThat(slots.stream().filter(slot -> !slot.isBefore(missed.get(0)) && !slot.isAfter(caughtUp.slot())))
                        .as(job + ": latest runs one slot for the outage").containsExactly(caughtUp.slot());
                assertThat(caughtUp.slot()).as(job + ": the newest missed slot, not an older one")
                        .isAfterOrEqualTo(newestMissed);
                // The run starts after the handling, behind the other triggers' catch-up runs on the
                // replica's few worker threads: within two periods, never before its slot.
                assertThat(Duration.between(caughtUp.slot(), caughtUp.created()))
                        .as(job + ": the identity is the slot the handling time falls in (%s created %s)",
                                caughtUp.slot(), caughtUp.created())
                        .isBetween(Duration.ZERO, Duration.ofSeconds(2L * PERIOD));
            } else {
                assertThat(slots).as(job + ": all runs every missed slot").containsAll(missed);
                List<Run> caughtUp = runs.stream().filter(run -> missed.contains(run.slot())).toList();
                assertThat(caughtUp).extracting(Run::slot).as(job + ": oldest first, each with its own identity")
                        .containsExactlyElementsOf(missed);
            }
        }
    }

    @Test
    @DisplayName("AQ.4: every replica down across three slots; skip runs none, latest the newest missed slot once, all each one oldest first; the interval trigger is back on its grid")
    void everyReplicaDownAcrossThreeSlots() throws Exception {
        Map<String, Object> triggers = QuartzJobs.tickTriggers(PERIOD);
        ConfigurableApplicationContext first = replica("a", triggers);
        ConfigurableApplicationContext second = replica("b", triggers);
        awaitEveryTickJobRanASlotAfter(Instant.now());
        first.close();
        second.close();
        Map<String, Instant> lastBefore = new LinkedHashMap<>();
        QuartzJobs.TICK_JOBS.forEach(job -> lastBefore.put(job, lastSlot(job)));
        Instant newest = lastBefore.values().stream().max(Instant::compareTo).orElseThrow();

        // Down across three slots, and long enough for the newest of them to be a misfire.
        Instant up = newest.plusSeconds(3L * PERIOD + 3);
        await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(100)).until(() -> Instant.now().isAfter(up));
        Instant restarted = Instant.now();
        ConfigurableApplicationContext third = replica("c", triggers);
        replica("d", triggers);
        awaitEveryTickJobRanASlotAfter(Instant.now().plusSeconds(PERIOD));

        Map<String, List<Instant>> missed = new LinkedHashMap<>();
        QuartzJobs.TICK_JOBS.forEach(job -> missed.put(job, slotsBetween(lastBefore.get(job), restarted.minusSeconds(2))));
        assertMisfirePolicies(missed);
        // After its RESCHEDULE_NOW misfire the interval trigger fires on the kit's grid again.
        Trigger interval = third.getBean(Scheduler.class).getTrigger(new TriggerKey("interval-latest.tick",
                QuartzSchedulerEngine.CONFIG_GROUP));
        assertThat(interval).isInstanceOf(SimpleTrigger.class);
        assertThat(interval.getStartTime().getTime() % (PERIOD * 1000L)).as("re-anchored: %s", interval.getStartTime()).isZero();
        assertThat(interval.getNextFireTime().getTime() % (PERIOD * 1000L)).isZero();
    }

    @Test
    @DisplayName("FQ3: with the database unreachable nothing fires; on its return the misfires are handled per policy and the triggers go on")
    void theDatabaseUnreachableAcrossThreeSlots() throws Exception {
        TcpProxy proxy = TcpProxy.inFrontOf(url);
        resources.add(proxy);
        Map<String, Object> properties = new LinkedHashMap<>(QuartzJobs.tickTriggers(PERIOD));
        // Fail fast while the database is away, and look again every second once it may be back.
        properties.put("spring.datasource.hikari.connection-timeout", "1000");
        properties.put("spring.datasource.hikari.validation-timeout", "500");
        properties.put("spring.quartz.properties.org.quartz.scheduler.dbFailureRetryInterval", "1000");
        properties.put("spring.quartz.properties.org.quartz.jobStore.dbRetryInterval", "1000");
        replica(proxy.through(url), "a", properties);
        awaitEveryTickJobRanASlotAfter(Instant.now());

        proxy.cut();
        Instant cut = Instant.now();
        Instant back = cut.plusSeconds(4L * PERIOD + 3);
        await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(100)).until(() -> Instant.now().isAfter(back));
        Map<String, Instant> lastBefore = new LinkedHashMap<>();
        QuartzJobs.TICK_JOBS.forEach(job -> lastBefore.put(job, lastSlot(job)));
        assertThat(lastBefore.values()).as("nothing fired while the database was unreachable")
                .allMatch(slot -> slot.isBefore(cut.plusSeconds(1)));
        Instant restored = Instant.now();
        proxy.restore();
        awaitEveryTickJobRanASlotAfter(Instant.now().plusSeconds(PERIOD));

        Map<String, List<Instant>> missed = new LinkedHashMap<>();
        QuartzJobs.TICK_JOBS.forEach(job -> missed.put(job, slotsBetween(cut.plusSeconds(PERIOD),
                restored.minusSeconds(2))));
        assertMisfirePolicies(missed);
    }
}
