package com.adhar.kit.dapr.outbox;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Index of pending {@link OutboxEvent} ids, persisted under a single well-known key so the relay
 * loop can discover un-published events without a query-capable state store.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class OutboxIndex {

    /** Ids of events still awaiting relay (insertion-ordered). */
    private Set<String> pendingIds = new LinkedHashSet<>();
}
