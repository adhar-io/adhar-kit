package com.adhar.kit.dapr.jobs;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * A scheduled job as the Dapr scheduler reports it.
 *
 * @param name     the job name
 * @param schedule the schedule expression, or {@code null}
 * @param dueTime  the due time, or {@code null}
 * @param repeats  the repeat count, or {@code null}
 * @param ttl      the expiry, or {@code null}
 * @param data     the payload bytes, or {@code null}
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record JobDetails(String name, String schedule, Instant dueTime, Integer repeats, Instant ttl,
                         byte[] data) {

    /** The payload as UTF-8 text, or {@code null}. */
    public String dataAsString() {
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }
}
