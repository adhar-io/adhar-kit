package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.config.BatchProperties;
import org.springframework.core.retry.RetryPolicy;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Spring Framework 7 {@link RetryPolicy} presets for chunk-oriented steps.
 *
 * <p>Every preset backs off exponentially: the first retry after {@code initial-backoff}, each
 * further one after twice the previous delay, capped at {@code max-backoff}, for at most
 * {@code max-attempts} attempts per item (the first one included). The defaults come from
 * {@code adhar.batch.retry.*}: 1 s, 30 s, 3 attempts. No preset retries a
 * {@link NonRetryableBatchException}, nor any failure whose cause chain contains one.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class RetryPresets {

    /**
     * Failures of the database or the network that a later attempt may not meet: Spring's
     * transient data-access exceptions (lock timeouts, deadlocks, query timeouts - but not an
     * optimistic-locking conflict, which repeats), a connection that could not be obtained,
     * JDBC's transient and recoverable SQL exceptions, socket timeouts and refused connections.
     */
    public static final Predicate<Throwable> TRANSIENT_INFRASTRUCTURE = causeChainMatches(throwable ->
            hasType(throwable, "org.springframework.dao.TransientDataAccessException",
                    "org.springframework.dao.RecoverableDataAccessException",
                    "org.springframework.jdbc.CannotGetJdbcConnectionException",
                    "java.sql.SQLTransientException",
                    "java.sql.SQLRecoverableException",
                    "java.net.SocketTimeoutException",
                    "java.net.ConnectException")
                    && !hasType(throwable, "org.springframework.dao.OptimisticLockingFailureException"));

    /**
     * HTTP calls that failed in a way the server or the network may not repeat: 5xx responses
     * and 429 from Spring's {@code RestClient}/{@code RestTemplate} and {@code WebClient}, I/O
     * failures of those clients, and the JDK {@code HttpClient}'s timeouts, socket timeouts and
     * refused connections. Matched by class name, so no HTTP client is a dependency of the kit.
     */
    public static final Predicate<Throwable> TRANSIENT_HTTP = causeChainMatches(throwable ->
            hasType(throwable, "org.springframework.web.client.HttpServerErrorException",
                    "org.springframework.web.client.HttpClientErrorException$TooManyRequests",
                    "org.springframework.web.client.ResourceAccessException",
                    "org.springframework.web.reactive.function.client.WebClientRequestException",
                    "org.springframework.web.reactive.function.client.WebClientResponseException$InternalServerError",
                    "org.springframework.web.reactive.function.client.WebClientResponseException$BadGateway",
                    "org.springframework.web.reactive.function.client.WebClientResponseException$ServiceUnavailable",
                    "org.springframework.web.reactive.function.client.WebClientResponseException$GatewayTimeout",
                    "org.springframework.web.reactive.function.client.WebClientResponseException$TooManyRequests",
                    "java.net.http.HttpTimeoutException",
                    "java.net.SocketTimeoutException",
                    "java.net.ConnectException"));

    private RetryPresets() {
    }

    /**
     * @param settings {@code adhar.batch.retry.*}
     * @return a policy retrying {@link #TRANSIENT_INFRASTRUCTURE} failures
     */
    public static RetryPolicy transientInfrastructure(BatchProperties.Retry settings) {
        return policy(TRANSIENT_INFRASTRUCTURE, settings.getMaxAttempts(), settings.getInitialBackoff(),
                settings.getMaxBackoff());
    }

    /**
     * @param settings {@code adhar.batch.retry.*}
     * @return a policy retrying {@link #TRANSIENT_HTTP} failures
     */
    public static RetryPolicy transientHttp(BatchProperties.Retry settings) {
        return policy(TRANSIENT_HTTP, settings.getMaxAttempts(), settings.getInitialBackoff(),
                settings.getMaxBackoff());
    }

    /**
     * @param settings {@code adhar.batch.retry.*}
     * @param types    the failures to retry (and their subclasses, anywhere in the cause chain)
     * @return a policy retrying those types
     */
    public static RetryPolicy retryOn(BatchProperties.Retry settings, Collection<Class<? extends Throwable>> types) {
        return policy(typesPredicate(types), settings.getMaxAttempts(), settings.getInitialBackoff(),
                settings.getMaxBackoff());
    }

    /**
     * Builds an exponential back-off policy around a classifier.
     *
     * @param retryable      which failures to retry; {@link NonRetryableBatchException} never is
     * @param maxAttempts    attempts per item, the first one included (at least 1)
     * @param initialBackoff delay before the first retry
     * @param maxBackoff     upper bound of a delay
     * @return the policy
     */
    public static RetryPolicy policy(Predicate<Throwable> retryable, int maxAttempts, Duration initialBackoff,
                                     Duration maxBackoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
        }
        return RetryPolicy.builder()
                .maxRetries(maxAttempts - 1L)
                .delay(initialBackoff)
                .multiplier(2.0)
                .maxDelay(maxBackoff)
                .predicate(retryable.and(throwable -> !isNonRetryable(throwable)))
                .build();
    }

    /**
     * @param throwable a failure
     * @return whether it, or a cause of it, is a {@link NonRetryableBatchException}
     */
    public static boolean isNonRetryable(Throwable throwable) {
        return causeChainMatches(t -> t instanceof NonRetryableBatchException).test(throwable);
    }

    static Predicate<Throwable> typesPredicate(Collection<Class<? extends Throwable>> types) {
        List<Class<? extends Throwable>> copy = List.copyOf(types);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("at least one exception type to retry is required");
        }
        return causeChainMatches(throwable -> copy.stream().anyMatch(type -> type.isInstance(throwable)));
    }

    private static Predicate<Throwable> causeChainMatches(Predicate<Throwable> test) {
        return throwable -> {
            Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Throwable current = throwable; current != null && seen.add(current); current = current.getCause()) {
                if (test.test(current)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean hasType(Throwable throwable, String... typeNames) {
        for (Class<?> type = throwable.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (String name : typeNames) {
                if (name.equals(type.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
