package com.adhar.kit.maven.openapi;

/**
 * One problem {@link OpenApiLinter} found in a spec.
 *
 * @param severity whether the finding fails the build
 * @param rule     the rule that produced it, e.g. {@code problem-json}
 * @param location where in the document, e.g. {@code GET /api/v1/pets responses.404}
 * @param message  what is wrong and how to fix it
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record LintFinding(Severity severity, String rule, String location, String message) {

    /** How serious a finding is. */
    public enum Severity {
        /** Breaks the contract; fails the build. */
        ERROR,
        /** A convention the spec should follow; fails the build only when configured to. */
        WARNING
    }

    /** Whether this finding is an {@link Severity#ERROR}. */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return location + ": " + message + " [" + rule + "]";
    }
}
