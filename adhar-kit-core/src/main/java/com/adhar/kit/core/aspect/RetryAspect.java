package com.adhar.kit.core.aspect;

import com.adhar.kit.core.annotation.Retry;
import com.adhar.kit.core.util.RetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * Aspect implementation for the {@link Retry} annotation.
 *
 * <p>Re-invokes the annotated method on failure, honoring {@code maxAttempts},
 * the {@code backoff} settings (initial delay, multiplier, max delay) and the
 * {@code retryOn} exception filter. Delegates the retry loop to
 * {@link RetryUtil}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Slf4j
public class RetryAspect {

    /**
     * Applies retry semantics around methods annotated with {@link Retry}.
     *
     * @param joinPoint the intercepted method invocation
     * @param retry the annotation instance
     * @return the method result
     * @throws Throwable the last failure if all attempts are exhausted, or the
     *                   original exception if it is not retryable
     */
    @Around("@annotation(retry)")
    public Object applyRetry(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        Retry.Backoff backoff = retry.backoff();
        int maxRetries = Math.max(retry.maxAttempts() - 1, 0);

        log.debug("Applying @Retry to {} (maxAttempts={}, delay={}ms, multiplier={}, maxDelay={}ms)",
            joinPoint.getSignature().toShortString(), retry.maxAttempts(),
            backoff.delay(), backoff.multiplier(), backoff.maxDelay());

        try {
            return RetryUtil.executeWithBackoff(
                () -> proceed(joinPoint),
                maxRetries,
                backoff.delay(),
                backoff.multiplier(),
                backoff.maxDelay(),
                e -> isRetryable(unwrap(e), retry.retryOn()));
        } catch (ProceedException e) {
            throw e.getCause();
        }
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            throw new ProceedException(t);
        }
    }

    private static Throwable unwrap(Exception e) {
        return e instanceof ProceedException ? e.getCause() : e;
    }

    private static boolean isRetryable(Throwable failure, Class<? extends Throwable>[] retryOn) {
        for (Class<? extends Throwable> type : retryOn) {
            if (type.isInstance(failure)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Carrier that tunnels checked {@link Throwable}s from
     * {@link ProceedingJoinPoint#proceed()} through the {@code Supplier}-based
     * {@link RetryUtil} API.
     */
    private static final class ProceedException extends RuntimeException {
        private ProceedException(Throwable cause) {
            super(cause);
        }
    }
}
