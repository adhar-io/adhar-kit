package com.adhar.kit.batch.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link SchedulerMetrics} on a Micrometer {@link MeterRegistry}.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class MicrometerSchedulerMetrics implements SchedulerMetrics {

    private final MeterRegistry registry;
    private final Map<Tags, AtomicLong> nextFires = new ConcurrentHashMap<>();
    private final Map<Tags, Gauge> nextFireGauges = new ConcurrentHashMap<>();
    private final AtomicLong instances = new AtomicLong();
    private final Counter reportFailures;

    /**
     * @param registry the application's meter registry
     */
    public MicrometerSchedulerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.reportFailures = Counter.builder(REPORT_FAILURES)
                .description("Schedule report writes that failed (advisory; scheduling is unaffected)")
                .register(registry);
        Gauge.builder(INSTANCES, instances, AtomicLong::get)
                .description("Replicas checked in to the in-service Quartz cluster")
                .register(registry);
    }

    @Override
    public void fired(String job, String trigger, SlotOutcome outcome) {
        Counter.builder(FIRES)
                .description("In-service schedule fires by outcome")
                .tags(Tags.of("job", job, "trigger", trigger, "outcome", SchedulerMetrics.outcomeTag(outcome)))
                .register(registry)
                .increment();
    }

    @Override
    public void nextFire(String job, String trigger, Instant next) {
        Tags tags = Tags.of("job", job, "trigger", trigger);
        if (next == null) {
            nextFires.remove(tags);
            Gauge gauge = nextFireGauges.remove(tags);
            if (gauge != null) {
                registry.remove(gauge);
            }
            return;
        }
        nextFires.computeIfAbsent(tags, key -> {
            AtomicLong value = new AtomicLong();
            nextFireGauges.put(key, Gauge.builder(NEXT_FIRE, value, AtomicLong::get)
                    .description("The next fire of an in-service trigger, epoch seconds")
                    .baseUnit("seconds")
                    .tags(key)
                    .register(registry));
            return value;
        }).set(next.getEpochSecond());
    }

    @Override
    public void instances(int count) {
        instances.set(count);
    }

    @Override
    public void reportFailed() {
        reportFailures.increment();
    }

    @Override
    public void calendarFailed(String calendar) {
        Counter.builder(CALENDAR_FAILURES)
                .description("Business calendar refreshes that kept the previous version")
                .tags("calendar", calendar)
                .register(registry)
                .increment();
    }

    @Override
    public void cronDstDifference(String job) {
        Counter.builder(CRON_DST_DIFFERENCES)
                .description("Zones in which a converted Spring cron fires elsewhere at a clock change (it started, "
                        + "with a warning)")
                .tags("job", job)
                .register(registry)
                .increment();
    }
}
