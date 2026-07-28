package com.adhar.kit.docs.diff;

import java.util.List;

/**
 * The structured result of comparing two OpenAPI specifications.
 *
 * <p>Exposes convenience accessors for CI gates that need to decide whether a spec change
 * is safe to publish (see {@link #hasBreakingChanges()}).</p>
 *
 * @param changes every detected change, in discovery order
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public record SpecDiffReport(List<Change> changes) {

    public SpecDiffReport {
        changes = List.copyOf(changes);
    }

    /**
     * @return only the breaking changes
     */
    public List<Change> breakingChanges() {
        return changes.stream().filter(Change::isBreaking).toList();
    }

    /**
     * @return only the non-breaking changes
     */
    public List<Change> nonBreakingChanges() {
        return changes.stream().filter(change -> !change.isBreaking()).toList();
    }

    /**
     * @return {@code true} if at least one breaking change was detected
     */
    public boolean hasBreakingChanges() {
        return changes.stream().anyMatch(Change::isBreaking);
    }

    /**
     * @return the number of breaking changes
     */
    public int breakingCount() {
        return (int) changes.stream().filter(Change::isBreaking).count();
    }

    /**
     * @return the number of non-breaking changes
     */
    public int nonBreakingCount() {
        return changes.size() - breakingCount();
    }

    /**
     * @return {@code true} if the two specs were equivalent for the compared elements
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * @return a one-line human-readable summary
     */
    public String summary() {
        return String.format("%d change(s): %d breaking, %d non-breaking",
                changes.size(), breakingCount(), nonBreakingCount());
    }
}
