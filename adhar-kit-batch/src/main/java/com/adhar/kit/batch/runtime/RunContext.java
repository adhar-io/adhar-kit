package com.adhar.kit.batch.runtime;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Everything a Kubernetes run knows about itself before it starts (design 0025 §5.5), resolved
 * once by {@link RunContextResolver} from the pod's environment, the downward API and one GET each
 * of the owner Job and the pod.
 *
 * @param jobName        the Spring Batch job ({@code ADHAR_BATCH_JOB})
 * @param batchJobName   the {@code BatchJob} it belongs to ({@code ADHAR_BATCH_NAME})
 * @param role           what this pod does, after resolving {@link BatchRole#INDEXED}
 * @param trigger        why the run exists ({@code ADHAR_BATCH_TRIGGER})
 * @param actor          who caused it, stamped by admission policy; empty when unknown
 * @param k8sJobName     the Kubernetes Job owning the pod; empty outside one
 * @param k8sJobUid      that Job's uid; empty when unknown
 * @param podName        this pod
 * @param podUid         this pod's uid; empty when unknown
 * @param nodeName       the node it runs on; empty when unknown
 * @param scheduledTime  the CronJob's nominal time for a scheduled run, never the current time
 * @param zone           the job's time zone ({@code ADHAR_BATCH_TIME_ZONE})
 * @param businessDate   the scheduled time in {@code zone}, as a date
 * @param imageDigest    the running image's digest ({@code sha256:...}); empty when unknown
 * @param attempt        1 for the Job's first pod, 2 after one failed pod, and so on
 * @param partitionIndex the Indexed Job completion index, when there is one
 * @param correlationId  ties the run's logs and events together
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record RunContext(
        String jobName, String batchJobName, BatchRole role, RunTrigger trigger, String actor,
        String k8sJobName, String k8sJobUid, String podName, String podUid, String nodeName,
        Optional<OffsetDateTime> scheduledTime, ZoneId zone, Optional<LocalDate> businessDate,
        String imageDigest, int attempt, OptionalInt partitionIndex, String correlationId) {
}
