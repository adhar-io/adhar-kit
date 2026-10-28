package com.adhar.kit.dapr.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * Shared join-point helpers for the kit's aspects.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
final class AspectSupport {

    private AspectSupport() {
    }

    /** The invoked method, on the target class when the join point names an interface method. */
    static Method resolveMethod(ProceedingJoinPoint joinPoint) {
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

    /** The default value of a primitive return type, so a skipped method still returns. */
    static Object defaultValue(Class<?> type) {
        if (type == null || !type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    /** The argument at {@code index}, failing clearly when the annotation names a missing one. */
    static Object argument(ProceedingJoinPoint joinPoint, int index, String annotation) {
        Object[] args = joinPoint.getArgs();
        if (index < 0 || index >= args.length) {
            throw new IllegalArgumentException("@" + annotation + " parameterIndex " + index
                    + " is out of bounds for " + args.length + " argument(s)");
        }
        return args[index];
    }
}
