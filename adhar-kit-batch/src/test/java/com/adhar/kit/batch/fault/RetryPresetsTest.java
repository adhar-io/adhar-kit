package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.config.BatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RetryPresets}: what each preset retries, and the back-off they all share.
 */
class RetryPresetsTest {

    private final BatchProperties.Retry settings = new BatchProperties.Retry();

    static List<Long> delays(RetryPolicy policy) {
        BackOffExecution execution = policy.getBackOff().start();
        List<Long> delays = new ArrayList<>();
        for (long delay = execution.nextBackOff(); delay != BackOffExecution.STOP; delay = execution.nextBackOff()) {
            delays.add(delay);
        }
        return delays;
    }

    static Stream<Throwable> transientInfrastructure() {
        return Stream.of(
                new QueryTimeoutException("query timeout"),
                new CannotAcquireLockException("lock timeout"),
                new PessimisticLockingFailureException("deadlock"),
                new RecoverableDataAccessException("reconnect"),
                new CannotGetJdbcConnectionException("pool exhausted"),
                new SQLTransientConnectionException("connection"),
                new SQLTimeoutException("statement timeout"),
                new SQLRecoverableException("connection lost"),
                new SocketTimeoutException("read timed out"),
                new ConnectException("refused"));
    }

    static Stream<Throwable> notTransientInfrastructure() {
        return Stream.of(
                new OptimisticLockingFailureException("a conflict repeats"),
                new DuplicateKeyException("constraint"),
                new DataIntegrityViolationException("constraint"),
                new BadSqlGrammarException("task", "select", new SQLException("grammar")),
                new SQLException("generic"),
                new IOException("generic"),
                new IllegalArgumentException("bug"));
    }

