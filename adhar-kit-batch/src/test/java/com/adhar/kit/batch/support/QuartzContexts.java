package com.adhar.kit.batch.support;

import com.adhar.kit.batch.scheduler.SlotIdentity;
import com.adhar.kit.batch.scheduler.quartz.QuartzSchedulerEngine;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contexts of the engine {@code quartz}: real Spring Boot applications (the kit's and Spring Boot's
 * auto-configuration, the kit's two migration streams, the clustered job store) sharing one
 * PostgreSQL database, as a service's replicas do. Short cluster timings so a dead replica is noticed
 * within seconds.
 */
public final class QuartzContexts {

    /** Every test service is one cluster. */
    public static final String SCHEDULER_NAME = "svc";

    private QuartzContexts() {
    }

    /**
     * @param url    a database of {@link PostgresDatabases}
     * @param runner the replica's name, for the test's own records
     * @return the properties of a replica on the engine quartz
     */
    public static Map<String, Object> properties(String url, String runner) {
        Map<String, Object> properties = new LinkedHashMap<>(BatchTestApplication.postgres(url));
        properties.put("adhar.batch.scheduler.enabled", "true");
        properties.put("adhar.batch.scheduler.engine", "quartz");
        properties.put("adhar.batch.quartz.scheduler-name", SCHEDULER_NAME);
        // The owner recorded on every Quartz job: the engine refuses a start without it.
        properties.put("spring.application.name", SCHEDULER_NAME);
        properties.put("adhar.batch.quartz.cluster-checkin-interval", "1s");
        properties.put("adhar.batch.scheduler.misfire-threshold", "2s");
        properties.put("adhar.batch.shutdown.timeout", "20s");
        // Quartz's scheduler thread sleeps up to idleWaitTime when nothing is due, and a trigger
        // another replica stores does not wake it.
        properties.put("spring.quartz.properties.org.quartz.scheduler.idleWaitTime", "1000");
        properties.put("test.runner", runner);
        return properties;
    }

    /**
     * @param properties the replica's properties
     * @param sources    its job configurations
     * @return the started replica
     */
    public static ConfigurableApplicationContext start(Map<String, Object> properties, Class<?>... sources) {
        return BatchTestApplication.builder(sources)
                .initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("testQuartzOverrides", properties)))
                .run();
    }

    /**
     * Stores a runtime {@code now} trigger, as {@code BatchTriggers.fireNow} will.
     *
     * @param context a replica
     * @param job     the job's bean name
     * @param name    the trigger's name
     * @param json    the INPUT parameters, a JSON object
     */
    public static void fireNow(ConfigurableApplicationContext context, String job, String name, String json)
            throws SchedulerException {
        Scheduler scheduler = context.getBean(Scheduler.class);
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        data.put(QuartzSchedulerEngine.PARAMS, json);
        scheduler.scheduleJob(QuartzSchedulerEngine.oneShot(new TriggerKey(name, QuartzSchedulerEngine.RUNTIME_GROUP),
                new JobKey(SlotIdentity.dnsLabel(job), QuartzSchedulerEngine.JOB_GROUP), data, Instant.now()));
    }

    /**
     * "Kills" a replica inside this JVM: its Quartz scheduler stops checking in and acquiring, as a
     * dead pod's would, while a run it holds stays where it is. Nothing of the kit's shutdown runs.
     *
     * @param context the replica
     */
    public static void kill(ConfigurableApplicationContext context) throws SchedulerException {
        context.getBean(Scheduler.class).shutdown(false);
    }
}
