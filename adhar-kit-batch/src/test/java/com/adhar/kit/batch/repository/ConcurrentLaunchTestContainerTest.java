package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.DefaultJobKeyGenerator;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIT-0.4 (design 0025 §5.8): two concurrent launches of the same identity on PostgreSQL 18 yield
 * exactly one JobExecution. The JobInstance identity is the duplicate guard the Kubernetes runner
 * (A1.2) and the embedded scheduler (A0.3) both rest on; this proves it on the real repository,
 * with no lock in front of it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class}, properties = "test.slow-job.sleep=1500")
class ConcurrentLaunchTestContainerTest {

    private static final String URL = PostgresDatabases.createDatabase("k04");
    private static final int LAUNCHES = 4;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.postgres(URL).forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    JobOperator jobOperator;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier(TestJobs.SLOW_JOB)
    Job slowJob;

    @Test
    @DisplayName("concurrent launches of one identity: exactly one execution, the others are refused")
    void concurrentLaunchesOfOneIdentityRunOnce() throws Exception {
        JobParameters identity = new JobParametersBuilder()
                .addString("businessDate", "2026-11-13", true)
                .addString("adhar.scheduled-time", "k04-concurrent", true)
                .toJobParameters();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(LAUNCHES);
        List<Future<Outcome>> outcomes = new ArrayList<>();
        try {
            for (int i = 0; i < LAUNCHES; i++) {
                outcomes.add(pool.submit(launch(identity, go)));
            }
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        long started = outcomes.stream().filter(outcome -> get(outcome) == Outcome.STARTED).count();
        long refused = outcomes.stream().filter(outcome -> get(outcome) == Outcome.REFUSED).count();
        assertThat(started).as("exactly one launch starts the execution").isEqualTo(1);
        assertThat(refused).as("every other launch is refused by the repository").isEqualTo(LAUNCHES - 1);

        JobInstance instance = jobRepository.getJobInstance(TestJobs.SLOW_JOB, identity);
        assertThat(instance).isNotNull();
        List<JobExecution> executions = jobRepository.getJobExecutions(instance);
        assertThat(executions).hasSize(1);
        assertThat(executions.getFirst().getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("select count(*) from slot_runs where scheduled_time = ?",
                Integer.class, "k04-concurrent")).isEqualTo(1);
        // Exactly one instance row exists for this identity: the SERIALIZABLE instance creation held.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from batch.batch_job_instance where job_name = ? and job_key = ?", Integer.class,
                TestJobs.SLOW_JOB, new DefaultJobKeyGenerator().generateKey(identity))).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent launches of DIFFERENT identities of one job all start, despite PG's serialization conflicts")
    void concurrentLaunchesOfDifferentIdentitiesAllStart() throws Exception {
        // Without InstanceCreationRetry every round but one launch per round is refused (probe 2026-11-14:
        // 59 of 80 launches, CannotAcquireLockException "could not serialize access").
        for (int round = 0; round < 5; round++) {
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(LAUNCHES);
            List<Future<JobExecution>> launches = new ArrayList<>();
            List<JobParameters> identities = new ArrayList<>();
            try {
                for (int i = 0; i < LAUNCHES; i++) {
                    JobParameters identity = new JobParametersBuilder()
                            .addString("adhar.scheduled-time", "k04-distinct-" + round + "-" + i, true)
                            .toJobParameters();
                    identities.add(identity);
                    launches.add(pool.submit(() -> {
                        go.await();
                        return InstanceCreationRetry.start(jobOperator, slowJob, identity);
                    }));
                }
                go.countDown();
                pool.shutdown();
                assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
            } finally {
                pool.shutdownNow();
            }
            for (int i = 0; i < LAUNCHES; i++) {
                assertThat(launches.get(i).get().getStatus()).isEqualTo(BatchStatus.COMPLETED);
                JobInstance instance = jobRepository.getJobInstance(TestJobs.SLOW_JOB, identities.get(i));
                assertThat(jobRepository.getJobExecutions(instance)).hasSize(1);
            }
        }
    }

    private Callable<Outcome> launch(JobParameters identity, CountDownLatch go) {
        return () -> {
            go.await();
            try {
                jobOperator.start(slowJob, identity);
                return Outcome.STARTED;
            } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException
                     | DataIntegrityViolationException | ConcurrencyFailureException ex) {
                return Outcome.REFUSED;
            } catch (IllegalStateException ex) {
                // Spring Batch stores the JobInstance and its first JobExecution in two transactions. A
                // launch that reads the instance between them is refused with this wording (the case
                // InstanceCreationRetry starts again); it created nothing, so it is a refusal here too.
                if (ex.getMessage() != null
                        && ex.getMessage().startsWith(InstanceCreationRetry.INSTANCE_WITHOUT_EXECUTION)) {
                    return Outcome.REFUSED;
                }
                throw ex;
            }
        };
    }

    private static Outcome get(Future<Outcome> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new AssertionError("a launch failed in an unexpected way", ex);
        }
    }

    enum Outcome { STARTED, REFUSED }
}
