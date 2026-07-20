package com.adhar.adharkit.cache.key;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates cache keys and evaluates caching conditions for the annotation aspects.
 *
 * <p>Key expressions use SpEL (Spring Expression Language) with the following
 * variables available:</p>
 * <ul>
 *   <li><code>#paramName</code> - method parameter by name (requires {@code -parameters})</li>
 *   <li><code>#p0</code>/<code>#a0</code> - method parameter by index</li>
 *   <li><code>#result</code> - method return value (conditions only, post-invocation)</li>
 *   <li><code>#root.methodName</code>, <code>#root.target</code>, <code>#root.args</code></li>
 * </ul>
 *
 * <p>When no key expression is supplied, a deterministic default key of
 * {@code className.methodName:argsHash} is used.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class CacheKeyGenerator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Generates a cache key for the given method invocation.
     *
     * @param keyExpression SpEL key expression (may be null/blank for the default key)
     * @param method        the invoked method
     * @param target        the target object
     * @param args          the invocation arguments
     * @return the generated key (never null)
     */
    public Object generate(String keyExpression, Method method, Object target, Object[] args) {
        Objects.requireNonNull(method, "Method must not be null");
        if (keyExpression == null || keyExpression.isBlank()) {
            return defaultKey(method, args);
        }
        Object value = parse(keyExpression).getValue(createContext(method, target, args, null));
        return value != null ? value : defaultKey(method, args);
    }

    /**
     * Evaluates a boolean SpEL condition. A blank condition evaluates to {@code true}.
     *
     * @param condition SpEL condition expression
     * @param method    the invoked method
     * @param target    the target object
     * @param args      the invocation arguments
     * @param result    the method result, bound as {@code #result} (may be null)
     * @return true if the condition is blank or evaluates to true
     */
    public boolean evaluateCondition(String condition, Method method, Object target, Object[] args, Object result) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        Boolean value = parse(condition).getValue(createContext(method, target, args, result), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    /**
     * Checks whether an expression references the method result.
     *
     * @param expression SpEL expression
     * @return true if the expression uses {@code #result}
     */
    public boolean referencesResult(String expression) {
        return expression != null && expression.contains("#result");
    }

    /**
     * Builds the default cache key: {@code className.methodName:argsHash}.
     *
     * @param method the invoked method
     * @param args   the invocation arguments
     * @return default key string
     */
    public String defaultKey(Method method, Object[] args) {
        return method.getDeclaringClass().getName() + "." + method.getName()
            + ":" + Arrays.deepHashCode(args);
    }

    private Expression parse(String expression) {
        return expressionCache.computeIfAbsent(expression, parser::parseExpression);
    }

    private StandardEvaluationContext createContext(Method method, Object target, Object[] args, Object result) {
        StandardEvaluationContext context =
            new StandardEvaluationContext(new CacheExpressionRootObject(method, target, args));

        Object[] safeArgs = args != null ? args : new Object[0];
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        for (int i = 0; i < safeArgs.length; i++) {
            if (paramNames != null && i < paramNames.length) {
                context.setVariable(paramNames[i], safeArgs[i]);
            }
            context.setVariable("p" + i, safeArgs[i]);
            context.setVariable("a" + i, safeArgs[i]);
        }
        context.setVariable("result", result);
        return context;
    }

    /**
     * Root object exposed to SpEL expressions as {@code #root}.
     */
    public static final class CacheExpressionRootObject {
        private final Method method;
        private final Object target;
        private final Object[] args;

        CacheExpressionRootObject(Method method, Object target, Object[] args) {
            this.method = method;
            this.target = target;
            this.args = args;
        }

        public Method getMethod() {
            return method;
        }

        public String getMethodName() {
            return method.getName();
        }

        public Object getTarget() {
            return target;
        }

        public Object[] getArgs() {
            return args;
        }
    }
}