    static Stream<Throwable> transientHttp() {
        return Stream.of(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null),
                HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "boom", null, null, null),
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", null, null, null),
                new ResourceAccessException("I/O"),
                new HttpTimeoutException("timed out"),
                new HttpConnectTimeoutException("connect timed out"),
                new SocketTimeoutException("read timed out"),
                new ConnectException("refused"));
    }

    static Stream<Throwable> notTransientHttp() {
        return Stream.of(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad", null, null, null),
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "gone", null, null, null),
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "who", null, null, null),
                new QueryTimeoutException("a database failure is not an HTTP one"),
                new IllegalStateException("bug"));
    }

    @ParameterizedTest
    @MethodSource("transientInfrastructure")
    @DisplayName("the infrastructure preset retries a transient failure, itself or as a cause")
    void infrastructureRetries(Throwable failure) {
        assertThat(RetryPresets.TRANSIENT_INFRASTRUCTURE).accepts(failure);
        assertThat(RetryPresets.TRANSIENT_INFRASTRUCTURE)
                .accepts(new IllegalStateException("outer", new RuntimeException("middle", failure)));
    }

    @ParameterizedTest
    @MethodSource("notTransientInfrastructure")
    @DisplayName("the infrastructure preset does not retry what a later attempt would meet again")
    void infrastructureDoesNotRetry(Throwable failure) {
        assertThat(RetryPresets.TRANSIENT_INFRASTRUCTURE).rejects(failure);
    }

    @ParameterizedTest
    @MethodSource("transientHttp")
    @DisplayName("the HTTP preset retries 5xx, 429 and I/O failures, itself or as a cause")
    void httpRetries(Throwable failure) {
        assertThat(RetryPresets.TRANSIENT_HTTP).accepts(failure);
        assertThat(RetryPresets.TRANSIENT_HTTP).accepts(new IllegalStateException("outer", failure));
    }

    @ParameterizedTest
    @MethodSource("notTransientHttp")
    @DisplayName("the HTTP preset does not retry other 4xx responses or other failures")
    void httpDoesNotRetry(Throwable failure) {
        assertThat(RetryPresets.TRANSIENT_HTTP).rejects(failure);
    }

    @Test
    @DisplayName("a cause cycle ends the matching instead of hanging")
    void causeCycle() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(RetryPresets.TRANSIENT_INFRASTRUCTURE).rejects(first);
        assertThat(RetryPresets.isNonRetryable(first)).isFalse();
    }

    @Test
    @DisplayName("a null failure matches nothing")
    void nullFailure() {
        assertThat(RetryPresets.TRANSIENT_INFRASTRUCTURE).rejects((Throwable) null);
        assertThat(RetryPresets.isNonRetryable(null)).isFalse();
    }

    @Test
    @DisplayName("isNonRetryable finds the exception anywhere in the cause chain")
    void isNonRetryable() {
        var nonRetryable = new NonRetryableBatchException("invalid data");

        assertThat(RetryPresets.isNonRetryable(nonRetryable)).isTrue();
        assertThat(RetryPresets.isNonRetryable(new RuntimeException("a", new RuntimeException("b", nonRetryable))))
                .isTrue();
        assertThat(RetryPresets.isNonRetryable(new RuntimeException("a", new IOException("b")))).isFalse();
    }

    @Test
    @DisplayName("no preset retries a NonRetryableBatchException, whatever else the chain holds")
    void presetsNeverRetryNonRetryable() {
        List<RetryPolicy> policies = List.of(
                RetryPresets.transientInfrastructure(settings),
                RetryPresets.transientHttp(settings),
                RetryPresets.retryOn(settings, List.of(RuntimeException.class)));

        for (RetryPolicy policy : policies) {
            // A retryable failure caused by a non-retryable one, and the other way round.
            assertThat(policy.shouldRetry(new NonRetryableBatchException("invalid"))).isFalse();
            assertThat(policy.shouldRetry(new QueryTimeoutException("db", new NonRetryableBatchException("invalid"))))
                    .isFalse();
            assertThat(policy.shouldRetry(new NonRetryableBatchException("invalid", new ConnectException("refused"))))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("each preset policy retries its own failures only")
    void policiesClassify() {
        var infrastructure = RetryPresets.transientInfrastructure(settings);
        var http = RetryPresets.transientHttp(settings);

        assertThat(infrastructure.shouldRetry(new QueryTimeoutException("db"))).isTrue();
        assertThat(infrastructure.shouldRetry(new ResourceAccessException("http"))).isFalse();
        assertThat(http.shouldRetry(new ResourceAccessException("http"))).isTrue();
        assertThat(http.shouldRetry(new QueryTimeoutException("db"))).isFalse();
    }

    @Test
    @DisplayName("retryOn retries the given types and their subclasses, anywhere in the chain")
    void retryOn() {
        var policy = RetryPresets.retryOn(settings, List.of(IOException.class));

        assertThat(policy.shouldRetry(new IOException("io"))).isTrue();
        assertThat(policy.shouldRetry(new SocketTimeoutException("subclass"))).isTrue();
        assertThat(policy.shouldRetry(new RuntimeException("wrapper", new IOException("cause")))).isTrue();
        assertThat(policy.shouldRetry(new IllegalStateException("other"))).isFalse();
    }

    @Test
    @DisplayName("retryOn refuses an empty list rather than a policy that retries nothing")
    void retryOnNothing() {
        assertThatThrownBy(() -> RetryPresets.retryOn(settings, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one exception type");
    }

    @Test
    @DisplayName("the default back-off is 1 s then 2 s: three attempts, the first one included")
    void defaultBackoff() {
        assertThat(delays(RetryPresets.transientInfrastructure(settings))).containsExactly(1000L, 2000L);
        assertThat(delays(RetryPresets.transientHttp(settings))).containsExactly(1000L, 2000L);
        assertThat(delays(RetryPresets.retryOn(settings, List.of(IOException.class)))).containsExactly(1000L, 2000L);
    }

    @Test
    @DisplayName("the settings drive the back-off: each delay doubles up to max-backoff")
    void backoffFromSettings() {
        settings.setMaxAttempts(6);
        settings.setInitialBackoff(Duration.ofMillis(100));
        settings.setMaxBackoff(Duration.ofMillis(350));

        assertThat(delays(RetryPresets.transientInfrastructure(settings)))
                .containsExactly(100L, 200L, 350L, 350L, 350L);
    }

    @Test
    @DisplayName("one attempt means no retry at all")
    void oneAttempt() {
        var policy = RetryPresets.policy(failure -> true, 1, Duration.ofSeconds(1), Duration.ofSeconds(30));

        assertThat(delays(policy)).isEmpty();
    }

    @Test
    @DisplayName("fewer than one attempt is refused")
    void noAttempts() {
        assertThatThrownBy(() -> RetryPresets.policy(failure -> true, 0, Duration.ofSeconds(1), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts must be at least 1, was 0");
    }
}
