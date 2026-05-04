package com.adhar.kit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdharStringUtils Tests")
class AdharStringUtilsTest {

    @Nested
    @DisplayName("Null-Safe Operations")
    class NullSafeOps {

        @Test
        void isEmpty_trueForNullAndEmpty() {
            assertThat(AdharStringUtils.isEmpty(null)).isTrue();
            assertThat(AdharStringUtils.isEmpty("")).isTrue();
            assertThat(AdharStringUtils.isEmpty("a")).isFalse();
        }

        @Test
        void isNotEmpty_falseForNullAndEmpty() {
            assertThat(AdharStringUtils.isNotEmpty(null)).isFalse();
            assertThat(AdharStringUtils.isNotEmpty("")).isFalse();
            assertThat(AdharStringUtils.isNotEmpty("a")).isTrue();
        }

        @Test
        void isBlank_trueForNullEmptyWhitespace() {
            assertThat(AdharStringUtils.isBlank(null)).isTrue();
            assertThat(AdharStringUtils.isBlank("")).isTrue();
            assertThat(AdharStringUtils.isBlank("   ")).isTrue();
            assertThat(AdharStringUtils.isBlank("a")).isFalse();
        }

        @Test
        void isNotBlank_falseForNullEmptyWhitespace() {
            assertThat(AdharStringUtils.isNotBlank(null)).isFalse();
            assertThat(AdharStringUtils.isNotBlank("text")).isTrue();
        }

        @Test
        void trimToEmpty_returnsEmptyForNull() {
            assertThat(AdharStringUtils.trimToEmpty(null)).isEmpty();
            assertThat(AdharStringUtils.trimToEmpty("  hello  ")).isEqualTo("hello");
        }

        @Test
        void trimToNull_returnsNullForBlank() {
            assertThat(AdharStringUtils.trimToNull(null)).isNull();
            assertThat(AdharStringUtils.trimToNull("   ")).isNull();
            assertThat(AdharStringUtils.trimToNull("  a  ")).isEqualTo("a");
        }

        @Test
        void defaultIfEmpty_returnsDefaultForEmpty() {
            assertThat(AdharStringUtils.defaultIfEmpty(null, "def")).isEqualTo("def");
            assertThat(AdharStringUtils.defaultIfEmpty("", "def")).isEqualTo("def");
            assertThat(AdharStringUtils.defaultIfEmpty("val", "def")).isEqualTo("val");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void isValidEmail_validAndInvalid() {
            assertThat(AdharStringUtils.isValidEmail("user@example.com")).isTrue();
            assertThat(AdharStringUtils.isValidEmail("a+b@test.co")).isTrue();
            assertThat(AdharStringUtils.isValidEmail(null)).isFalse();
            assertThat(AdharStringUtils.isValidEmail("")).isFalse();
            assertThat(AdharStringUtils.isValidEmail("not-an-email")).isFalse();
            assertThat(AdharStringUtils.isValidEmail("@no-user.com")).isFalse();
        }

        @Test
        void isValidPhone_validAndInvalid() {
            assertThat(AdharStringUtils.isValidPhone("1234567890")).isTrue();
            assertThat(AdharStringUtils.isValidPhone("+12345678901")).isTrue();
            assertThat(AdharStringUtils.isValidPhone(null)).isFalse();
            assertThat(AdharStringUtils.isValidPhone("")).isFalse();
        }

        @Test
        void isAlphanumeric_correctResults() {
            assertThat(AdharStringUtils.isAlphanumeric("abc123")).isTrue();
            assertThat(AdharStringUtils.isAlphanumeric("abc 123")).isFalse();
            assertThat(AdharStringUtils.isAlphanumeric(null)).isFalse();
            assertThat(AdharStringUtils.isAlphanumeric("")).isFalse();
        }

        @Test
        void isNumeric_correctResults() {
            assertThat(AdharStringUtils.isNumeric("12345")).isTrue();
            assertThat(AdharStringUtils.isNumeric("12.3")).isFalse();
            assertThat(AdharStringUtils.isNumeric(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Masking")
    class Masking {

        @Test
        void maskEmail_masksCorrectly() {
            assertThat(AdharStringUtils.maskEmail("user@example.com")).isEqualTo("u***@example.com");
            assertThat(AdharStringUtils.maskEmail(null)).isNull();
            assertThat(AdharStringUtils.maskEmail("noemail")).isEqualTo("noemail");
            assertThat(AdharStringUtils.maskEmail("a@b.com")).isEqualTo("a@b.com"); // single char local part
        }

        @Test
        void maskPhone_masksCorrectly() {
            assertThat(AdharStringUtils.maskPhone("1234567890")).isEqualTo("******7890");
            assertThat(AdharStringUtils.maskPhone(null)).isNull();
            assertThat(AdharStringUtils.maskPhone("1234")).isEqualTo("1234"); // too short
        }

        @Test
        void maskCreditCard_masksCorrectly() {
            assertThat(AdharStringUtils.maskCreditCard("4111111111111111"))
                .isEqualTo("**** **** **** 1111");
            assertThat(AdharStringUtils.maskCreditCard(null)).isNull();
            assertThat(AdharStringUtils.maskCreditCard("1234")).isEqualTo("1234");
        }
    }

    @Nested
    @DisplayName("Case Conversion")
    class CaseConversion {

        @Test
        void toCamelCase_convertsCorrectly() {
            assertThat(AdharStringUtils.toCamelCase("hello world")).isEqualTo("helloWorld");
            assertThat(AdharStringUtils.toCamelCase("hello_world")).isEqualTo("helloWorld");
            assertThat(AdharStringUtils.toCamelCase("hello-world")).isEqualTo("helloWorld");
            assertThat(AdharStringUtils.toCamelCase(null)).isNull();
        }

        @Test
        void toSnakeCase_convertsCorrectly() {
            assertThat(AdharStringUtils.toSnakeCase("helloWorld")).isEqualTo("hello_world");
            assertThat(AdharStringUtils.toSnakeCase("hello world")).isEqualTo("hello_world");
            assertThat(AdharStringUtils.toSnakeCase(null)).isNull();
        }

        @Test
        void toKebabCase_convertsCorrectly() {
            assertThat(AdharStringUtils.toKebabCase("helloWorld")).isEqualTo("hello-world");
            assertThat(AdharStringUtils.toKebabCase("hello_world")).isEqualTo("hello-world");
            assertThat(AdharStringUtils.toKebabCase(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Other Utilities")
    class OtherUtilities {

        @Test
        void truncate_truncatesString() {
            assertThat(AdharStringUtils.truncate("hello world", 5)).isEqualTo("hello");
            assertThat(AdharStringUtils.truncate(null, 5)).isNull();
        }

        @Test
        void abbreviate_addsEllipsis() {
            assertThat(AdharStringUtils.abbreviate("a very long string here", 10)).hasSize(10);
            assertThat(AdharStringUtils.abbreviate("a very long string here", 10)).endsWith("...");
        }

        @Test
        void reverse_reversesString() {
            assertThat(AdharStringUtils.reverse("abc")).isEqualTo("cba");
            assertThat(AdharStringUtils.reverse(null)).isNull();
        }

        @Test
        void repeat_repeatsString() {
            assertThat(AdharStringUtils.repeat("ab", 3)).isEqualTo("ababab");
        }

        @Test
        void removeWhitespace_removesAll() {
            assertThat(AdharStringUtils.removeWhitespace("a b c")).isEqualTo("abc");
            assertThat(AdharStringUtils.removeWhitespace(null)).isNull();
        }
    }
}
