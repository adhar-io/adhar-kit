package com.adhar.kit.graphql.validation;

import com.adhar.kit.graphql.validation.InputValidator.ValidationResult;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InputValidator}.
 */
class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }

    static class CreateUser {
        @NotBlank
        String name;
        @Email
        String email;
        @Min(0)
        int age;

        CreateUser(String name, String email, int age) {
            this.name = name;
            this.email = email;
            this.age = age;
        }
    }

    // ---------------- validate ----------------

    @Test
    @DisplayName("valid object yields a valid result")
    void validObject() {
        ValidationResult result = validator.validate(new CreateUser("Alice", "alice@example.com", 30));

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("invalid object collects field errors")
    void invalidObject() {
        ValidationResult result = validator.validate(new CreateUser("", "not-an-email", -5));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors())
                .extracting(InputValidator.FieldError::field)
                .containsExactlyInAnyOrder("name", "email", "age");
    }

    @Test
    @DisplayName("validate throws on null input")
    void validateNull() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    // ---------------- validateField ----------------

    @Test
    @DisplayName("validateField passes for a valid field")
    void validateFieldValid() {
        ValidationResult result =
                validator.validateField(new CreateUser("Alice", "alice@example.com", 30), "name");

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("validateField returns error for an invalid field")
    void validateFieldInvalid() {
        ValidationResult result =
                validator.validateField(new CreateUser("", "alice@example.com", 30), "name");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().field()).isEqualTo("name");
    }

    @Test
    @DisplayName("validateField throws on null input")
    void validateFieldNullInput() {
        assertThatThrownBy(() -> validator.validateField(null, "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("validateField throws on null field name")
    void validateFieldNullName() {
        CreateUser user = new CreateUser("Alice", "alice@example.com", 30);
        assertThatThrownBy(() -> validator.validateField(user, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name");
    }

    @Test
    @DisplayName("validateField throws on blank field name")
    void validateFieldBlankName() {
        CreateUser user = new CreateUser("Alice", "alice@example.com", 30);
        assertThatThrownBy(() -> validator.validateField(user, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name");
    }

    // ---------------- ValidationResult ----------------

    @Test
    @DisplayName("ValidationResult.valid() is an empty valid result")
    void validationResultValidFactory() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("custom validator constructor is used")
    void customValidatorConstructor() {
        InputValidator custom = new InputValidator(
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());

        ValidationResult result = custom.validate(new CreateUser("Bob", "bob@example.com", 1));
        assertThat(result.isValid()).isTrue();
    }
}
