package com.adhar.kit.security.aspect;

import com.adhar.kit.security.annotation.CheckMode;
import com.adhar.kit.security.annotation.RequiresPermission;
import com.adhar.kit.security.annotation.RequiresRole;
import com.adhar.kit.security.api.SecurityService;
import com.adhar.kit.security.exception.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Aspect that enforces {@link RequiresRole} and {@link RequiresPermission} annotations
 * (method-level and class-level) using the framework-agnostic {@link SecurityService}.
 *
 * <p>Method-level annotations take precedence over class-level ones for the same
 * annotation type. When both a role and a permission constraint apply, both must pass.
 * On failure an {@link AccessDeniedException} is thrown.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Aspect
@Slf4j
public class AccessControlAspect {

    private final SecurityService securityService;

    /**
     * Creates the aspect.
     *
     * @param securityService the security service used to evaluate roles/permissions
     */
    public AccessControlAspect(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Intercepts invocations of methods annotated (directly or at class level) with
     * {@link RequiresRole} or {@link RequiresPermission} and enforces the constraints.
     *
     * @param joinPoint the intercepted invocation
     * @return the method result if access is granted
     * @throws Throwable the target method's exception, or {@link AccessDeniedException}
     */
    @Around("@annotation(com.adhar.kit.security.annotation.RequiresRole)"
        + " || @within(com.adhar.kit.security.annotation.RequiresRole)"
        + " || @annotation(com.adhar.kit.security.annotation.RequiresPermission)"
        + " || @within(com.adhar.kit.security.annotation.RequiresPermission)")
    public Object enforceAccessControl(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Class<?> targetClass = joinPoint.getTarget() != null
            ? joinPoint.getTarget().getClass()
            : method.getDeclaringClass();

        RequiresRole requiresRole = findAnnotation(method, targetClass, RequiresRole.class);
        if (requiresRole != null) {
            checkRoles(requiresRole, joinPoint);
        }

        RequiresPermission requiresPermission = findAnnotation(method, targetClass, RequiresPermission.class);
        if (requiresPermission != null) {
            checkPermissions(requiresPermission, joinPoint);
        }

        return joinPoint.proceed();
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (joinPoint.getTarget() != null) {
            return AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
        }
        return method;
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(
            Method method, Class<?> targetClass, Class<A> annotationType) {
        A annotation = method.getAnnotation(annotationType);
        if (annotation == null) {
            annotation = targetClass.getAnnotation(annotationType);
        }
        return annotation;
    }

    private void checkRoles(RequiresRole annotation, ProceedingJoinPoint joinPoint) {
        String[] roles = annotation.value();
        boolean granted = annotation.mode() == CheckMode.ALL_OF
            ? securityService.hasAllRoles(roles)
            : securityService.hasAnyRole(roles);

        if (!granted) {
            log.debug("Access denied at {}: missing role(s) {} (mode={})",
                joinPoint.getSignature().toShortString(), Arrays.toString(roles), annotation.mode());
            throw new AccessDeniedException(String.format(
                "Access denied: requires %s of roles %s",
                annotation.mode() == CheckMode.ALL_OF ? "all" : "any",
                Arrays.toString(roles)));
        }
    }

    private void checkPermissions(RequiresPermission annotation, ProceedingJoinPoint joinPoint) {
        String[] permissions = annotation.value();
        boolean granted = annotation.mode() == CheckMode.ALL_OF
            ? Arrays.stream(permissions).allMatch(securityService::hasPermission)
            : Arrays.stream(permissions).anyMatch(securityService::hasPermission);

        if (!granted) {
            log.debug("Access denied at {}: missing permission(s) {} (mode={})",
                joinPoint.getSignature().toShortString(), Arrays.toString(permissions), annotation.mode());
            throw new AccessDeniedException(String.format(
                "Access denied: requires %s of permissions %s",
                annotation.mode() == CheckMode.ALL_OF ? "all" : "any",
                Arrays.toString(permissions)));
        }
    }
}
