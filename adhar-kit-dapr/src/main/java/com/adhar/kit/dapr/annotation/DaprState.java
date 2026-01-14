package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to save state to Dapr state store.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprState(storeName = "statestore", key = "#userId")
 * public User saveUser(String userId, User user) {
 *     return user;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprState {

    /**
     * State store name.
     */
    String storeName() default "statestore";

    /**
     * State key (supports SpEL expressions).
     */
    String key();

    /**
     * Operation type.
     */
    Operation operation() default Operation.SAVE;

    /**
     * State operations.
     */
    enum Operation {
        SAVE, GET, DELETE
    }
}

