package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprState;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Enforces the declarative {@link DaprState} semantics documented on the annotation: methods
 * are wrapped so that state is automatically saved, fetched, or deleted around the method
 * invocation, closing the gap where the annotation existed but nothing acted on it.
 *
 * <ul>
 *   <li>{@code SAVE} (default) - proceeds, then saves the (non-null) return value under the
 *       resolved key.</li>
 *   <li>{@code GET} - skips the method body (whose return value is only a placeholder per the
 *       annotation's documented usage) and returns the state store value directly.</li>
 *   <li>{@code DELETE} - proceeds, then deletes the resolved key.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class DaprStateAspect {

    private final DaprFacade daprFacade;
    private final DaprKeyResolver keyResolver = new DaprKeyResolver();

    public DaprStateAspect(DaprFacade daprFacade) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
    }

    @Around("@annotation(daprState)")
    public Object applyState(ProceedingJoinPoint joinPoint, DaprState daprState) throws Throwable {
        Method method = resolveMethod(joinPoint);
        String key = keyResolver.resolveKey(daprState.key(), method, joinPoint.getTarget(), joinPoint.getArgs());
        String store = daprState.storeName().isBlank() ? daprFacade.components().stateStore() : daprState.storeName();

        return switch (daprState.operation()) {
            case GET -> handleGet(joinPoint, store, method, key);
            case DELETE -> handleDelete(joinPoint, store, key);
            case SAVE -> handleSave(joinPoint, store, key);
        };
    }

    private Object handleGet(ProceedingJoinPoint joinPoint, String store, Method method, String key)
            throws Throwable {
        Class<?> returnType = method != null ? method.getReturnType() : Object.class;
        if (returnType == void.class || returnType == Void.class) {
            return joinPoint.proceed();
        }
        log.debug("Fetching Dapr state: store={}, key={}", store, key);
        // The generic return type keeps List<Order> a list of orders rather than of maps.
        java.lang.reflect.Type type = method != null ? method.getGenericReturnType() : Object.class;
        return daprFacade.getState(store, key, io.dapr.utils.TypeRef.get(type));
    }

    private Object handleDelete(ProceedingJoinPoint joinPoint, String store, String key) throws Throwable {
        Object result = joinPoint.proceed();
        log.debug("Deleting Dapr state: store={}, key={}", store, key);
        daprFacade.deleteState(store, key);
        return result;
    }

    private Object handleSave(ProceedingJoinPoint joinPoint, String store, String key) throws Throwable {
        Object result = joinPoint.proceed();
        if (result != null) {
            log.debug("Saving Dapr state: store={}, key={}", store, key);
            daprFacade.saveState(store, key, result);
        }
        return result;
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target != null && method.getDeclaringClass().isInterface()) {
            try {
                return target.getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // keep the interface method
            }
        }
        return method;
    }
}
