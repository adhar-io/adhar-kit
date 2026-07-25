package com.adhar.kit.dapr.aspect;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@code @DaprState(key = ...)} SpEL expressions against method arguments, mirroring
 * the {@code #paramName} / {@code #p0} / {@code #a0} conventions used by Spring's own
 * {@code @Cacheable}. A blank expression, or one that evaluates to {@code null}, falls back to
 * a deterministic default key.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
final class DaprKeyResolver {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    String resolveKey(String keyExpression, Method method, Object target, Object[] args) {
        if (keyExpression == null || keyExpression.isBlank()) {
            return defaultKey(method, args);
        }
        Object value = parseExpression(keyExpression).getValue(createContext(method, target, args));
        return value != null ? String.valueOf(value) : defaultKey(method, args);
    }

    private Expression parseExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, parser::parseExpression);
    }

    private StandardEvaluationContext createContext(Method method, Object target, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext(target);
        Object[] safeArgs = args != null ? args : new Object[0];
        String[] paramNames = method != null ? discoverer.getParameterNames(method) : null;
        for (int i = 0; i < safeArgs.length; i++) {
            if (paramNames != null && i < paramNames.length) {
                context.setVariable(paramNames[i], safeArgs[i]);
            }
            context.setVariable("p" + i, safeArgs[i]);
            context.setVariable("a" + i, safeArgs[i]);
        }
        return context;
    }

    private static String defaultKey(Method method, Object[] args) {
        if (method == null) {
            return "dapr-state:" + Arrays.deepHashCode(args);
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName()
                + ":" + Arrays.deepHashCode(args);
    }
}
