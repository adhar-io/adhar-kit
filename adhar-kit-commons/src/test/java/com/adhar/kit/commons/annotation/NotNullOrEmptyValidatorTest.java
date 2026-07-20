package com.adhar.kit.commons.annotation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotNullOrEmptyValidatorTest {

    private final NotNullOrEmptyValidator validator = new NotNullOrEmptyValidator();

    @Test
    void nullValue_shouldBeInvalid() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void strings_shouldValidateEmptiness() {
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid("value", null)).isTrue();
        assertThat(validator.isValid(new StringBuilder("x"), null)).isTrue();
        assertThat(validator.isValid(new StringBuilder(), null)).isFalse();
    }

    @Test
    void collections_shouldValidateEmptiness() {
        assertThat(validator.isValid(List.of(), null)).isFalse();
        assertThat(validator.isValid(List.of("a"), null)).isTrue();
        assertThat(validator.isValid(Set.of(), null)).isFalse();
        assertThat(validator.isValid(Set.of(1), null)).isTrue();
    }

    @Test
    void maps_shouldValidateEmptiness() {
        assertThat(validator.isValid(Map.of(), null)).isFalse();
        assertThat(validator.isValid(Map.of("k", "v"), null)).isTrue();
    }

    @Test
    void objectArrays_shouldValidateLength() {
        assertThat(validator.isValid(new String[0], null)).isFalse();
        assertThat(validator.isValid(new String[]{"a"}, null)).isTrue();
    }

    @Test
    void primitiveArrays_shouldValidateLength() {
        assertThat(validator.isValid(new int[0], null)).isFalse();
        assertThat(validator.isValid(new int[]{1}, null)).isTrue();
    }

    @Test
    void optionals_shouldValidatePresence() {
        assertThat(validator.isValid(Optional.empty(), null)).isFalse();
        assertThat(validator.isValid(Optional.of("x"), null)).isTrue();
    }

    @Test
    void otherNonNullTypes_shouldBeValid() {
        assertThat(validator.isValid(42, null)).isTrue();
        assertThat(validator.isValid(new Object(), null)).isTrue();
    }

    @Test
    void annotation_shouldDeclareThisValidator() {
        jakarta.validation.Constraint constraint =
            NotNullOrEmpty.class.getAnnotation(jakarta.validation.Constraint.class);
        assertThat(constraint).isNotNull();
        assertThat(constraint.validatedBy()).containsExactly(NotNullOrEmptyValidator.class);
    }
}
