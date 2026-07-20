package com.adhar.kit.commons.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a field is not null or empty.
 *
 * <p>Works with strings, collections, arrays, and optional values.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * public class CreateUserRequest {
 *
 *     @NotNullOrEmpty(message = "Username is required")
 *     private String username;
 *
 *     @NotNullOrEmpty(message = "Email is required")
 *     private String email;
 *
 *     @NotNullOrEmpty(message = "At least one role required")
 *     private List<String> roles;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = NotNullOrEmptyValidator.class)
public @interface NotNullOrEmpty {

    /**
     * The error message.
     */
    String message() default "Must not be null or empty";

    /**
     * Validation groups.
     */
    Class<?>[] groups() default {};

    /**
     * Constraint payload (required by the Jakarta Bean Validation spec).
     */
    Class<? extends Payload>[] payload() default {};
}

