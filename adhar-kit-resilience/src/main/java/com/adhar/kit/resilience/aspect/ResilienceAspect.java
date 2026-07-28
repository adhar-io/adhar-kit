package com.adhar.kit.resilience.aspect;

import com.adhar.kit.resilience.annotation.Bulkhead;
import com.adhar.kit.resilience.annotation.CircuitBreaker;
import com.adhar.kit.resilience.annotation.RateLimit;
import com.adhar.kit.resilience.annotation.Retry;
import com.adhar.kit.resilience.annotation.TimeLimiter;
import com.adhar.kit.resilience.cache.FallbackCache;
import com.adhar.kit.resilience.chaos.ChaosPolicy;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Unified aspect that handles all Adhar resilience annotations.
 *
 * <p>A single around advice inspects the intercepted method (and its declaring class)
 * for every resilience annotation present and composes the corresponding Resilience4j
 * decorators in the recommended default order
 * (outermost first): {@code Retry -> CircuitBreaker -> RateLimiter -> TimeLimiter -> Bulkhead}.
 * The method body is therefore executed exactly once per attempt, regardless of how many
 * annotations are stacked on it.</p>
 *
 * <p>Annotation attributes are honored: when a named instance is created for the first
 * time, a per-name configuration is derived from the annotation's explicitly set
 * attributes (a {@code -1} sentinel means "inherit the registry default"). Instances are
 * cached per name, so repeated calls reuse the same instance. Named instances that were
 * pre-configured through {@code adhar.resilience.*} properties take precedence over
 * annotation attributes.</p>
 *
 * <p>Methods returning {@link CompletionStage}/{@link CompletableFuture} are decorated
 * with the asynchronous decorator variants (including a non-blocking time limiter);
 * all other methods use the synchronous path.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class ResilienceAspect {

    private static final String RESILIENCE_POINTCUT =
            "@annotation(com.adhar.kit.resilience.annotation.CircuitBreaker)"
                    + " || @annotation(com.adhar.kit.resilience.annotation.Retry)"
                    + " || @annotation(com.adhar.kit.resilience.annotation.RateLimit)"
                    + " || @annotation(com.adhar.kit.resilience.annotation.Bulkhead)"
                    + " || @annotation(com.adhar.kit.resilience.annotation.TimeLimiter)"
                    + " || @within(com.adhar.kit.resilience.annotation.CircuitBreaker)"
                    + " || @within(com.adhar.kit.resilience.annotation.Retry)"
                    + " || @within(com.adhar.kit.resilience.annotation.RateLimit)"
                    + " || @within(com.adhar.kit.resilience.annotation.Bulkhead)"
                    + " || @within(com.adhar.kit.resilience.annotation.TimeLimiter)";

    /** Shared daemon scheduler used for async time limiting and async retry backoff. */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "adhar-resilience-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    private final ConcurrentMap<String, io.github.resilience4j.circuitbreaker.CircuitBreaker> circuitBreakerCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, io.github.resilience4j.retry.Retry> retryCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, io.github.resilience4j.ratelimiter.RateLimiter> rateLimiterCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, io.github.resilience4j.bulkhead.Bulkhead> bulkheadCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, io.github.resilience4j.timelimiter.TimeLimiter> timeLimiterCache =
            new ConcurrentHashMap<>();

    /** Optional "last known good" result cache; {@code null} when the fallback cache is not configured. */
    private final FallbackCache fallbackCache;
    /** When {@code true}, the fallback cache applies to every annotated method regardless of annotation attributes. */
    private final boolean globalFallbackCacheEnabled;
    /** Optional chaos policy applied at the innermost point of the decorator chain; {@code null} when disabled. */
    private final ChaosPolicy chaosPolicy;

    /**
     * Backward-compatible constructor without fallback cache or chaos support.
     *
     * @param circuitBreakerRegistry circuit breaker registry
     * @param retryRegistry retry registry
     * @param rateLimiterRegistry rate limiter registry
     * @param bulkheadRegistry bulkhead registry
     * @param timeLimiterRegistry time limiter registry
     */
    public ResilienceAspect(CircuitBreakerRegistry circuitBreakerRegistry,
                            RetryRegistry retryRegistry,
                            RateLimiterRegistry rateLimiterRegistry,
                            BulkheadRegistry bulkheadRegistry,
                            TimeLimiterRegistry timeLimiterRegistry) {
        this(circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, bulkheadRegistry,
                timeLimiterRegistry, null, false, null);
    }

    /**
     * Full constructor.
     *
     * @param circuitBreakerRegistry circuit breaker registry
     * @param retryRegistry retry registry
     * @param rateLimiterRegistry rate limiter registry
     * @param bulkheadRegistry bulkhead registry
     * @param timeLimiterRegistry time limiter registry
     * @param fallbackCache optional last-known-good result cache; may be {@code null}
     * @param globalFallbackCacheEnabled whether the cache applies to all annotated methods
     * @param chaosPolicy optional chaos policy; may be {@code null}
     */
    public ResilienceAspect(CircuitBreakerRegistry circuitBreakerRegistry,
                            RetryRegistry retryRegistry,
                            RateLimiterRegistry rateLimiterRegistry,
                            BulkheadRegistry bulkheadRegistry,
                            TimeLimiterRegistry timeLimiterRegistry,
                            FallbackCache fallbackCache,
                            boolean globalFallbackCacheEnabled,
                            ChaosPolicy chaosPolicy) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.fallbackCache = fallbackCache;
        this.globalFallbackCacheEnabled = globalFallbackCacheEnabled;
        this.chaosPolicy = chaosPolicy;
    }

    // ------------------------------------------------------------------
    // Unified advice
    // ------------------------------------------------------------------

    /**
     * Single around advice for every resilience annotation. Collects all resilience
     * annotations present on the method (or its declaring class), composes the
     * decorators in the recommended order and executes the join point once.
     *
     * @param joinPoint the intercepted join point
     * @return the (possibly decorated) invocation result
     * @throws Throwable original failure when no fallback is configured
     */
    @Around(RESILIENCE_POINTCUT)
    public Object applyResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        ResilienceAnnotations annotations = ResilienceAnnotations.of(method, joinPoint.getTarget());
        if (annotations.isEmpty()) {
            return joinPoint.proceed();
        }
        return execute(joinPoint, method, annotations);
    }

    // ------------------------------------------------------------------
    // Backward-compatible single-pattern entry points (no longer advices;
    // the unified advice above handles weaving)
    // ------------------------------------------------------------------

    /**
     * Applies only the given circuit breaker annotation to the join point.
     *
     * @deprecated since 0.1.0 in favor of the unified {@link #applyResilience(ProceedingJoinPoint)} advice.
     */
    @Deprecated
    public Object applyCircuitBreaker(ProceedingJoinPoint joinPoint, CircuitBreaker circuitBreaker) throws Throwable {
        return execute(joinPoint, resolveMethod(joinPoint),
                new ResilienceAnnotations(circuitBreaker, null, null, null, null));
    }

    /**
     * Applies only the given retry annotation to the join point.
     *
     * @deprecated since 0.1.0 in favor of the unified {@link #applyResilience(ProceedingJoinPoint)} advice.
     */
    @Deprecated
    public Object applyRetry(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        return execute(joinPoint, resolveMethod(joinPoint),
                new ResilienceAnnotations(null, retry, null, null, null));
    }

    /**
     * Applies only the given rate limiter annotation to the join point.
     *
     * @deprecated since 0.1.0 in favor of the unified {@link #applyResilience(ProceedingJoinPoint)} advice.
     */
    @Deprecated
    public Object applyRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        return execute(joinPoint, resolveMethod(joinPoint),
                new ResilienceAnnotations(null, null, rateLimit, null, null));
    }

    /**
     * Applies only the given bulkhead annotation to the join point.
     *
     * @deprecated since 0.1.0 in favor of the unified {@link #applyResilience(ProceedingJoinPoint)} advice.
     */
    @Deprecated
    public Object applyBulkhead(ProceedingJoinPoint joinPoint, Bulkhead bulkhead) throws Throwable {
        return execute(joinPoint, resolveMethod(joinPoint),
                new ResilienceAnnotations(null, null, null, bulkhead, null));
    }

    /**
     * Applies only the given time limiter annotation to the join point.
     *
     * @deprecated since 0.1.0 in favor of the unified {@link #applyResilience(ProceedingJoinPoint)} advice.
     */
    @Deprecated
    public Object applyTimeLimiter(ProceedingJoinPoint joinPoint, TimeLimiter timeLimiter) throws Throwable {
        return execute(joinPoint, resolveMethod(joinPoint),
                new ResilienceAnnotations(null, null, null, null, timeLimiter));
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    private Object execute(ProceedingJoinPoint joinPoint, Method method, ResilienceAnnotations annotations)
            throws Throwable {
        boolean async = method != null && CompletionStage.class.isAssignableFrom(method.getReturnType());
        String fallbackMethod = annotations.fallbackMethod();
        boolean cacheEnabled = fallbackCache != null && annotations.fallbackCacheEnabled(globalFallbackCacheEnabled);
        String cacheKey = cacheEnabled ? buildCacheKey(method, joinPoint) : null;

        if (log.isDebugEnabled()) {
            log.debug("Applying resilience decorators {} to method '{}' (async={}, fallbackCache={})",
                    annotations.describe(), joinPoint.getSignature().getName(), async, cacheEnabled);
        }

        if (async) {
            return executeAsyncWithFallback(joinPoint, annotations, fallbackMethod, cacheEnabled, cacheKey);
        }

        try {
            Object result = executeSync(joinPoint, annotations);
            if (cacheEnabled) {
                fallbackCache.put(cacheKey, result);
            }
            return result;
        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            if (!fallbackMethod.isEmpty()) {
                return adaptFallbackResult(invokeFallback(joinPoint, fallbackMethod, cause), false);
            }
            if (cacheEnabled) {
                Optional<Object> cached = fallbackCache.get(cacheKey);
                if (cached.isPresent()) {
                    log.warn("resilience.fallback-cache serving last known good result for '{}' after failure: {}",
                            joinPoint.getSignature().getName(), cause.toString());
                    return cached.get();
                }
            }
            throw cause;
        }
    }

    /**
     * Asynchronous execution with fallback-cache support layered on top of the async
     * decorator chain (which already applies any {@code fallbackMethod}). Successful
     * results are cached; when no fallback method is configured and the future fails, the
     * last known good cached result is served instead.
     */
    @SuppressWarnings("unchecked")
    private Object executeAsyncWithFallback(ProceedingJoinPoint joinPoint, ResilienceAnnotations annotations,
                                            String fallbackMethod, boolean cacheEnabled, String cacheKey) {
        CompletableFuture<Object> future =
                (CompletableFuture<Object>) executeAsync(joinPoint, annotations, fallbackMethod);
        if (!cacheEnabled) {
            return future;
        }
        if (!fallbackMethod.isEmpty()) {
            // A fallback method takes precedence; still cache successful results for later use.
            return future.whenComplete((value, throwable) -> {
                if (throwable == null) {
                    fallbackCache.put(cacheKey, value);
                }
            });
        }
        String methodName = joinPoint.getSignature().getName();
        return future
                .thenApply(value -> {
                    fallbackCache.put(cacheKey, value);
                    return value;
                })
                .exceptionallyCompose(throwable -> {
                    Optional<Object> cached = fallbackCache.get(cacheKey);
                    if (cached.isPresent()) {
                        log.warn("resilience.fallback-cache serving last known good result for '{}' after failure: {}",
                                methodName, unwrap(throwable).toString());
                        return CompletableFuture.completedFuture(cached.get());
                    }
                    return CompletableFuture.failedFuture(unwrap(throwable));
                });
    }

    private static String buildCacheKey(Method method, ProceedingJoinPoint joinPoint) {
        String base = method != null
                ? method.getDeclaringClass().getName() + "#" + method.getName()
                : joinPoint.getSignature().toLongString();
        return base + ":" + Arrays.deepHashCode(joinPoint.getArgs());
    }

    /**
     * Synchronous composition, innermost to outermost:
     * Bulkhead -> TimeLimiter -> RateLimiter -> CircuitBreaker -> Retry.
     */
    private Object executeSync(ProceedingJoinPoint joinPoint, ResilienceAnnotations annotations) {
        Supplier<Object> supplier = () -> proceed(joinPoint);

        if (annotations.bulkhead() != null) {
            supplier = io.github.resilience4j.bulkhead.Bulkhead
                    .decorateSupplier(bulkheadFor(annotations.bulkhead()), supplier);
        }
        if (annotations.timeLimiter() != null) {
            supplier = decorateSyncTimeLimiter(timeLimiterFor(annotations.timeLimiter()), supplier);
        }
        if (annotations.rateLimit() != null) {
            supplier = io.github.resilience4j.ratelimiter.RateLimiter
                    .decorateSupplier(rateLimiterFor(annotations.rateLimit()), supplier);
        }
        if (annotations.circuitBreaker() != null) {
            supplier = io.github.resilience4j.circuitbreaker.CircuitBreaker
                    .decorateSupplier(circuitBreakerFor(annotations.circuitBreaker()), supplier);
        }
        if (annotations.retry() != null) {
            supplier = io.github.resilience4j.retry.Retry
                    .decorateSupplier(retryFor(annotations.retry()), supplier);
        }
        return supplier.get();
    }

    /**
     * Asynchronous composition using the {@code decorateCompletionStage} variants,
     * innermost to outermost: Bulkhead -> TimeLimiter -> RateLimiter -> CircuitBreaker -> Retry.
     */
    @SuppressWarnings("unchecked")
    private Object executeAsync(ProceedingJoinPoint joinPoint, ResilienceAnnotations annotations,
                                String fallbackMethod) {
        Supplier<CompletionStage<Object>> supplier = () -> {
            try {
                injectChaos(joinPoint);
                Object result = joinPoint.proceed();
                return result == null
                        ? CompletableFuture.completedFuture(null)
                        : (CompletionStage<Object>) result;
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        };

        if (annotations.bulkhead() != null) {
            supplier = io.github.resilience4j.bulkhead.Bulkhead
                    .decorateCompletionStage(bulkheadFor(annotations.bulkhead()), supplier);
        }
        if (annotations.timeLimiter() != null) {
            supplier = io.github.resilience4j.timelimiter.TimeLimiter
                    .decorateCompletionStage(timeLimiterFor(annotations.timeLimiter()), SCHEDULER, supplier);
        }
        if (annotations.rateLimit() != null) {
            supplier = io.github.resilience4j.ratelimiter.RateLimiter
                    .decorateCompletionStage(rateLimiterFor(annotations.rateLimit()), supplier);
        }
        if (annotations.circuitBreaker() != null) {
            supplier = io.github.resilience4j.circuitbreaker.CircuitBreaker
                    .decorateCompletionStage(circuitBreakerFor(annotations.circuitBreaker()), supplier);
        }
        if (annotations.retry() != null) {
            supplier = io.github.resilience4j.retry.Retry
                    .decorateCompletionStage(retryFor(annotations.retry()), SCHEDULER, supplier);
        }

        CompletableFuture<Object> future = supplier.get().toCompletableFuture();
        if (fallbackMethod.isEmpty()) {
            return future;
        }
        return future.exceptionallyCompose(throwable -> {
            try {
                Object result = invokeFallback(joinPoint, fallbackMethod, unwrap(throwable));
                return result instanceof CompletionStage<?> stage
                        ? (CompletionStage<Object>) stage
                        : CompletableFuture.completedFuture(result);
            } catch (Throwable fallbackFailure) {
                return CompletableFuture.failedFuture(fallbackFailure);
            }
        }).toCompletableFuture();
    }

    /** Runs the (already decorated) supplier on a separate thread and awaits with a timeout. */
    private static Supplier<Object> decorateSyncTimeLimiter(
            io.github.resilience4j.timelimiter.TimeLimiter timeLimiter, Supplier<Object> inner) {
        return () -> {
            try {
                return timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(inner));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new ResilienceInvocationException(t);
            }
        };
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            injectChaos(joinPoint);
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new ResilienceInvocationException(t);
        }
    }

    /**
     * Applies the optional chaos policy at the innermost point of the decorator chain,
     * so injected latency and errors are observed by the surrounding decorators exactly
     * as real behaviour would be.
     */
    private void injectChaos(ProceedingJoinPoint joinPoint) {
        if (chaosPolicy != null) {
            chaosPolicy.apply(joinPoint.getSignature().getName());
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ResilienceInvocationException
                || current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Object adaptFallbackResult(Object result, boolean async) {
        if (async && !(result instanceof CompletionStage)) {
            return CompletableFuture.completedFuture(result);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Named instance resolution (annotation attributes honored, cached per name)
    // ------------------------------------------------------------------

    private io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreakerFor(CircuitBreaker annotation) {
        return circuitBreakerCache.computeIfAbsent(annotation.name(), name -> {
            if (allDefaults(annotation)) {
                return circuitBreakerRegistry.circuitBreaker(name);
            }
            CircuitBreakerConfig.Builder builder =
                    CircuitBreakerConfig.from(circuitBreakerRegistry.getDefaultConfig());
            if (annotation.failureRateThreshold() >= 0) {
                builder.failureRateThreshold(annotation.failureRateThreshold());
            }
            if (annotation.slowCallRateThreshold() >= 0) {
                builder.slowCallRateThreshold(annotation.slowCallRateThreshold());
            }
            if (annotation.slowCallDurationThreshold() >= 0) {
                builder.slowCallDurationThreshold(Duration.ofMillis(annotation.slowCallDurationThreshold()));
            }
            if (annotation.slidingWindowSize() >= 0) {
                builder.slidingWindowSize(annotation.slidingWindowSize());
            }
            if (annotation.minimumNumberOfCalls() >= 0) {
                builder.minimumNumberOfCalls(annotation.minimumNumberOfCalls());
            }
            if (annotation.permittedNumberOfCallsInHalfOpenState() >= 0) {
                builder.permittedNumberOfCallsInHalfOpenState(annotation.permittedNumberOfCallsInHalfOpenState());
            }
            if (annotation.waitDurationInOpenState() >= 0) {
                builder.waitDurationInOpenState(Duration.ofMillis(annotation.waitDurationInOpenState()));
            }
            log.debug("Creating circuit breaker '{}' from annotation attributes", name);
            return circuitBreakerRegistry.circuitBreaker(name, builder.build());
        });
    }

    private io.github.resilience4j.retry.Retry retryFor(Retry annotation) {
        return retryCache.computeIfAbsent(annotation.name(), name -> {
            if (annotation.maxAttempts() < 0 && annotation.waitDuration() < 0) {
                return retryRegistry.retry(name);
            }
            RetryConfig.Builder<Object> builder = RetryConfig.from(retryRegistry.getDefaultConfig());
            if (annotation.maxAttempts() >= 0) {
                builder.maxAttempts(annotation.maxAttempts());
            }
            if (annotation.waitDuration() >= 0) {
                builder.waitDuration(Duration.ofMillis(annotation.waitDuration()));
            }
            log.debug("Creating retry '{}' from annotation attributes", name);
            return retryRegistry.retry(name, builder.build());
        });
    }

    private io.github.resilience4j.ratelimiter.RateLimiter rateLimiterFor(RateLimit annotation) {
        return rateLimiterCache.computeIfAbsent(annotation.name(), name -> {
            if (annotation.limitForPeriod() < 0 && annotation.limitRefreshPeriod() < 0
                    && annotation.timeoutDuration() < 0) {
                return rateLimiterRegistry.rateLimiter(name);
            }
            RateLimiterConfig.Builder builder = RateLimiterConfig.from(rateLimiterRegistry.getDefaultConfig());
            if (annotation.limitForPeriod() >= 0) {
                builder.limitForPeriod(annotation.limitForPeriod());
            }
            if (annotation.limitRefreshPeriod() >= 0) {
                builder.limitRefreshPeriod(Duration.ofMillis(annotation.limitRefreshPeriod()));
            }
            if (annotation.timeoutDuration() >= 0) {
                builder.timeoutDuration(Duration.ofMillis(annotation.timeoutDuration()));
            }
            log.debug("Creating rate limiter '{}' from annotation attributes", name);
            return rateLimiterRegistry.rateLimiter(name, builder.build());
        });
    }

    private io.github.resilience4j.bulkhead.Bulkhead bulkheadFor(Bulkhead annotation) {
        return bulkheadCache.computeIfAbsent(annotation.name(), name -> {
            if (annotation.type() == Bulkhead.Type.THREADPOOL) {
                log.debug("Bulkhead '{}' requested THREADPOOL isolation; using semaphore isolation", name);
            }
            if (annotation.maxConcurrentCalls() < 0 && annotation.maxWaitDuration() < 0) {
                return bulkheadRegistry.bulkhead(name);
            }
            BulkheadConfig.Builder builder = BulkheadConfig.from(bulkheadRegistry.getDefaultConfig());
            if (annotation.maxConcurrentCalls() >= 0) {
                builder.maxConcurrentCalls(annotation.maxConcurrentCalls());
            }
            if (annotation.maxWaitDuration() >= 0) {
                builder.maxWaitDuration(Duration.ofMillis(annotation.maxWaitDuration()));
            }
            log.debug("Creating bulkhead '{}' from annotation attributes", name);
            return bulkheadRegistry.bulkhead(name, builder.build());
        });
    }

    private io.github.resilience4j.timelimiter.TimeLimiter timeLimiterFor(TimeLimiter annotation) {
        return timeLimiterCache.computeIfAbsent(annotation.name(), name -> {
            TimeLimiterConfig defaults = timeLimiterRegistry.getDefaultConfig();
            if (annotation.timeoutDuration() < 0
                    && annotation.cancelRunningFuture() == defaults.shouldCancelRunningFuture()) {
                return timeLimiterRegistry.timeLimiter(name);
            }
            TimeLimiterConfig.Builder builder = TimeLimiterConfig.from(defaults)
                    .cancelRunningFuture(annotation.cancelRunningFuture());
            if (annotation.timeoutDuration() >= 0) {
                builder.timeoutDuration(Duration.ofMillis(annotation.timeoutDuration()));
            }
            log.debug("Creating time limiter '{}' from annotation attributes", name);
            return timeLimiterRegistry.timeLimiter(name, builder.build());
        });
    }

    private static boolean allDefaults(CircuitBreaker annotation) {
        return annotation.failureRateThreshold() < 0
                && annotation.slowCallRateThreshold() < 0
                && annotation.slowCallDurationThreshold() < 0
                && annotation.slidingWindowSize() < 0
                && annotation.minimumNumberOfCalls() < 0
                && annotation.permittedNumberOfCallsInHalfOpenState() < 0
                && annotation.waitDurationInOpenState() < 0;
    }

    // ------------------------------------------------------------------
    // Fallback handling
    // ------------------------------------------------------------------

    private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName, Throwable cause) {
        try {
            Object target = joinPoint.getTarget();
            Method fallbackMethod = findFallbackMethod(target.getClass(), fallbackMethodName, cause);
            if (fallbackMethod == null) {
                throw new NoSuchMethodException(fallbackMethodName);
            }
            fallbackMethod.setAccessible(true);
            return fallbackMethod.getParameterCount() == 1
                    ? fallbackMethod.invoke(target, cause)
                    : fallbackMethod.invoke(target);
        } catch (Exception e) {
            Throwable failure = e instanceof InvocationTargetException ite ? ite.getTargetException() : e;
            log.error("Failed to invoke fallback method: {}", fallbackMethodName, failure);
            throw new RuntimeException("Fallback method execution failed", failure);
        }
    }

    private static Method findFallbackMethod(Class<?> targetClass, String name, Throwable cause) {
        Method noArgCandidate = null;
        for (Class<?> type = targetClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(cause.getClass())) {
                    return method;
                }
                if (method.getParameterCount() == 0 && noArgCandidate == null) {
                    noArgCandidate = method;
                }
            }
        }
        return noArgCandidate;
    }

    // ------------------------------------------------------------------
    // Annotation discovery
    // ------------------------------------------------------------------

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (method != null && target != null && method.getDeclaringClass().isInterface()) {
            try {
                method = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // keep the interface method
            }
        }
        return method;
    }

    /**
     * Immutable holder for the resilience annotations found on a method (or its class).
     * Fallback resolution follows composition order (outermost first):
     * Retry, CircuitBreaker, RateLimit, TimeLimiter, Bulkhead.
     */
    record ResilienceAnnotations(CircuitBreaker circuitBreaker, Retry retry, RateLimit rateLimit,
                                 Bulkhead bulkhead, TimeLimiter timeLimiter) {

        static ResilienceAnnotations of(Method method, Object target) {
            Class<?> targetClass = target != null ? target.getClass() : null;
            return new ResilienceAnnotations(
                    find(method, targetClass, CircuitBreaker.class),
                    find(method, targetClass, Retry.class),
                    find(method, targetClass, RateLimit.class),
                    find(method, targetClass, Bulkhead.class),
                    find(method, targetClass, TimeLimiter.class));
        }

        private static <A extends Annotation> A find(Method method, Class<?> targetClass, Class<A> type) {
            if (method == null) {
                return null;
            }
            A annotation = method.getAnnotation(type);
            if (annotation != null) {
                return annotation;
            }
            annotation = method.getDeclaringClass().getAnnotation(type);
            if (annotation == null && targetClass != null) {
                annotation = targetClass.getAnnotation(type);
            }
            return annotation;
        }

        boolean isEmpty() {
            return circuitBreaker == null && retry == null && rateLimit == null
                    && bulkhead == null && timeLimiter == null;
        }

        /**
         * Determines whether the fallback cache should be used for this method.
         *
         * @param global global fallback-cache enablement flag
         * @return {@code true} when caching is enabled globally or via a
         *         {@code fallbackCache=true} attribute on the retry / circuit breaker
         */
        boolean fallbackCacheEnabled(boolean global) {
            if (global) {
                return true;
            }
            if (circuitBreaker != null && circuitBreaker.fallbackCache()) {
                return true;
            }
            return retry != null && retry.fallbackCache();
        }

        String fallbackMethod() {
            if (retry != null && !retry.fallbackMethod().isEmpty()) {
                return retry.fallbackMethod();
            }
            if (circuitBreaker != null && !circuitBreaker.fallbackMethod().isEmpty()) {
                return circuitBreaker.fallbackMethod();
            }
            if (rateLimit != null && !rateLimit.fallbackMethod().isEmpty()) {
                return rateLimit.fallbackMethod();
            }
            if (timeLimiter != null && !timeLimiter.fallbackMethod().isEmpty()) {
                return timeLimiter.fallbackMethod();
            }
            if (bulkhead != null && !bulkhead.fallbackMethod().isEmpty()) {
                return bulkhead.fallbackMethod();
            }
            return "";
        }

        String describe() {
            StringBuilder sb = new StringBuilder("[");
            if (retry != null) {
                sb.append("retry=").append(retry.name()).append(' ');
            }
            if (circuitBreaker != null) {
                sb.append("circuitBreaker=").append(circuitBreaker.name()).append(' ');
            }
            if (rateLimit != null) {
                sb.append("rateLimit=").append(rateLimit.name()).append(' ');
            }
            if (timeLimiter != null) {
                sb.append("timeLimiter=").append(timeLimiter.name()).append(' ');
            }
            if (bulkhead != null) {
                sb.append("bulkhead=").append(bulkhead.name()).append(' ');
            }
            return sb.toString().trim() + "]";
        }
    }

    /** Wrapper used to tunnel checked exceptions through {@link Supplier} chains. */
    private static final class ResilienceInvocationException extends RuntimeException {
        ResilienceInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
