package com.adhar.kit.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method (or every method of the annotated class)
 * requires the current user to hold the given permission(s) (e.g. {@code order:create}).
 *
 * <p>Enforced by {@link com.adhar.kit.security.aspect.AccessControlAspect} via the
 * framework-agnostic {@link com.adhar.kit.security.api.SecurityService}. A
 * method-level annotation overrides a class-level one.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @RequiresPermission("order:create")
 * public Order createOrder(OrderRequest request) { ... }
 *
 * @RequiresPermission(value = {"order:read", "order:export"}, mode = CheckMode.ALL_OF)
 * public byte[] exportOrders() { ... }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 * @see RequiresRole
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * The required permission(s), matched as authorities (e.g. {@code order:create}).
     *
     * @return required permissions
     */
    String[] value();

    /**
     * How the declared permissions are combined.
     *
     * @return the evaluation mode, {@link CheckMode#ANY_OF} by default
     */
    CheckMode mode() default CheckMode.ANY_OF;
}
