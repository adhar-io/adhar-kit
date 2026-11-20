package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.support.QuartzJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.batch.core.job.Job;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.5, FQ4 and FQ5 (design 0025 §5.20.10): one {@code scheduler-name} is one service's. A start
 * that finds another application's jobs under its scheduler name fails, before it stores anything;
 * a job this application stored and no longer defines was retired by a newer version and goes.
 * (FQ5's trigger half, the reconcile replacing changed triggers and removing dropped ones, is
 * {@code QuartzSchedulerEngineTest}'s "a restart keeps an unchanged trigger ...".)
 */
class SchedulerNameCollisionTest extends QuartzEngineFixture {

    private QuartzSchedulerEngine engineOf(String application, Map<String, Job> jobs) {
        engine = new QuartzSchedulerEngine(scheduler, operator, repository, jobs, specs, resolver, ownership, jdbc,
                PREFIX, properties, application, "1.4.0", calendars(), clock);
        return engine;
    }

    @Test
    @DisplayName("FQ4: a second service on the same scheduler name fails its start, naming the other application, and stores nothing")
    void anotherApplicationsJobsFailTheStart() throws Exception {
        cron(DATED_JOB, "0 0 9 ? * *");
        reconciled();
        java.util.Set<JobKey> before = scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyJobGroup());
        TriggerKey trigger = configKey(DATED_JOB);
        Map<String, Job> otherJobs = Map.of("exportJob", beans.getBean(QuartzJobs.CHUNK_JOB, Job.class));
        properties.getQuartz().getTriggers().clear();

        assertThatThrownBy(() -> engineOf("billing", otherJobs).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("of application 'svc'")
                .hasMessageContaining("this is application 'billing' (jobs [export-job])")
                .hasMessageContaining("FQ4");

        assertThat(scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyJobGroup()))
                .as("nothing stored, nothing removed").isEqualTo(before);
        assertThat(scheduler.checkExists(trigger)).as("the first service's trigger is untouched").isTrue();
        engine = null;
    }

    @Test
    @DisplayName("FQ4: the same job name stored by another application is refused too")
    void theSameJobOfAnotherApplicationIsRefused() throws Exception {
        reconciled();

        assertThatThrownBy(() -> engineOf("billing", beans.getBeansOfType(Job.class)).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("of application 'svc'");
        engine = null;
    }

    @Test
    @DisplayName("FQ4: a job in the kit's group that names no application and that this one does not define is refused")
    void anUnclaimedForeignJobIsRefused() throws Exception {
        scheduler.addJob(JobBuilder.newJob(BatchLaunchJob.class).withIdentity("legacy-export",
                QuartzSchedulerEngine.JOB_GROUP).storeDurably().usingJobData(QuartzSchedulerEngine.JOB, "legacyExport")
                .build(), true);

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already holds job adhar-batch.legacy-export, which this application does not define");
        engine = null;
    }

    @Test
    @DisplayName("FQ5: a job this application stored and no longer defines is removed with its triggers; other groups are left alone")
    void aRetiredJobIsRemoved() throws Exception {
        JobKey retired = new JobKey("old-report", QuartzSchedulerEngine.JOB_GROUP);
        scheduler.addJob(JobBuilder.newJob(BatchLaunchJob.class).withIdentity(retired).storeDurably()
                .usingJobData(QuartzSchedulerEngine.JOB, "oldReport")
                .usingJobData(QuartzSchedulerEngine.APPLICATION, APPLICATION).build(), true);
        scheduler.scheduleJob(TriggerBuilder.newTrigger().withIdentity("old-report.tick", QuartzSchedulerEngine.CONFIG_GROUP)
                .forJob(retired).withSchedule(SimpleScheduleBuilder.repeatHourlyForever()).build());
        JobKey own = new JobKey("housekeeping", "application");
        scheduler.addJob(JobBuilder.newJob(BatchLaunchJob.class).withIdentity(own).storeDurably().build(), true);

        reconciled();

        assertThat(scheduler.checkExists(retired)).isFalse();
        assertThat(scheduler.checkExists(new TriggerKey("old-report.tick", QuartzSchedulerEngine.CONFIG_GROUP))).isFalse();
        assertThat(scheduler.checkExists(own)).as("the application's own Quartz jobs share the store").isTrue();
        assertThat(scheduler.getJobDetail(engine.jobKey(DATED_JOB)).getJobDataMap()
                .getString(QuartzSchedulerEngine.APPLICATION)).isEqualTo(APPLICATION);
    }

    @Test
    @DisplayName("a restart of the same application, with the same jobs, starts")
    void theSameApplicationRestarts() {
        reconciled();
        engine.stop();

        reconciled();

        assertThat(engine.listScheduledJobs()).isEmpty();
    }
}
