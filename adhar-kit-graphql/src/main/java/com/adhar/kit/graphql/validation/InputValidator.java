package com.adhar.kit.graphql.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Validates GraphQL input objects using Jakarta Validation (Bean Validation) annotations.
 *
 * <p>Provides a convenient way to validate input types annotated with Jakarta
 * constraints such as {@code @NotNull}, {@code @Size}, {@code @Email}, etc.,
 * returning structured validation results suitable for GraphQL error responses.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * InputValidator validator = new InputValidator();
 * ValidationResult result = validator.validate(createUserInput);
 * if (!result.isValid()) {
 *     // return errors to the client
 *     result.errors().forEach(e -> log.warn("{}: {}", e.field(), e.message()));
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class InputValidator {

    private final Validator validator;

    /**
     * Creates an InputValidator using the default Jakarta Validation provider.
     */
    public InputValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    /**
     * Creates an InputValidator with a custom {@link Validator} instance.
     *
     * @param validator the Jakarta Validator to use
     */
    public InputValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Represents a validation error for a specific field.
     *
     * @param field   the name of the invalid field
     * @param message the validation error message
     */
    public record FieldError(String field, String message) {
    }

    /**
     * The result of validating an input object.
     *
     * @param errors the list of field-level validation errors (empty if valid)
     */
    public record ValidationResult(List<FieldError> errors) {

        /**
         * Returns true if the input passed all validation constraints.
         *
         * @return true if there are no validation errors
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Returns a successful validation result with no errors.
         *
         * @return a valid result
         */
        public static ValidationResult valid() {
            return new ValidationResult(Collections.emptyList());
        }
    }

    /**
     * Validates the given input object against its Jakarta Validation annotations.
     *
     * @param input the input object to validate
     * @return a {@link ValidationResult} containing any constraint violations
     * @throws IllegalArgumentException if input is null
     */
    public ValidationResult validate(Object input) {
        if (input == null) {
            throw new IllegalArgumentException("Input to validate must not be null");
        }

        Set<ConstraintViolation<Object>> violations = validator.validate(input);

        if (violations.isEmpty()) {
            log.debug("Input validation passed for type: {}", input.getClass().getSimpleName());
            return ValidationResult.valid();
        }

        List<FieldError> errors = violations.stream()
                .map(v -> new FieldError(
                        v.getPropertyPath().toString(),
                        v.getMessage()))
                .toList();

        log.debug("Input validation failed for type {} with {} error(s)",
                input.getClass().getSimpleName(), errors.size());
        return new ValidationResult(errors);
    }

    /**
     * Validates a single field value against the specified constraint groups.
     *
     * <p>This method validates the given value by checking constraints for the
     * specified property on the input object's class.</p>
     *
     * @param input     the object containing the field
     * @param fieldName the name of the field/property to validate
     * @param groups    optional validation groups to apply
     * @return a {@link ValidationResult} for the specified field
     * @throws IllegalArgumentException if input or fieldName is null
     */
    public ValidationResult validateField(Object input, String fieldName, Class<?>... groups) {
        if (input == null) {
            throw new IllegalArgumentException("Input to validate must not be null");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Field name must not be null or blank");
        }

        Set<ConstraintViolation<Object>> violations =
                validator.validateProperty(input, fieldName, groups);

        if (violations.isEmpty()) {
            return ValidationResult.valid();
        }

        List<FieldError> errors = violations.stream()
                .map(v -> new FieldError(fieldName, v.getMessage()))
                .toList();

        return new ValidationResult(errors);
    }
}
