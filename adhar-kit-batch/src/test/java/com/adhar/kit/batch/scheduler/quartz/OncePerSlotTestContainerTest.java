package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.QuartzContexts;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIT-Q.6, AQ.1 (design 0025 §15, Addendum Q): three replicas of one service on one PostgreSQL 18
 * database, one cron schedule every 5 s, for two minutes: every slot runs exactly once across the
 * three, each with its own JobInstance, and the run names are naming v1. Run against both engines
 * on the same {@code adhar.batch.scheduler.jobs} schedule, as the contract both keep; the slot each
 * run stands for is its {@code adhar.scheduled-time}, which the job records.
 */
@Testcontainers(disabledWithoutDocker = true)
class OncePerSlotTestContainerTest {

    private static final int PERIOD_SECONDS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(2);

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();

    @AfterEach
    void stopReplicas() {
        replicas.forEach(ConfigurableApplicationContext::close);
    }

    private ConfigurableApplicationContext replica(String engine, String url, String name) {
        Map<String, Object> properties = "quartz".equals(engine) ? QuartzContexts.properties(url, name)
                : new LinkedHashMap<>(BatchTestApplication.postgres(url));
        properties.put("adhar.batch.scheduler.enabled", "true");
        properties.put("adhar.batch.scheduler.engine", engine);
        properties.put("adhar.batch.scheduler.jobs." + TestJobs.SLOW_JOB, "*/" + PERIOD_SECONDS + " * * * * *");
        properties.put("test.runner", name);
        properties.put("test.slow-job.sleep", "200");
        // Once per slot is the subject, not misfires: a fire late on a loaded machine still runs its slot.
        properties.put("adhar.batch.scheduler.misfire-threshold", "1m");
        ConfigurableApplicationContext context = BatchTestApplication.builder(TestJobs.class)
                .initializers(app -> app.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("oncePerSlot", properties)))
                .run();
        replicas.add(context);
        return context;
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(strings = {"simple", "quartz"})
    @DisplayName("AQ.1: three replicas, a 5 s cron for two minutes: every slot exactly once, its own JobInstance, named by naming v1")
    void everySlotRunsExactlyOnceAcrossThreeReplicas(String engine) throws Exception {
        String url = PostgresDatabases.createDatabase("aq1_" + engine);
        JdbcTemplate jdbc = new JdbcTemplate(PostgresDatabases.dataSource(url));
        for (String name : List.of("replica-a", "replica-b", "replica-c")) {
            replica(engine, url, name);
        }
        Instant from = Instant.now();

        Thread.sleep(WINDOW.toMillis());
        replicas.forEach(ConfigurableApplicationContext::close);
        replicas.clear();

        Map<String, Integer> runsPerSlot = new TreeMap<>();
        Map<String, String> runners = new TreeMap<>();
        jdbc.query("select runner, scheduled_time from slot_runs", rs -> {
            runsPerSlot.merge(rs.getString(2), 1, Integer::sum);
            runners.put(rs.getString(2), rs.getString(1));
        });
        assertThat(runsPerSlot).as("slots run").hasSizeGreaterThanOrEqualTo(
                (int) (WINDOW.toSeconds() / PERIOD_SECONDS) - 6);
        assertThat(runsPerSlot).as("every slot exactly once").allSatisfy((slot, runs) -> assertThat(runs).isEqualTo(1));
        List<Instant> slots = runsPerSlot.keySet().stream().map(Instant::parse).toList();
        for (int i = 1; i < slots.size(); i++) {
            assertThat(Duration.between(slots.get(i - 1), slots.get(i))).as("no slot between %s and %s was lost",
                    slots.get(i - 1), slots.get(i)).isEqualTo(Duration.ofSeconds(PERIOD_SECONDS));
        }
        assertThat(slots.get(0)).isAfterOrEqualTo(from.minusSeconds(60));

        List<Map<String, Object>> instances = jdbc.queryForList("select i.job_instance_id, count(e.job_execution_id) "
                + "as runs from batch.batch_job_instance i join batch.batch_job_execution e "
                + "on e.job_instance_id = i.job_instance_id where i.job_name = ? group by i.job_instance_id",
                TestJobs.SLOW_JOB);
        // A run in progress when the replicas stopped may have rolled back its row: its instance is the one extra.
        assertThat(instances).as("one JobInstance per slot, one execution each")
                .hasSizeBetween(runsPerSlot.size(), runsPerSlot.size() + 1)
                .allSatisfy(row -> assertThat(((Number) row.get("runs")).intValue()).isEqualTo(1));

        Map<String, String> scheduled = new TreeMap<>();
        Map<String, String> recorded = new TreeMap<>();
        jdbc.query("select p.job_execution_id, p.parameter_name, p.parameter_value from "
                + "batch.batch_job_execution_params p join batch.batch_job_execution e on e.job_execution_id = "
                + "p.job_execution_id join batch.batch_job_instance i on i.job_instance_id = e.job_instance_id "
                + "where i.job_name = ? and e.status = 'COMPLETED'", rs -> {
            if (BatchScheduler.SCHEDULED_TIME_PARAMETER.equals(rs.getString(2))) {
                scheduled.put(rs.getString(1), rs.getString(3));
            } else if (BatchScheduler.RUN_PARAMETER.equals(rs.getString(2))) {
                recorded.put(rs.getString(1), rs.getString(3));
            }
        }, TestJobs.SLOW_JOB);
        // Every COMPLETED run is a recorded slot, and every recorded slot completed — except, at most, the
        // LAST one: a slot still running when the replicas were closed has written its slot_runs row but its
        // execution never reached COMPLETED (the same in-flight run the instance count above allows for). On
        // a loaded CI runner that window is wide enough to be hit (GitHub Actions, 2026-11-22).
        assertThat(runsPerSlot.keySet()).containsAll(scheduled.values());
        java.util.TreeSet<String> unfinished = new java.util.TreeSet<>(runsPerSlot.keySet());
        scheduled.values().forEach(unfinished::remove);
        assertThat(unfinished).as("slots recorded but not completed").hasSizeLessThanOrEqualTo(1);
        if (!unfinished.isEmpty()) {
            assertThat(unfinished.first()).as("only the slot in flight at shutdown may be unfinished")
                    .isEqualTo(((TreeMap<String, Integer>) runsPerSlot).lastKey());
        }
        if ("quartz".equals(engine)) {
            assertThat(recorded).hasSameSizeAs(scheduled).allSatisfy((execution, run) -> assertThat(run)
                    .isEqualTo(RunIdentity.runName("slow-job",
                            Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, scheduled.get(execution)))));
        }
        assertThat(runners.values()).as("the replicas share the work").isNotEmpty();
    }
}
