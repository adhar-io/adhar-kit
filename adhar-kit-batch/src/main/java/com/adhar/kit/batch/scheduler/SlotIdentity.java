package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.params.JobParametersResolver.ResolvedParameters;
import com.adhar.kit.batch.params.RunIdentity;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The identity of one fire of an in-service schedule, the ONE function both engines of
 * {@link BatchScheduler} derive a run's JobParameters from (design 0025 §5.20.5, identity rules
 * 1-2), so a service that changes engine keeps every JobInstance, restart point and run name.
 *
 * <ul>
 *   <li>The declared parameters are resolved by the same {@link JobParametersResolver} as the
 *       Kubernetes runner's, with the slot's nominal time as the scheduled time: a
 *       {@code @JobParam(source = SCHEDULE)} business date is the nominal date in the trigger's
 *       zone, exactly as for a CronJob run.</li>
 *   <li>A fire WITH a nominal time, of a job that declares no identifying schedule-sourced
 *       parameter, also gets the identifying {@value BatchScheduler#SCHEDULED_TIME_PARAMETER} =
 *       the nominal time, ISO-8601 UTC, whole seconds. A job that declares one is identified by it
 *       instead, so a trigger firing more often than its business date changes runs once per date.</li>
 *   <li>A fire without one (a Quartz {@code fireNow}) carries the resolved parameters only.</li>
 *   <li>Every run carries the non-identifying {@value BatchScheduler#TRIGGER_PARAMETER} =
 *       {@value BatchScheduler#EMBEDDED_TRIGGER}.</li>
 * </ul>
 *
 * @param parameters  the run's JobParameters: identity and provenance
 * @param identifying the identifying values as strings, which name the run (naming v1)
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record SlotIdentity(JobParameters parameters, Map<String, String> identifying) {

    /** The resolution of a job that declares no parameters. */
    public static final ResolvedParameters UNDECLARED = new ResolvedParameters(new JobParameters(), Map.of(),
            Optional.empty());

    public SlotIdentity {
        identifying = Collections.unmodifiableMap(new LinkedHashMap<>(identifying));
    }

    /**
     * @param spec     the job's declared parameters
     * @param resolved its parameters as {@link JobParametersResolver} resolved them for this fire
     * @param nominal  the fire's nominal time, {@code null} for a fire that has none
     * @return the fire's identity
     */
    public static SlotIdentity of(JobParameterSpec spec, ResolvedParameters resolved, Instant nominal) {
        JobParametersBuilder builder = new JobParametersBuilder(resolved.parameters());
        Map<String, String> identifying = new LinkedHashMap<>(resolved.identifying());
        if (nominal != null && !declaresScheduleIdentity(spec)) {
            String scheduled = scheduledTime(nominal);
            builder.addString(BatchScheduler.SCHEDULED_TIME_PARAMETER, scheduled, true);
            identifying.put(BatchScheduler.SCHEDULED_TIME_PARAMETER, scheduled);
        }
        builder.addString(BatchScheduler.TRIGGER_PARAMETER, BatchScheduler.EMBEDDED_TRIGGER, false);
        return new SlotIdentity(builder.toJobParameters(), identifying);
    }

    /**
     * Resolves a fire's input through the runner's resolver, then derives its identity.
     *
     * @param resolver the resolver
     * @param spec     the job's declared parameters
     * @param input    values for declared INPUT parameters (a trigger's own, or a runtime call's)
     * @param nominal  the fire's nominal time, {@code null} for none
     * @param zone     the trigger's time zone, in which a schedule-sourced date is taken
     * @return the fire's identity
     * @throws com.adhar.kit.batch.params.ParameterValidationException when the input does not
     *                                                                 resolve, as exit 2 would
     */
    public static SlotIdentity resolve(JobParametersResolver resolver, JobParameterSpec spec,
                                       Map<String, String> input, Instant nominal, ZoneId zone) {
        Optional<OffsetDateTime> scheduled = Optional.ofNullable(nominal)
                .map(time -> time.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
        return of(spec, resolver.resolve(spec, input, scheduled, zone), nominal);
    }

    /**
     * @param spec the job's declared parameters
     * @return whether it declares an identifying {@code @JobParam(source = SCHEDULE)}, which then
     *         replaces {@value BatchScheduler#SCHEDULED_TIME_PARAMETER} as the slot's identity
     */
    public static boolean declaresScheduleIdentity(JobParameterSpec spec) {
        return spec.parameters().stream()
                .anyMatch(param -> param.identifying() && param.source() == ParamSource.SCHEDULE);
    }

    /**
     * @param nominal a nominal fire time
     * @return its {@value BatchScheduler#SCHEDULED_TIME_PARAMETER} value: ISO-8601 UTC, whole seconds
     */
    public static String scheduledTime(Instant nominal) {
        // Whole SECONDS, not the minute design 0025 §5.16 named until 2026-11-13: the schedules
        // are six-field cron expressions with second resolution, so "0/30 * * * * *" has two slots
        // a minute. Truncated to the minute both would share one JobInstance and the second would
        // be skipped as already run. A minute-resolution schedule gets the same identity either way.
        return DateTimeFormatter.ISO_INSTANT.format(nominal.truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * @param scheduleName the schedule's name: the {@code <batchjob>} of naming v1
     * @return {@code <scheduleName>-<hash8>} over the identifying parameters
     */
    public String runName(String scheduleName) {
        return RunIdentity.runName(scheduleName, identifying);
    }
}
