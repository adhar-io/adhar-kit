package com.adhar.kit.dapr.invoke;

/**
 * HTTP verb for service invocation.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public enum HttpVerb {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS;

    /** {@code true} for verbs that may be retried without side effects being repeated. */
    public boolean isIdempotent() {
        return this != POST && this != PATCH;
    }

    /**
     * Parses a verb case-insensitively; unknown verbs are rejected rather than silently sent as POST.
     *
     * @param value the verb
     * @return the verb
     */
    public static HttpVerb parse(String value) {
        if (value == null || value.isBlank()) {
            return POST;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP verb for Dapr invocation: " + value, e);
        }
    }
}
