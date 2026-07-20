package com.adhar.kit.resilience.event;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers Resilience4j event consumers on every existing and future instance of the
 * managed registries. Events are logged as structured warnings and counted in a
 * {@link ResilienceEventRecorder}.
 *
 * <p>Covered events:</p>
 * <ul>
 *   <li>Circuit breaker state transitions</li>
 *   <li>Retry attempts</li>
 *   <li>Rate limiter rejections</li>
 *   <li>Bulkhead call rejections</li>
 *   <li>Time limiter timeouts</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ResilienceEventListeners {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ResilienceEventRecorder recorder;

    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * Attaches event consumers to all current registry entries and subscribes to
     * registry entry-added events so future instances are covered as well.
     * Idempotent: subsequent calls are no-ops.
     */
    public void register() {
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::attach);
        circuitBreakerRegistry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));

        retryRegistry.getAllRetries().forEach(this::attach);
        retryRegistry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));

        rateLimiterRegistry.getAllRateLimiters().forEach(this::attach);
        rateLimiterRegistry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));

        bulkheadRegistry.getAllBulkheads().forEach(this::attach);
        bulkheadRegistry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));

        timeLimiterRegistry.getAllTimeLimiters().forEach(this::attach);
        timeLimiterRegistry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));

        log.info("Adhar resilience event listeners registered");
    }

    private void attach(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            var transition = event.getStateTransition();
            recorder.recordCircuitBreakerTransition(circuitBreaker.getName(),
                    transition.getFromState().name(), transition.getToState().name());
            log.warn("resilience.event type=circuit-breaker.state-transition name={} from={} to={}",
                    circuitBreaker.getName(), transition.getFromState(), transition.getToState());
        });
    }

    private void attach(Retry retry) {
        retry.getEventPublisher().onRetry(event -> {
            recorder.recordRetryAttempt(retry.getName());
            log.warn("resilience.event type=retry.attempt name={} attempt={} lastException={}",
                    retry.getName(), event.getNumberOfRetryAttempts(),
                    event.getLastThrowable() == null ? "n/a" : event.getLastThrowable().toString());
        });
    }

    private void attach(RateLimiter rateLimiter) {
        rateLimiter.getEventPublisher().onFailure(event -> {
            recorder.recordRateLimiterRejection(rateLimiter.getName());
            log.warn("resilience.event type=rate-limiter.rejected name={} permits={}",
                    rateLimiter.getName(), event.getNumberOfPermits());
        });
    }

    private void attach(Bulkhead bulkhead) {
        bulkhead.getEventPublisher().onCallRejected(event -> {
            recorder.recordBulkheadRejection(bulkhead.getName());
            log.warn("resilience.event type=bulkhead.rejected name={}", bulkhead.getName());
        });
    }

    private void attach(TimeLimiter timeLimiter) {
        timeLimiter.getEventPublisher().onTimeout(event -> {
            recorder.recordTimeLimiterTimeout(timeLimiter.getName());
            log.warn("resilience.event type=time-limiter.timeout name={}", timeLimiter.getName());
        });
    }
}
