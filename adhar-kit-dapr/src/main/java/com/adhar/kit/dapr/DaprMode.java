package com.adhar.kit.dapr;

import java.util.Locale;

/**
 * How the kit reaches the Dapr building blocks ({@code adhar.dapr.mode} / {@code ADHAR_DAPR_MODE}).
 *
 * <p>The same three values exist in every adhar-kit language, so a service behaves the same
 * whichever kit it is written with.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public enum DaprMode {

    /**
     * Sidecar when {@code DAPR_HTTP_PORT} or {@code DAPR_HTTP_ENDPOINT} is set (the Dapr injector
     * sets both on every annotated pod), otherwise memory. The default.
     */
    AUTO,

    /** Always talk to a sidecar; calls fail when there is none. */
    SIDECAR,

    /**
     * An in-process implementation of state, cache, pub/sub (local fan-out), lock, configuration
     * and secrets, so a service runs and tests with no Dapr at all. Everything else (invocation,
     * bindings, jobs, workflows, actors, crypto) needs a sidecar and fails with a
     * {@link DaprError} naming the mode.
     */
    MEMORY;

    /**
     * Parses a mode name, case-insensitively; blank means {@link #AUTO}.
     *
     * @param value the configured value
     * @return the mode
     * @throws IllegalArgumentException for an unknown value, so a typo is not silently "auto"
     */
    public static DaprMode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown Dapr mode '" + value + "'; expected auto, sidecar or memory", e);
        }
    }
}
