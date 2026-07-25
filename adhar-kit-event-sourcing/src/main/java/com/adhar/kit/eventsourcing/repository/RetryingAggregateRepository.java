package com.adhar.kit.eventsourcing.repository;

import com.adhar.kit.eventsourcing.core.AggregateRoot;
import com.adhar.kit.eventsourcing.store.ConcurrencyException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Wraps an {@link AggregateRepository} to automatically retry commands that fail with an
 * optimistic {@link ConcurrencyException}.
 *
 * <p>On a concurrency conflict the aggregate is reloaded (picking up the latest events) and
 * the command is reapplied, up to a configured number of attempts.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class RetryingAggregateRepository {

    private final AggregateRepository delegate;
    private final int maxAttempts;

    /**
     * @param delegate    the underlying repository used to load and save aggregates
     * @param maxAttempts maximum number of attempts (must be at least {@code 1})
     */
    public RetryingAggregateRepository(AggregateRepository delegate, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Loads the aggregate, applies the given command, and saves it. If saving fails with a
     * {@link ConcurrencyException}, the aggregate is reloaded and the command reapplied,
     * up to {@code maxAttempts} times.
     *
     * @param aggregateId the aggregate identifier
     * @param type        the aggregate class
     * @param command     the command to apply to the loaded aggregate
     * @param <T>         aggregate type
     * @return the saved aggregate
     * @throws ConcurrencyException if all attempts are exhausted
     */
    public <T extends AggregateRoot> T executeWithRetry(String aggregateId, Class<T> type, Consumer<T> command) {
        ConcurrencyException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            T aggregate = delegate.load(aggregateId, type);
            try {
                command.accept(aggregate);
                delegate.save(aggregate);
                return aggregate;
            } catch (ConcurrencyException ex) {
                lastFailure = ex;
                log.warn("Concurrency conflict saving aggregate '{}' (attempt {}/{}): {}",
                        aggregateId, attempt, maxAttempts, ex.getMessage());
            }
        }

        throw lastFailure;
    }
}
