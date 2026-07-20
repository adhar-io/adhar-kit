package com.adhar.kit.commons.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Jakarta Bean Validation {@link ConstraintValidator} backing {@link NotNullOrEmpty}.
 *
 * <p>Supported types and their emptiness rules:</p>
 * <ul>
 *   <li>{@link CharSequence} - must contain at least one character</li>
 *   <li>{@link Collection} - must contain at least one element</li>
 *   <li>{@link Map} - must contain at least one entry</li>
 *   <li>Arrays (object and primitive) - must have length greater than zero</li>
 *   <li>{@link Optional} - must be present</li>
 * </ul>
 *
 * <p>{@code null} values are always invalid. Any other non-null type is considered
 * valid, so the constraint degrades gracefully to a not-null check.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class NotNullOrEmptyValidator implements ConstraintValidator<NotNullOrEmpty, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent();
        }
        // Non-null values of unsupported types are treated as valid (not-null semantics).
        return true;
    }
}
