package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprPublish;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Objects;

/**
 * Enforces the declarative {@link DaprPublish} semantics documented on the annotation: after
 * the annotated method executes, its result (or a specific parameter) is published to the
 * configured pub/sub topic, closing the gap where the annotation existed but nothing acted
 * on it.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class DaprPublishAspect {

    private final DaprFacade daprFacade;

    public DaprPublishAspect(DaprFacade daprFacade) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
    }

    @Around("@annotation(daprPublish)")
    public Object applyPublish(ProceedingJoinPoint joinPoint, DaprPublish daprPublish) throws Throwable {
        Object result = joinPoint.proceed();

        Object payload = daprPublish.publishReturnValue()
                ? result
                : resolveParameter(joinPoint, daprPublish.parameterIndex());

        if (payload != null) {
            log.debug("Publishing Dapr event: pubsub={}, topic={}", daprPublish.pubsubName(), daprPublish.topic());
            daprFacade.publishEvent(daprPublish.pubsubName(), daprPublish.topic(), payload);
        }
        return result;
    }

    private Object resolveParameter(ProceedingJoinPoint joinPoint, int parameterIndex) {
        Object[] args = joinPoint.getArgs();
        if (parameterIndex < 0 || parameterIndex >= args.length) {
            throw new IllegalArgumentException(
                    "@DaprPublish parameterIndex " + parameterIndex + " is out of bounds for "
                            + args.length + " argument(s)");
        }
        return args[parameterIndex];
    }
}
