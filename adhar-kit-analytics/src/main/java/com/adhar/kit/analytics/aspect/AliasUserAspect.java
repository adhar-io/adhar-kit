package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.AliasUser;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for processing @AliasUser annotation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class AliasUserAspect {

    private final AnalyticsFacade analytics;

    public AliasUserAspect() {
        this.analytics = AnalyticsFacade.getInstance();
    }

    /**
     * Package-private constructor for injecting a controllable
     * {@link AnalyticsFacade} (e.g. a Mockito mock) in tests, without going
     * through the process-wide singleton.
     */
    AliasUserAspect(AnalyticsFacade analytics) {
        this.analytics = analytics;
    }

    @Around("@annotation(aliasUser)")
    public Object processAliasUser(ProceedingJoinPoint joinPoint, AliasUser aliasUser) throws Throwable {
        if (!analytics.isAvailable()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, Object> params = buildParameterMap(
            signature.getParameterNames(),
            joinPoint.getArgs()
        );

        String distinctId = extractParam(params, aliasUser.distinctIdParam());
        String alias = extractParam(params, aliasUser.aliasParam());

        if (distinctId != null && alias != null) {
            log.debug("Aliasing {} to {}", distinctId, alias);
            analytics.alias(distinctId, alias);

            if (aliasUser.trackEvent()) {
                analytics.track(alias, aliasUser.eventName(), Map.of(
                    "distinct_id", distinctId,
                    "alias", alias
                ));
            }
        }

        return joinPoint.proceed();
    }

    private Map<String, Object> buildParameterMap(String[] names, Object[] values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            map.put(names[i], values[i]);
        }
        return map;
    }

    private String extractParam(Map<String, Object> params, String paramName) {
        Object value = params.get(paramName);
        return value != null ? value.toString() : null;
    }
}

