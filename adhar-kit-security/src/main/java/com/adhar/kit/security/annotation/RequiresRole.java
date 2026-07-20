package com.adhar.kit.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method (or every method of the annotated class)
 * requires the current user to hold the given role(s).
 *
 * <p>Enforced by {@link com.adhar.kit.security.aspect.AccessControlAspect} via the
 * framework-agnostic {@link com.adhar.kit.security.api.SecurityService}. A
 * method-level annotation overrides a class-level one.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @RequiresRole("ADMIN")
 * public void deleteOrder(Long id) { ... }
 *
 * @RequiresRole(value = {"AUDITOR", "COMPLIANCE"}, mode = CheckMode.ALL_OF)
 * public Report generateComplianceReport() { ... }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 * @see RequiresPermission
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /**
     * The required role name(s), without any {@code ROLE_} prefix requirement
     * (both prefixed and unprefixed authorities are matched).
     *
     * @return required roles
     */
    String[] value();

    /**
     * How the declared roles are combined.
     *
     * @return the evaluation mode, {@link CheckMode#ANY_OF} by default
     */
    CheckMode mode() default CheckMode.ANY_OF;
}
