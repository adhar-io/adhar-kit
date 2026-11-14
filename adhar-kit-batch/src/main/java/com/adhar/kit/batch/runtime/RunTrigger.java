package com.adhar.kit.batch.runtime;

import java.util.Locale;

/**
 * Why a run exists ({@code ADHAR_BATCH_TRIGGER}): the CronJob's schedule, a person, a restart, a
 * backfill, and so on. It is recorded as the non-identifying parameter {@code adhar.trigger} and in
 * the termination summary; it never changes the run's identity.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public enum RunTrigger {
    /** The CronJob fired: the business date comes from the scheduled time. */
    SCHEDULE,
    /** Started by a person (Console, CLI). */
    MANUAL,
    /** A restart of an earlier run ({@code <run>-r<n>}). */
    RESTART,
    /** One date of a backfill. */
    BACKFILL,
    /** Started because the runs it depends on completed (Phase 3). */
    DEPENDENCY,
    /** A workflow step (Phase 3). */
    WORKFLOW,
    /** A promotion's smoke run (Phase 3). */
    SMOKE,
    /** An operations command (Phase 3). */
    OPS,
    /** The embedded scheduler of a service, not a Kubernetes run. */
    EMBEDDED;

    /**
     * @param value the trigger's name, any case; blank means {@link #MANUAL}
     * @return the trigger
     * @throws IllegalArgumentException for an unknown name
     */
    public static RunTrigger parse(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown run trigger '" + value + "'", ex);
        }
    }

    /**
     * @return the lower-case name used in parameters, labels and the termination summary
     */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
