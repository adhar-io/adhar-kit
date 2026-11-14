package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A0.3 (design 0025 §15): two embedded-mode contexts sharing one database, firing the same cron
 * slot, produce exactly one JobExecution. Two REAL Spring Boot contexts (the auto-configuration,
 * the kit's Flyway schema, the JDBC scheduler lock, the JDBC repository) on one PostgreSQL 18
 * database; their schedulers fire the same nominal time at once. Three layers may refuse the
 * second fire - the lock, the JobInstance check, the repository's instance identity - and the
 * test does not care which one did, only that one run happened.
 */
@Testcontainers(disabledWithoutDocker = true)
class TwoContextSchedulerTestContainerTest {

    // A slot of NOW: the schedulers run on the real clock, so a fixed past slot would misfire.
    private static final Instant SLOT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String SLOT_TEXT = DateTimeFormatter.ISO_INSTANT.format(SLOT);

    private static String url;
    private static ConfigurableApplicationContext first;
    private static ConfigurableApplicationContext second;

    @BeforeAll
    static void startTwoContexts() {
        url = PostgresDatabases.createDatabase("a03");
        first = context("first");
        second = context("second");
    }

    @AfterAll
    static void stopTheContexts() {
        if (second != null) {
            second.close();
        }
        if (first != null) {
            first.close();
        }
    }

    private static ConfigurableApplicationContext context(String runner) {
        Map<String, Object> properties = BatchTestApplication.postgres(url);
        properties.put("adhar.batch.scheduler.enabled", "true");
        properties.put("test.runner", runner);
        properties.put("test.slow-job.sleep", "2000");
        return BatchTestApplication.builder(TestJobs.class).properties(properties).run();
    }

    @Test
    @DisplayName("the same slot fired by two contexts at once runs exactly once")
    void theSameSlotFiredTwiceRunsOnce() throws Exception {
        List<Callable<SlotOutcome>> fires = List.of(fire(first), fire(second));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(fires.size());
        List<Future<SlotOutcome>> outcomes = new ArrayList<>();
        try {
            for (Callable<SlotOutcome> fire : fires) {
                outcomes.add(pool.submit(() -> {
                    go.await();
                    return fire.call();
                }));
            }
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        List<SlotOutcome> results = new ArrayList<>();
        for (Future<SlotOutcome> outcome : outcomes) {
            results.add(outcome.get());
        }
        assertThat(results).containsOnlyOnce(SlotOutcome.RAN);
        assertThat(results).filteredOn(outcome -> outcome != SlotOutcome.RAN)
                .allMatch(EnumSet.of(SlotOutcome.LOCKED_ELSEWHERE, SlotOutcome.ALREADY_RAN)::contains);

        JobRepository repository = first.getBean(JobRepository.class);
        JobInstance instance = repository.getJobInstance(TestJobs.SLOW_JOB, slot(SLOT));
        assertThat(instance).isNotNull();
        List<JobExecution> executions = repository.getJobExecutions(instance);
        assertThat(executions).as("exactly one JobExecution for the slot").hasSize(1);
        JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("select count(*) from slot_runs where scheduled_time = ?", Integer.class,
                SLOT_TEXT)).isEqualTo(1);
    }

    private static Callable<SlotOutcome> fire(ConfigurableApplicationContext context) {
        BatchScheduler scheduler = context.getBean(BatchScheduler.class);
        Job job = context.getBean(TestJobs.SLOW_JOB, Job.class);
        return () -> scheduler.runSlot(TestJobs.SLOW_JOB, job, SLOT);
    }

    /** The parameters of a slot of a job that declares none, as both engines derive them. */
    private static JobParameters slot(Instant nominal) {
        return SlotIdentity.of(JobParameterSpec.none("job"), SlotIdentity.UNDECLARED, nominal).parameters();
    }
}
