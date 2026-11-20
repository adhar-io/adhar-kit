package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Instance;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.LastRun;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Report;
import org.quartz.CronTrigger;
import org.quartz.DailyTimeIntervalTrigger;
import org.quartz.JobDataMap;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TimeOfDay;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the schedule report (design 0025 §5.20.8) from the Quartz store (triggers, their states
 * and fire times, the replicas checked in) and the JobRepository (each job's last run), which live
 * in one database: every replica therefore writes the same document.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
final class QuartzScheduleReport {

    private QuartzScheduleReport() {
    }

    /**
     * @param engine the engine
     * @return the report of its scheduler, now
     * @throws SchedulerException when the store cannot be read
     */
    static Report build(QuartzSchedulerEngine engine) throws SchedulerException {
        Scheduler scheduler = engine.scheduler();
        List<ScheduleReportWriter.Job> jobs = new ArrayList<>();
        for (Map.Entry<String, org.springframework.batch.core.job.Job> entry : new TreeMap<>(engine.jobs()).entrySet()) {
            String bean = entry.getKey();
            if (entry.getValue().getJobParametersIncrementer() != null) {
                continue;
            }
            List<ScheduleReportWriter.Trigger> triggers = new ArrayList<>();
            List<? extends Trigger> stored = new ArrayList<>(scheduler.getTriggersOfJob(engine.jobKey(bean)));
            stored.sort(Comparator.comparing((Trigger trigger) -> trigger.getKey().getGroup())
                    .thenComparing(trigger -> trigger.getKey().getName()));
            for (Trigger trigger : stored) {
                triggers.add(trigger(scheduler, trigger));
            }
            jobs.add(new ScheduleReportWriter.Job(bean, engine.scheduleNameOf(bean), triggers,
                    lastRun(engine.repository(), entry.getValue().getName())));
        }
        return new Report(ScheduleReportWriter.VERSION, QuartzSchedulerEngine.NAME, engine.schedulerName(),
                engine.clock().instant(), false, instances(engine), jobs);
    }

    static List<Instance> instances(QuartzSchedulerEngine engine) {
        List<Instance> instances = new ArrayList<>();
        engine.checkIns().forEach((id, checkin) -> instances.add(new Instance(id, checkin)));
        return instances;
    }

    private static ScheduleReportWriter.Trigger trigger(Scheduler scheduler, Trigger trigger) throws SchedulerException {
        JobDataMap data = trigger.getJobDataMap();
        String kind = data.getString(QuartzSchedulerEngine.KIND);
        String expression = null;
        String every = null;
        Instant start = null;
        String from = null;
        String to = null;
        List<String> days = null;
        if (QuartzSchedulerEngine.KIND_CONFIG.equals(kind)) {
            if (trigger instanceof CronTrigger cron) {
                kind = "cron";
                expression = cron.getCronExpression();
            } else if (trigger instanceof DailyTimeIntervalTrigger daily) {
                kind = "daily";
                every = java.time.Duration.ofSeconds(seconds(daily)).toString();
                from = time(daily.getStartTimeOfDay());
                to = time(daily.getEndTimeOfDay());
                days = new ArrayList<>();
                for (Integer day : new java.util.TreeSet<>(daily.getDaysOfWeek())) {
                    days.add(DayOfWeek.of((day + 5) % 7 + 1).name().substring(0, 3));
                }
            } else {
                kind = "interval";
                every = data.getString(QuartzSchedulerEngine.GRID_EVERY);
                String anchor = data.getString(QuartzSchedulerEngine.GRID_START);
                start = anchor != null ? Instant.parse(anchor) : null;
            }
        }
        String key = trigger.getKey().getGroup() + "." + trigger.getKey().getName();
        return new ScheduleReportWriter.Trigger(key, kind, expression, every, start, from, to, days,
                data.getString(QuartzSchedulerEngine.ZONE), trigger.getCalendarName(),
                data.getString(QuartzSchedulerEngine.MISFIRE), scheduler.getTriggerState(trigger.getKey()).name(),
                instant(trigger.getPreviousFireTime()), instant(trigger.getNextFireTime()));
    }

    private static long seconds(DailyTimeIntervalTrigger daily) {
        long interval = daily.getRepeatInterval();
        return switch (daily.getRepeatIntervalUnit()) {
            case MINUTE -> interval * 60;
            case HOUR -> interval * 3600;
            default -> interval;
        };
    }

    private static String time(TimeOfDay time) {
        return time == null ? null
                : LocalTime.of(time.getHour(), time.getMinute(), time.getSecond()).toString();
    }

    private static LastRun lastRun(JobRepository repository, String jobName) {
        List<JobInstance> instances = repository.getJobInstances(jobName, 0, 1);
        if (instances.isEmpty()) {
            return null;
        }
        JobExecution execution = repository.getLastJobExecution(instances.get(0));
        if (execution == null) {
            return null;
        }
        BatchStatus status = execution.getStatus();
        String outcome = status.isRunning() ? "running" : status.name().toLowerCase(Locale.ROOT);
        return new LastRun(execution.getJobParameters().getString(BatchScheduler.RUN_PARAMETER),
                execution.getId(), status.name(), outcome,
                execution.getJobParameters().getString(BatchScheduler.TRIGGER_KEY_PARAMETER),
                local(execution.getStartTime()), local(execution.getEndTime()));
    }

    private static Instant local(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
