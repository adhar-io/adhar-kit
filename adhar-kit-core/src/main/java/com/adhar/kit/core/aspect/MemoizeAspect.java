package com.adhar.kit.core.aspect;

import com.adhar.kit.core.annotation.Memoize;
import com.adhar.kit.core.util.Memoizer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect implementation for the {@link Memoize} annotation.
 *
 * <p>Caches method results in a {@link Memoizer} keyed on the method signature
 * plus (optionally) the invocation arguments, honoring the annotation's
 * {@code ttl} and cache {@code value} (name) attributes.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Slf4j
public class MemoizeAspect {

    /**
     * One memoizer per cache name + TTL combination (a Memoizer's TTL is fixed
     * at construction time).
     */
    private final Map<String, Memoizer<String, Object>> caches = new ConcurrentHashMap<>();

    /**
     * Caches results of methods annotated with {@link Memoize}.
     *
     * @param joinPoint the intercepted method invocation
     * @param memoize the annotation instance
     * @return the cached or freshly computed result
     * @throws Throwable if the method invocation fails
     */
    @Around("@annotation(memoize)")
    public Object applyMemoize(ProceedingJoinPoint joinPoint, Memoize memoize) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        Memoizer<String, Object> cache = caches.computeIfAbsent(
            memoize.value() + "|" + memoize.ttl(),
            name -> new Memoizer<>(memoize.ttl()));

        String key = buildKey(method, joinPoint.getArgs(), memoize.useAllParams());
        log.debug("Applying @Memoize to {} (cache={}, ttl={}ms)",
            signature.toShortString(), memoize.value(), memoize.ttl());

        try {
            return cache.get(key, k -> proceed(joinPoint));
        } catch (ProceedException e) {
            throw e.getCause();
        }
    }

    /**
     * Clears all caches managed by this aspect.
     */
    public void clearAll() {
        caches.values().forEach(Memoizer::clear);
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            throw new ProceedException(t);
        }
    }

    private static String buildKey(Method method, Object[] args, boolean useAllParams) {
        StringBuilder key = new StringBuilder()
            .append(method.getDeclaringClass().getName())
            .append('#')
            .append(method.getName())
            .append(Arrays.toString(method.getParameterTypes()));
        if (useAllParams) {
            key.append(Arrays.deepToString(args));
        }
        return key.toString();
    }

    /**
     * Carrier that tunnels checked {@link Throwable}s from
     * {@link ProceedingJoinPoint#proceed()} through the {@code Function}-based
     * {@link Memoizer} API.
     */
    private static final class ProceedException extends RuntimeException {
        private ProceedException(Throwable cause) {
            super(cause);
        }
    }
}
