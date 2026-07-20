package com.adhar.kit.security.annotation;

/**
 * Evaluation mode for {@link RequiresRole} and {@link RequiresPermission}.
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public enum CheckMode {

    /**
     * Access is granted if the user has at least one of the declared values.
     */
    ANY_OF,

    /**
     * Access is granted only if the user has every declared value.
     */
    ALL_OF
}
