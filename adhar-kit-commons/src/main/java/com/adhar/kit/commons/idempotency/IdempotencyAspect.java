package com.adhar.kit.commons.idempotency;

import com.adhar.kit.commons.annotation.Idempotent;
import com.adhar.kit.commons.constant.CommonConstants;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AOP runtime for the {@link Idempotent} annotation.
 *
 * <p>Behaviour for a method annotated {@code @Idempotent(key = "#paymentId", ttl = 300)}:</p>
 * <ul>
 *   <li>First call: the key is claimed, the method executes and its result is cached
 *       for {@code ttl} seconds.</li>
 *   <li>Duplicate call within TTL after success: the cached result is returned
 *       <b>without</b> re-executing the method.</li>
 *   <li>Duplicate call while the original is still in flight: a
 *       {@link DuplicateRequestException} is thrown (fail-fast; the aspect never blocks).</li>
 *   <li>Failed call: the key is released so the operation can be retried.</li>
 * </ul>
 *
 * <p><b>Key resolution</b>: expressions containing {@code #} are evaluated as SpEL against
 * the method arguments (named parameters plus {@code #p0}/{@code #a0} style indexes,
 * requires {@code spring-expression}). Expressions containing {@code {n}} placeholders are
 * substituted with the n-th argument. Anything else is used as a literal. The resolved key
 * is namespaced with the annotation's {@code storage} attribute and the method signature,
 * so identical key values on different methods never collide.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private static final Pattern INDEX_PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final IdempotencyStore store;

    /**
     * Constructor.
     *
     * @param store the idempotency store backing this aspect
     */
    public IdempotencyAspect(IdempotencyStore store) {
        this.store = store;
    }

    /**
     * Wraps {@link Idempotent} methods with duplicate-call protection.
     *
     * @param joinPoint  the intercepted call
     * @param idempotent the annotation instance
     * @return the method result (fresh or cached)
     * @throws Throwable                  whatever the target method throws
     * @throws DuplicateRequestException  when a call with the same key is still in flight
     */
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = buildKey(joinPoint, idempotent);
        IdempotencyStore.Outcome outcome = store.begin(key, idempotent.ttl());
        switch (outcome.status()) {
            case COMPLETED -> {
                log.debug("Idempotent replay for key '{}' - returning cached result", key);
                return outcome.result();
            }
            case IN_PROGRESS -> throw new DuplicateRequestException(key);
            default -> {
                // ACQUIRED - fall through to execute
            }
        }
        try {
            Object result = joinPoint.proceed();
            store.complete(key, result, idempotent.ttl());
            return result;
        } catch (Throwable ex) {
            store.abort(key);
            throw ex;
        }
    }

    /**
     * Builds the fully qualified idempotency key: {@code storage::declaringClass.method::resolvedKey}.
     */
    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String resolved = resolveKeyExpression(idempotent.key(), signature, joinPoint.getArgs());
        return idempotent.storage() + CommonConstants.CACHE_KEY_SEPARATOR
            + method.getDeclaringClass().getName() + "." + method.getName()
            + CommonConstants.CACHE_KEY_SEPARATOR + resolved;
    }

    /**
     * Resolves the annotation's key expression against the invocation arguments.
     */
    private String resolveKeyExpression(String expression, MethodSignature signature, Object[] args) {
        if (expression.contains("#")) {
            return evaluateSpel(expression, signature, args);
        }
        Matcher matcher = INDEX_PLACEHOLDER.matcher(expression);
        if (matcher.find()) {
            return substituteIndexes(expression, args);
        }
        return expression;
    }

    private String evaluateSpel(String expression, MethodSignature signature, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < args.length; i++) {
            if (parameterNames != null && i < parameterNames.length && parameterNames[i] != null) {
                context.setVariable(parameterNames[i], args[i]);
            }
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        Expression parsed = parser.parseExpression(expression);
        return String.valueOf(parsed.getValue(context));
    }

    private String substituteIndexes(String expression, Object[] args) {
        Matcher matcher = INDEX_PLACEHOLDER.matcher(expression);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = index < args.length ? String.valueOf(args[index]) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
