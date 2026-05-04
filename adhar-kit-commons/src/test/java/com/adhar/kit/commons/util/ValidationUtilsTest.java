package com.adhar.kit.commons.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationUtilsTest {

    // ==================== NULL CHECKS ====================

    @Test
    void requireNonNull_shouldReturnObjectWhenNotNull() {
        String value = "hello";
        assertThat(ValidationUtils.requireNonNull(value, "must not be null")).isEqualTo("hello");
    }

    @Test
    void requireNonNull_shouldThrowWhenNull() {
        assertThatThrownBy(() -> ValidationUtils.requireNonNull(null, "must not be null"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must not be null");
    }

    @Test
    void requireNonNull_withSupplier_shouldThrowWhenNull() {
        assertThatThrownBy(() -> ValidationUtils.requireNonNull(null, () -> "lazy message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lazy message");
    }

    @Test
    void isNull_shouldReturnTrueForNull() {
        assertThat(ValidationUtils.isNull(null)).isTrue();
        assertThat(ValidationUtils.isNull("hello")).isFalse();
    }

    @Test
    void isNotNull_shouldReturnTrueForNonNull() {
        assertThat(ValidationUtils.isNotNull("hello")).isTrue();
        assertThat(ValidationUtils.isNotNull(null)).isFalse();
    }

    // ==================== STRING VALIDATIONS ====================

    @Test
    void requireNonEmpty_string_shouldReturnStringWhenNotEmpty() {
        String result = ValidationUtils.requireNonEmpty("hello", "required");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void requireNonEmpty_string_shouldThrowForNullOrEmpty() {
        assertThatThrownBy(() -> { String s = ValidationUtils.requireNonEmpty((String) null, "required"); })
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> { String s = ValidationUtils.requireNonEmpty("", "required"); })
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> { String s = ValidationUtils.requireNonEmpty("   ", "required"); })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isEmpty_string_shouldCheckCorrectly() {
        assertThat(ValidationUtils.isEmpty((String) null)).isTrue();
        assertThat(ValidationUtils.isEmpty("")).isTrue();
        assertThat(ValidationUtils.isEmpty("   ")).isTrue();
        assertThat(ValidationUtils.isEmpty("hello")).isFalse();
    }

    @Test
    void isNotEmpty_string_shouldCheckCorrectly() {
        assertThat(ValidationUtils.isNotEmpty("hello")).isTrue();
        assertThat(ValidationUtils.isNotEmpty("")).isFalse();
    }

    @Test
    void isBlank_shouldCheckCorrectly() {
        assertThat(ValidationUtils.isBlank(null)).isTrue();
        assertThat(ValidationUtils.isBlank("")).isTrue();
        assertThat(ValidationUtils.isBlank("   ")).isTrue();
        assertThat(ValidationUtils.isBlank("hello")).isFalse();
    }

    @Test
    void isNotBlank_shouldCheckCorrectly() {
        assertThat(ValidationUtils.isNotBlank("hello")).isTrue();
        assertThat(ValidationUtils.isNotBlank(null)).isFalse();
    }

    // ==================== COLLECTION VALIDATIONS ====================

    @Test
    void requireNonEmpty_collection_shouldReturnWhenNotEmpty() {
        List<String> list = List.of("a");
        assertThat(ValidationUtils.requireNonEmpty(list, "required")).isSameAs(list);
    }

    @Test
    void requireNonEmpty_collection_shouldThrowForNullOrEmpty() {
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty((List<?>) null, "required"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty(List.of(), "required"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isEmpty_collection_shouldCheckCorrectly() {
        assertThat(ValidationUtils.isEmpty((List<?>) null)).isTrue();
        assertThat(ValidationUtils.isEmpty(List.of())).isTrue();
        assertThat(ValidationUtils.isEmpty(List.of("a"))).isFalse();
    }

    @Test
    void requireNonEmpty_map_shouldReturnWhenNotEmpty() {
        Map<String, String> map = Map.of("k", "v");
        assertThat(ValidationUtils.requireNonEmpty(map, "required")).isSameAs(map);
    }

    @Test
    void requireNonEmpty_map_shouldThrowForNullOrEmpty() {
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty((Map<?, ?>) null, "required"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty(Map.of(), "required"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== BOOLEAN VALIDATIONS ====================

    @Test
    void requireTrue_shouldPassWhenTrue() {
        ValidationUtils.requireTrue(true, "must be true");
    }

    @Test
    void requireTrue_shouldThrowWhenFalse() {
        assertThatThrownBy(() -> ValidationUtils.requireTrue(false, "must be true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be true");
    }

    @Test
    void requireTrue_withSupplier_shouldThrowWhenFalse() {
        assertThatThrownBy(() -> ValidationUtils.requireTrue(false, () -> "lazy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lazy");
    }

    @Test
    void requireFalse_shouldPassWhenFalse() {
        ValidationUtils.requireFalse(false, "must be false");
    }

    @Test
    void requireFalse_shouldThrowWhenTrue() {
        assertThatThrownBy(() -> ValidationUtils.requireFalse(true, "must be false"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be false");
    }

    // ==================== NUMERIC VALIDATIONS ====================

    @Test
    void requirePositive_shouldReturnWhenPositive() {
        assertThat(ValidationUtils.requirePositive(5, "must be positive")).isEqualTo(5);
    }

    @Test
    void requirePositive_shouldThrowWhenZeroOrNegative() {
        assertThatThrownBy(() -> ValidationUtils.requirePositive(0, "must be positive"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requirePositive(-1, "must be positive"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNonNegative_shouldReturnWhenNonNegative() {
        assertThat(ValidationUtils.requireNonNegative(0, "msg")).isEqualTo(0);
        assertThat(ValidationUtils.requireNonNegative(5, "msg")).isEqualTo(5);
    }

    @Test
    void requireNonNegative_shouldThrowWhenNegative() {
        assertThatThrownBy(() -> ValidationUtils.requireNonNegative(-1, "must not be negative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireInRange_shouldReturnWhenInRange() {
        assertThat(ValidationUtils.requireInRange(5, 1, 10, "out of range")).isEqualTo(5);
        assertThat(ValidationUtils.requireInRange(1, 1, 10, "out of range")).isEqualTo(1);
        assertThat(ValidationUtils.requireInRange(10, 1, 10, "out of range")).isEqualTo(10);
    }

    @Test
    void requireInRange_shouldThrowWhenOutOfRange() {
        assertThatThrownBy(() -> ValidationUtils.requireInRange(0, 1, 10, "out of range"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requireInRange(11, 1, 10, "out of range"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== FORMAT VALIDATIONS ====================

    @Test
    void isValidEmail_shouldValidateCorrectly() {
        assertThat(ValidationUtils.isValidEmail("user@example.com")).isTrue();
        assertThat(ValidationUtils.isValidEmail("user+tag@example.co.uk")).isTrue();
        assertThat(ValidationUtils.isValidEmail("invalid")).isFalse();
        assertThat(ValidationUtils.isValidEmail(null)).isFalse();
        assertThat(ValidationUtils.isValidEmail("")).isFalse();
    }

    @Test
    void requireValidEmail_shouldThrowForInvalid() {
        assertThat(ValidationUtils.requireValidEmail("user@example.com", "bad email"))
                .isEqualTo("user@example.com");
        assertThatThrownBy(() -> ValidationUtils.requireValidEmail("invalid", "bad email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isValidPhone_shouldValidateCorrectly() {
        assertThat(ValidationUtils.isValidPhone("+12025551234")).isTrue();
        assertThat(ValidationUtils.isValidPhone("12025551234")).isTrue();
        assertThat(ValidationUtils.isValidPhone("abc")).isFalse();
        assertThat(ValidationUtils.isValidPhone(null)).isFalse();
    }

    @Test
    void requireValidPhone_shouldThrowForInvalid() {
        assertThatThrownBy(() -> ValidationUtils.requireValidPhone("abc", "bad phone"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isValidUUID_shouldValidateCorrectly() {
        assertThat(ValidationUtils.isValidUUID("550e8400-e29b-41d4-a716-446655440000")).isTrue();
        assertThat(ValidationUtils.isValidUUID("not-a-uuid")).isFalse();
        assertThat(ValidationUtils.isValidUUID(null)).isFalse();
    }

    @Test
    void requireValidUUID_shouldThrowForInvalid() {
        assertThatThrownBy(() -> ValidationUtils.requireValidUUID("bad", "bad uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== STRING LENGTH VALIDATIONS ====================

    @Test
    void requireLength_shouldReturnWhenValid() {
        assertThat(ValidationUtils.requireLength("hello", 1, 10, "bad length")).isEqualTo("hello");
    }

    @Test
    void requireLength_shouldThrowWhenOutOfRange() {
        assertThatThrownBy(() -> ValidationUtils.requireLength("hi", 5, 10, "too short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requireLength("hello world!!", 1, 5, "too long"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireMinLength_shouldThrowWhenTooShort() {
        assertThat(ValidationUtils.requireMinLength("hello", 3, "too short")).isEqualTo("hello");
        assertThatThrownBy(() -> ValidationUtils.requireMinLength("hi", 5, "too short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireMaxLength_shouldThrowWhenTooLong() {
        assertThat(ValidationUtils.requireMaxLength("hi", 5, "too long")).isEqualTo("hi");
        assertThatThrownBy(() -> ValidationUtils.requireMaxLength("hello world", 5, "too long"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== PATTERN VALIDATIONS ====================

    @Test
    void matches_shouldCheckPattern() {
        Pattern digits = Pattern.compile("\\d+");
        assertThat(ValidationUtils.matches("12345", digits)).isTrue();
        assertThat(ValidationUtils.matches("abc", digits)).isFalse();
        assertThat(ValidationUtils.matches(null, digits)).isFalse();
    }

    @Test
    void requireMatches_shouldThrowWhenNoMatch() {
        Pattern digits = Pattern.compile("\\d+");
        assertThat(ValidationUtils.requireMatches("123", digits, "must be digits")).isEqualTo("123");
        assertThatThrownBy(() -> ValidationUtils.requireMatches("abc", digits, "must be digits"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== EQUALITY CHECKS ====================

    @Test
    void requireEquals_shouldPassWhenEqual() {
        ValidationUtils.requireEquals("a", "a", "must be equal");
        ValidationUtils.requireEquals(null, null, "must be equal");
    }

    @Test
    void requireEquals_shouldThrowWhenNotEqual() {
        assertThatThrownBy(() -> ValidationUtils.requireEquals("a", "b", "must be equal"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNotEquals_shouldPassWhenNotEqual() {
        ValidationUtils.requireNotEquals("a", "b", "must not be equal");
    }

    @Test
    void requireNotEquals_shouldThrowWhenEqual() {
        assertThatThrownBy(() -> ValidationUtils.requireNotEquals("a", "a", "must not be equal"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_shouldThrowUnsupportedOperationException() throws Exception {
        var constructor = ValidationUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
