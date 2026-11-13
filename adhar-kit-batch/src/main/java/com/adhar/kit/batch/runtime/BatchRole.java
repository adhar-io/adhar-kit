package com.adhar.kit.batch.runtime;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * What a run pod does in its job ({@code ADHAR_BATCH_ROLE}, {@code adhar.batch.role}).
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public enum BatchRole {
    /** The whole job in one process (the default). */
    SINGLE,
    /** Runs the job and hands partitions to workers (remote partitioning, Phase 2). */
    MANAGER,
    /** Runs the partitions a manager hands out (remote partitioning, Phase 2). */
    WORKER,
    /** An Indexed Job pod: index 0 is the manager, every other index a worker. */
    INDEXED;

    /**
     * @param index the pod's completion index, empty when it has none
     * @return the role this pod plays: {@link #INDEXED} becomes {@link #MANAGER} at index 0 (or
     *         without an index) and {@link #WORKER} otherwise
     */
    public BatchRole effective(OptionalInt index) {
        if (this != INDEXED) {
            return this;
        }
        return index.orElse(0) == 0 ? MANAGER : WORKER;
    }

    /**
     * @param value {@code single}, {@code manager}, {@code worker} or {@code indexed}, any case;
     *              blank means {@link #SINGLE}
     * @return the role
     * @throws IllegalArgumentException for any other value
     */
    public static BatchRole parse(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown batch role '" + value
                    + "': expected single, manager, worker or indexed", ex);
        }
    }

    /**
     * @return the lower-case name used in environment variables and labels
     */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
