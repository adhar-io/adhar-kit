package com.adhar.kit.docs.diff;

/**
 * A single difference detected between two OpenAPI specifications.
 *
 * @param type        the kind of change
 * @param severity    whether the change is backward-incompatible ({@link Severity#BREAKING})
 *                    or backward-compatible ({@link Severity#NON_BREAKING})
 * @param location    a dotted path pointing at the affected element (e.g.
 *                    {@code paths./orders.get.parameters.query:status})
 * @param description a human-readable explanation of the change
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public record Change(Type type, Severity severity, String location, String description) {

    /**
     * The backward-compatibility classification of a change.
     */
    public enum Severity {
        /** A change that can break existing API consumers. */
        BREAKING,
        /** A change that is backward-compatible for existing consumers. */
        NON_BREAKING
    }

    /**
     * The specific kind of change detected.
     */
    public enum Type {
        PATH_ADDED,
        PATH_REMOVED,
        OPERATION_ADDED,
        OPERATION_REMOVED,
        PARAMETER_ADDED,
        PARAMETER_REMOVED,
        PARAMETER_NOW_REQUIRED,
        SCHEMA_REMOVED,
        TYPE_CHANGED,
        PROPERTY_NOW_REQUIRED,
        ENUM_VALUE_ADDED,
        ENUM_VALUE_REMOVED
    }

    /**
     * @return {@code true} if this change is classified as breaking
     */
    public boolean isBreaking() {
        return severity == Severity.BREAKING;
    }
}
