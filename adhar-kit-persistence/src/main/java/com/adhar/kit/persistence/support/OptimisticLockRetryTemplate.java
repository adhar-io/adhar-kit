package com.adhar.kit.persistence.support;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retries an operation that may fail with an optimistic-locking conflict
 * ({@link ObjectOptimisticLockingFailureException} or {@link OptimisticLockException}), applying
 * exponential backoff between attempts.
 *
 * <p><b>The supplier must re-read state.</b> An optimistic-lock failure means the entity was
 * modified concurrently since it was loaded; simply re-submitting the same (now stale) entity
 * instance will fail again in exactly the same way. The {@link Supplier} passed to
 * {@link #execute(Supplier)} must therefore, on every invocation, re-fetch the entity (or
 * otherwise recompute its input) from the current database state, apply the intended change, and
 * save -- not close over a single already-loaded entity from before the first attempt. For
 * example:</p>
 *
 * <pre>{@code
 * OptimisticLockRetryTemplate retryTemplate = OptimisticLockRetryTemplate.withDefaults();
 *
 * Account updated = retryTemplate.execute(() -> {
 *     Account account = accountRepository.findById(id).orElseThrow(); // re-read every attempt
 *     account.setBalance(account.getBalance().add(amount));
 *     return accountRepository.save(account);
 * });
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe; a single instance may be reused/shared.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public final class OptimisticLockRetryTemplate {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockRetryTemplate.class);

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double backoffMultiplier;

    /**
     * @param maxAttempts       maximum number of attempts (including the first), must be at least 1
     * @param initialBackoff    delay before the first retry
     * @param backoffMultiplier multiplier applied to the backoff after each failed attempt
     */
    public OptimisticLockRetryTemplate(int maxAttempts, Duration initialBackoff, double backoffMultiplier) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * Returns a template configured with sensible defaults: 3 attempts, 50ms initial backoff,
     * 2x multiplier.
     */
    public static OptimisticLockRetryTemplate withDefaults() {
        return new OptimisticLockRetryTemplate(3, Duration.ofMillis(50), 2.0);
    }

    /**
     * Executes {@code supplier}, retrying on {@link ObjectOptimisticLockingFailureException} or
     * {@link OptimisticLockException} up to {@code maxAttempts} times with exponential backoff.
     * If every attempt fails, the last caught exception is rethrown.
     *
     * @param supplier operation to execute; must re-read entity state on every invocation
     * @param <T>      result type
     * @return the result of the first successful invocation
     */
    public <T> T execute(Supplier<T> supplier) {
        long backoffMs = initialBackoff.toMillis();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
                lastFailure = ex;
                if (attempt >= maxAttempts) {
                    log.warn("Optimistic lock retry exhausted after {} attempts", attempt, ex);
                    break;
                }
                log.debug("Optimistic lock conflict on attempt {}/{}; retrying in {}ms",
                        attempt, maxAttempts, backoffMs);
                sleep(backoffMs);
                backoffMs = (long) (backoffMs * backoffMultiplier);
            }
        }
        throw lastFailure;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry after an optimistic lock conflict", ex);
        }
    }
}
