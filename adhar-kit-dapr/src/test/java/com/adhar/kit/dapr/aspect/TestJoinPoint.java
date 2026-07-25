package com.adhar.kit.dapr.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe {@link ProceedingJoinPoint} test double that invokes a real method on a real
 * target and counts {@code proceed()} calls. Mirrors the equivalent double used in
 * adhar-kit-cache's aspect tests.
 */
final class TestJoinPoint implements ProceedingJoinPoint {

    private final Object target;
    private final Method method;
    private final Object[] args;
    final AtomicInteger proceedCount = new AtomicInteger();

    TestJoinPoint(Object target, String methodName, Object... args) {
        this.target = target;
        this.method = findMethod(target.getClass(), methodName);
        this.args = args;
    }

    static Method findMethod(Class<?> type, String name) {
        for (Method candidate : type.getDeclaredMethods()) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No method named " + name + " on " + type);
    }

    Method method() {
        return method;
    }

    @Override
    public Object proceed() throws Throwable {
        proceedCount.incrementAndGet();
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Override
    public Object proceed(Object[] ignored) throws Throwable {
        return proceed();
    }

    @Override
    public void set$AroundClosure(AroundClosure arc) {
        // not needed for tests
    }

    @Override
    public String toShortString() {
        return method.getName();
    }

    @Override
    public String toLongString() {
        return method.toString();
    }

    @Override
    public Object getThis() {
        return target;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public Object[] getArgs() {
        return args;
    }

    @Override
    public Signature getSignature() {
        return new TestMethodSignature(method);
    }

    @Override
    public SourceLocation getSourceLocation() {
        return null;
    }

    @Override
    public String getKind() {
        return METHOD_EXECUTION;
    }

    @Override
    public JoinPoint.StaticPart getStaticPart() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    record TestMethodSignature(Method wrapped) implements MethodSignature {

        @Override
        public Method getMethod() {
            return wrapped;
        }

        @Override
        public Class getReturnType() {
            return wrapped.getReturnType();
        }

        @Override
        public Class[] getParameterTypes() {
            return wrapped.getParameterTypes();
        }

        @Override
        public String[] getParameterNames() {
            return null;
        }

        @Override
        public Class[] getExceptionTypes() {
            return wrapped.getExceptionTypes();
        }

        @Override
        public String getName() {
            return wrapped.getName();
        }

        @Override
        public String toShortString() {
            return wrapped.getName();
        }

        @Override
        public String toLongString() {
            return wrapped.toString();
        }

        @Override
        public int getModifiers() {
            return wrapped.getModifiers();
        }

        @Override
        public Class getDeclaringType() {
            return wrapped.getDeclaringClass();
        }

        @Override
        public String getDeclaringTypeName() {
            return wrapped.getDeclaringClass().getName();
        }
    }
}
