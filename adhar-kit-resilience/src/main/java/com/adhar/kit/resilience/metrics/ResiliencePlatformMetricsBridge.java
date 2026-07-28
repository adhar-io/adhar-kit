package com.adhar.kit.resilience.metrics;

import com.adhar.kit.metrics.auto.PlatformMetrics;
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
 * Bridges Resilience4j events to the metrics module's {@link PlatformMetrics}, so that
 * resilience activity is exported through the shared Adhar metric naming convention
 * ({@code adhar.resilience.*}) alongside every other module.
 *
 * <p>Event translation:</p>
 * <ul>
 *   <li>circuit breaker state transitions -&gt; {@link PlatformMetrics#recordCircuitBreakerState(String, String)}</li>
 *   <li>retry attempts / successes -&gt; {@link PlatformMetrics#recordRetryAttempt(String, int, boolean)}</li>
 *   <li>rate limiter rejections -&gt; {@link PlatformMetrics#recordRateLimitReject(String)}</li>
 *   <li>bulkhead rejections -&gt; {@link PlatformMetrics#recordOperationLatency(String, String, long, boolean)}</li>
 *   <li>time limiter timeouts -&gt; {@link PlatformMetrics#recordOperationLatency(String, String, long, boolean)}</li>
 * </ul>
 *
 * <p>This type references {@link PlatformMetrics} directly; the dependency on
 * {@code adhar-kit-metrics} is declared {@code optional}, so the module still compiles
 * without it, and the owning bean is gated on the class/bean being present at runtime.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ResiliencePlatformMetricsBridge {

    private static final String MODULE = "resilience";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final PlatformMetrics platformMetrics;

    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * Attaches metric-recording consumers to all current registry entries and to future
     * ones via registry entry-added events. Idempotent: subsequent calls are no-ops.
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

        log.info("Adhar resilience -> PlatformMetrics bridge registered");
    }

    private void attach(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                platformMetrics.recordCircuitBreakerState(circuitBreaker.getName(),
                        event.getStateTransition().getToState().name()));
    }

    private void attach(Retry retry) {
        retry.getEventPublisher().onRetry(event ->
                platformMetrics.recordRetryAttempt(retry.getName(), event.getNumberOfRetryAttempts(), false));
        retry.getEventPublisher().onSuccess(event -> {
            if (event.getNumberOfRetryAttempts() > 0) {
                platformMetrics.recordRetryAttempt(retry.getName(), event.getNumberOfRetryAttempts(), true);
            }
        });
    }

    private void attach(RateLimiter rateLimiter) {
        rateLimiter.getEventPublisher().onFailure(event ->
                platformMetrics.recordRateLimitReject(rateLimiter.getName()));
    }

    private void attach(Bulkhead bulkhead) {
        bulkhead.getEventPublisher().onCallRejected(event ->
                platformMetrics.recordOperationLatency(MODULE, "bulkhead." + bulkhead.getName() + ".rejected",
                        0, false));
    }

    private void attach(TimeLimiter timeLimiter) {
        timeLimiter.getEventPublisher().onTimeout(event ->
                platformMetrics.recordOperationLatency(MODULE, "timeLimiter." + timeLimiter.getName() + ".timeout",
                        0, false));
    }
}
