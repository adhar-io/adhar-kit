package com.adhar.kit.batch.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Simple job scheduler that uses Spring's {@link TaskScheduler} to schedule
 * batch jobs based on cron expressions.
 *
 * <p>Manages the lifecycle of scheduled jobs, allowing jobs to be scheduled,
 * cancelled, and listed at runtime.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * batchScheduler.scheduleJob("dailyReport", "0 0 2 * * *");   // Run at 2 AM daily
 * batchScheduler.scheduleJob("hourlySync", "0 0 * * * *");     // Run every hour
 * batchScheduler.cancelScheduledJob("hourlySync");              // Cancel the hourly job
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class BatchScheduler {

    private final TaskScheduler taskScheduler;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@code BatchScheduler}.
     *
     * @param taskScheduler      the Spring task scheduler used for cron-based scheduling
     * @param jobLauncher        the Spring Batch job launcher
     * @param applicationContext the application context for looking up job beans by name
     */
    public BatchScheduler(TaskScheduler taskScheduler, JobLauncher jobLauncher, ApplicationContext applicationContext) {
        this.taskScheduler = taskScheduler;
        this.jobLauncher = jobLauncher;
        this.applicationContext = applicationContext;
    }

    /**
     * Schedules a batch job to run according to the given cron expression.
     *
     * <p>The job bean is looked up by name from the Spring application context.
     * Each execution receives a unique {@code run.id} parameter to allow re-execution.</p>
     *
     * <p>If a job with the same name is already scheduled, the existing schedule
     * is cancelled and replaced with the new one.</p>
     *
     * @param jobName        the Spring bean name of the {@link Job} to schedule
     * @param cronExpression the cron expression defining the schedule
     * @throws IllegalArgumentException if no job bean with the given name exists
     */
    public void scheduleJob(String jobName, String cronExpression) {
        // Cancel any existing schedule for this job
        cancelScheduledJob(jobName);

        var trigger = new CronTrigger(cronExpression);

        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                var job = applicationContext.getBean(jobName, Job.class);
                JobParameters params = new JobParametersBuilder()
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters();
                jobLauncher.run(job, params);
                log.info("Scheduled job [{}] executed successfully", jobName);
            } catch (Exception e) {
                log.error("Scheduled job [{}] failed: {}", jobName, e.getMessage(), e);
            }
        }, trigger);

        scheduledJobs.put(jobName, future);
        log.info("Scheduled job [{}] with cron expression: {}", jobName, cronExpression);
    }

    /**
     * Cancels a previously scheduled job.
     *
     * @param jobName the name of the job to cancel
     * @return {@code true} if the job was found and cancelled, {@code false} if no such scheduled job exists
     */
    public boolean cancelScheduledJob(String jobName) {
        var future = scheduledJobs.remove(jobName);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled scheduled job [{}]", jobName);
            return true;
        }
        return false;
    }

    /**
     * Returns an unmodifiable set of all currently scheduled job names.
     *
     * @return the set of scheduled job names
     */
    public Set<String> listScheduledJobs() {
        return Collections.unmodifiableSet(scheduledJobs.keySet());
    }
}
