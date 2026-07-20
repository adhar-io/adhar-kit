package com.adhar.kit.security.exception;

/**
 * Thrown by {@link com.adhar.kit.security.aspect.AccessControlAspect} when the current
 * user does not satisfy a {@link com.adhar.kit.security.annotation.RequiresRole} or
 * {@link com.adhar.kit.security.annotation.RequiresPermission} constraint.
 *
 * <p>A dedicated (framework-agnostic) exception is used instead of Spring Security's
 * {@code AccessDeniedException} so that callers of the portable
 * {@link com.adhar.kit.security.api.SecurityService} abstraction do not need a
 * Spring Security dependency to handle authorization failures.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class AccessDeniedException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message.
     *
     * @param message description of the failed access check
     */
    public AccessDeniedException(String message) {
        super(message);
    }
}
