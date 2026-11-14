package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.KubernetesLookupException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.OwnerJob;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves a run's {@link RunContext} from the pod's environment (set by the {@code BatchJob}
 * composition and the downward API) and one GET each of the owner Job and the pod.
 *
 * <p>A scheduled run's business date is the owner Job's {@value KubernetesOwnerLookup#SCHEDULED_TIMESTAMP}
 * in the job's time zone. If the API cannot be asked for it, the run fails as a start-up failure
 * (exit 1, retried by the Job): a transient API error must not become a permanent "invalid" run,
 * and the current time must never stand in for the schedule. For any other trigger the reads are
 * best effort (uid, digest, attempt).</p>
 *
 * <p>(2026-11-14) Design 0025 §5.7 lists "unresolved business date" under exit 2. That is the
 * owner Job read WITHOUT a scheduled time (exit 2, reason {@code business-date}); an API that
 * cannot be read is treated like §5.7's "DataSource unreachable at start", exit 1.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class RunContextResolver {

    private final Function<String, String> env;
    private final KubernetesOwnerLookup lookup;
    private final BatchProperties properties;

    /**
     * @param env        the process environment
     * @param lookup     the Kubernetes API reads
     * @param properties {@code adhar.batch.*} (the job and the role are bound from
     *                   {@code ADHAR_BATCH_JOB} and {@code ADHAR_BATCH_ROLE})
     */
    public RunContextResolver(Function<String, String> env, KubernetesOwnerLookup lookup, BatchProperties properties) {
        this.env = env;
        this.lookup = lookup;
        this.properties = properties;
    }

    /**
     * @return the run's context
     * @throws ParameterValidationException  for an unknown trigger, role or time zone (exit 2)
     * @throws KubernetesLookupException when a scheduled run cannot read its owner Job (exit 1)
     */
    public RunContext resolve() {
        String jobName = Optional.ofNullable(properties.getJob()).orElse(env("ADHAR_BATCH_JOB")).strip();
        RunTrigger trigger = parse(jobName, () -> RunTrigger.parse(env("ADHAR_BATCH_TRIGGER")));
        ZoneId zone = parse(jobName, () -> {
            String value = env("ADHAR_BATCH_TIME_ZONE");
            return value.isBlank() ? ZoneOffset.UTC : ZoneId.of(value.strip());
        });
        OptionalInt index = parse(jobName, () -> {
            String value = env("JOB_COMPLETION_INDEX");
            return value.isBlank() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value.strip()));
        });
        BatchRole role = properties.getRole().effective(index);
        String k8sJob = env("ADHAR_BATCH_K8S_JOB");
        String podName = env("POD_NAME").isBlank() ? env("HOSTNAME") : env("POD_NAME");

        Optional<OwnerJob> owner = Optional.empty();
        if (!k8sJob.isBlank()) {
            try {
                owner = lookup.job(k8sJob);
            } catch (KubernetesLookupException ex) {
                if (trigger == RunTrigger.SCHEDULE) {
                    throw ex;
                }
                log.warn("Could not read the owner Job {}: {}", k8sJob, ex.getMessage());
            }
        }
        Optional<OffsetDateTime> scheduled = trigger == RunTrigger.SCHEDULE
                ? owner.flatMap(OwnerJob::scheduledTime) : Optional.empty();
        if (trigger == RunTrigger.SCHEDULE && scheduled.isEmpty()) {
            log.warn("A scheduled run without a scheduled time (owner Job '{}'): its business date can only come "
                    + "from its parameters", k8sJob);
        }
        Optional<LocalDate> businessDate = scheduled.map(time -> time.atZoneSameInstant(zone).toLocalDate());
        String digest = imageDigest(podName);
        String jobUid = owner.map(OwnerJob::uid).orElse("");
        String correlation = firstNonBlank(env("ADHAR_BATCH_CORRELATION_ID"), jobUid, env("POD_UID"),
                UUID.randomUUID().toString());
        return new RunContext(jobName, firstNonBlank(env("ADHAR_BATCH_NAME"), jobName), role, trigger,
                env("ADHAR_BATCH_ACTOR"), k8sJob, jobUid, podName, env("POD_UID"), env("NODE_NAME"), scheduled, zone,
                businessDate, digest, owner.map(job -> job.failed() + 1).orElse(1), index, correlation);
    }

    private String imageDigest(String podName) {
        if (podName.isBlank()) {
            return "";
        }
        try {
            return lookup.pod(podName).map(KubernetesOwnerLookup.Pod::imageDigest).orElse("");
        } catch (KubernetesLookupException ex) {
            log.warn("Could not read pod {} for its image digest: {}", podName, ex.getMessage());
            return "";
        }
    }

    private String env(String name) {
        String value = env.apply(name);
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static <T> T parse(String jobName, Supplier<T> parser) {
        try {
            return parser.get();
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw new ParameterValidationException(jobName, List.of(ex.getMessage()),
                    ParameterValidationException.PARAMETERS);
        }
    }
}
