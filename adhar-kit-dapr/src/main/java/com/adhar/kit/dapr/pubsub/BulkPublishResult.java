package com.adhar.kit.dapr.pubsub;

import java.util.List;

/**
 * Outcome of a native bulk publish: the entries the broker rejected. Dapr accepts or rejects each
 * entry on its own, so a partial failure is a normal result, not an exception.
 *
 * @param published   how many entries were accepted
 * @param failed      the rejected entries
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record BulkPublishResult(int published, List<FailedEntry> failed) {

    public BulkPublishResult {
        failed = failed == null ? List.of() : List.copyOf(failed);
    }

    /** {@code true} when every entry was accepted. */
    public boolean allSucceeded() {
        return failed.isEmpty();
    }

    /**
     * A rejected entry.
     *
     * @param entryId the entry id (its index in the published list, as a string)
     * @param event   the payload that was rejected
     * @param error   the broker's error message
     */
    public record FailedEntry(String entryId, Object event, String error) {
    }
}
