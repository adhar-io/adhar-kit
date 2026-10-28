package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprLock;
import com.adhar.kit.dapr.lock.DaprLockNotAcquiredException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

/**
 * Enforces {@link DaprLock}: the method runs only while this caller holds the distributed lock on
 * the resolved resource, and the lock is released afterwards (it also expires on its own, so a
 * crashed holder cannot block the resource forever).
 *
 * <p>The lock owner is made unique per invocation ({@code <lockOwner>-<uuid>}): with a fixed owner
 * shared by every replica, one replica's unlock would release another replica's lock.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
@Aspect
public class DaprLockAspect {

    private final DaprFacade daprFacade;
    private final DaprKeyResolver keyResolver = new DaprKeyResolver();

    public DaprLockAspect(DaprFacade daprFacade) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
    }

    @Around("@annotation(daprLock)")
    public Object applyLock(ProceedingJoinPoint joinPoint, DaprLock daprLock) throws Throwable {
        Method method = AspectSupport.resolveMethod(joinPoint);
        String resource = keyResolver.resolveKey(daprLock.resourceId(), method, joinPoint.getTarget(),
                joinPoint.getArgs());
        String store = daprLock.storeName().isBlank() ? daprFacade.components().lockStore() : daprLock.storeName();
        String base = daprLock.lockOwner().isBlank() ? daprFacade.connection().appId() : daprLock.lockOwner();
        String owner = base + "-" + UUID.randomUUID();

        if (!daprFacade.tryLock(store, resource, owner, daprLock.expiryInSeconds())) {
            if (daprLock.failOnLockFailure()) {
                throw new DaprLockNotAcquiredException(store, resource);
            }
            log.debug("Lock '{}' in store '{}' is held elsewhere; skipping {}", resource, store,
                    method != null ? method.getName() : "method");
            return AspectSupport.defaultValue(method != null ? method.getReturnType() : null);
        }
        try {
            return joinPoint.proceed();
        } finally {
            try {
                daprFacade.unlock(store, resource, owner);
            } catch (DaprError e) {
                log.warn("Releasing lock '{}' in store '{}' failed (it expires on its own): {}", resource, store,
                        e.getMessage());
            }
        }
    }
}
