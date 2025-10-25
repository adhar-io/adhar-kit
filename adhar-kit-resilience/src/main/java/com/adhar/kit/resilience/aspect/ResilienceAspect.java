package com.adhar.kit.resilience.aspect;

import com.adhar.kit.resilience.annotation.*;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Aspect to handle resilience annotations.
 * Applies circuit breaker, retry, rate limiter, bulkhead, and time limiter patterns.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ResilienceAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    @Around("@annotation(circuitBreaker)")
    public Object applyCircuitBreaker(ProceedingJoinPoint joinPoint, CircuitBreaker circuitBreaker) throws Throwable {
        String name = circuitBreaker.name();
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);

        log.debug("Applying circuit breaker '{}' to method '{}'", name, joinPoint.getSignature().getName());

        try {
            return cb.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            if (!circuitBreaker.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, circuitBreaker.fallbackMethod(), e);
            }
            throw e;
        }
    }

    @Around("@annotation(retry)")
    public Object applyRetry(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        String name = retry.name();
        io.github.resilience4j.retry.Retry r = retryRegistry.retry(name);

        log.debug("Applying retry '{}' to method '{}'", name, joinPoint.getSignature().getName());

        try {
            return r.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            if (!retry.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, retry.fallbackMethod(), e);
            }
            throw e;
        }
    }

    @Around("@annotation(rateLimit)")
    public Object applyRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String name = rateLimit.name();
        io.github.resilience4j.ratelimiter.RateLimiter rl = rateLimiterRegistry.rateLimiter(name);

        log.debug("Applying rate limiter '{}' to method '{}'", name, joinPoint.getSignature().getName());

        try {
            return rl.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            if (!rateLimit.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, rateLimit.fallbackMethod(), e);
            }
            throw e;
        }
    }

    @Around("@annotation(bulkhead)")
    public Object applyBulkhead(ProceedingJoinPoint joinPoint, Bulkhead bulkhead) throws Throwable {
        String name = bulkhead.name();
        io.github.resilience4j.bulkhead.Bulkhead bh = bulkheadRegistry.bulkhead(name);

        log.debug("Applying bulkhead '{}' to method '{}'", name, joinPoint.getSignature().getName());

        try {
            return bh.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            if (!bulkhead.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, bulkhead.fallbackMethod(), e);
            }
            throw e;
        }
    }

    @Around("@annotation(timeLimiter)")
    public Object applyTimeLimiter(ProceedingJoinPoint joinPoint, TimeLimiter timeLimiter) throws Throwable {
        String name = timeLimiter.name();
        io.github.resilience4j.timelimiter.TimeLimiter tl = timeLimiterRegistry.timeLimiter(name);

        log.debug("Applying time limiter '{}' to method '{}'", name, joinPoint.getSignature().getName());

        try {
            Supplier<CompletableFuture<Object>> futureSupplier = () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });

            return tl.executeFutureSupplier(futureSupplier);
        } catch (Exception e) {
            if (!timeLimiter.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, timeLimiter.fallbackMethod(), e);
            }
            throw e;
        }
    }

    private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName, Exception exception) {
        try {
            Object target = joinPoint.getTarget();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            // Try to find fallback method with exception parameter
            try {
                Method fallbackMethod = target.getClass().getDeclaredMethod(
                    fallbackMethodName,
                    exception.getClass()
                );
                fallbackMethod.setAccessible(true);
                return fallbackMethod.invoke(target, exception);
            } catch (NoSuchMethodException e) {
                // Try without exception parameter
                Method fallbackMethod = target.getClass().getDeclaredMethod(fallbackMethodName);
                fallbackMethod.setAccessible(true);
                return fallbackMethod.invoke(target);
            }
        } catch (Exception e) {
            log.error("Failed to invoke fallback method: {}", fallbackMethodName, e);
            throw new RuntimeException("Fallback method execution failed", e);
        }
    }
}

