package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprBinding;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Objects;

/**
 * Enforces {@link DaprBinding}: after the method returns, its result (or the chosen parameter)
 * is sent to the output binding. A {@code null} payload sends nothing; a failed binding call fails
 * the method.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
@Aspect
public class DaprBindingAspect {

    private final DaprFacade daprFacade;

    public DaprBindingAspect(DaprFacade daprFacade) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
    }

    @Around("@annotation(daprBinding)")
    public Object applyBinding(ProceedingJoinPoint joinPoint, DaprBinding daprBinding) throws Throwable {
        Object result = joinPoint.proceed();
        Object payload = daprBinding.sendReturnValue()
                ? result
                : AspectSupport.argument(joinPoint, daprBinding.parameterIndex(), "DaprBinding");
        if (payload != null) {
            log.debug("Invoking Dapr binding: binding={}, operation={}", daprBinding.bindingName(),
                    daprBinding.operation());
            daprFacade.invokeBinding(daprBinding.bindingName(), daprBinding.operation(), payload);
        }
        return result;
    }
}
