package com.adhar.kit.commons.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringUtilsTest {

    @Test
    void isBlank_shouldReturnTrueForNull() {
        assertThat(StringUtils.isBlank(null)).isTrue();
    }

    @Test
    void isBlank_shouldReturnTrueForEmptyString() {
        assertThat(StringUtils.isBlank("")).isTrue();
    }

    @Test
    void isBlank_shouldReturnTrueForWhitespace() {
        assertThat(StringUtils.isBlank("   ")).isTrue();
    }

    @Test
    void isBlank_shouldReturnFalseForNonBlankString() {
        assertThat(StringUtils.isBlank("hello")).isFalse();
    }

    @Test
    void isNotBlank_shouldReturnTrueForNonBlankString() {
        assertThat(StringUtils.isNotBlank("hello")).isTrue();
    }

    @Test
    void isNotBlank_shouldReturnFalseForNull() {
        assertThat(StringUtils.isNotBlank(null)).isFalse();
    }

    @Test
    void defaultIfBlank_shouldReturnOriginalWhenNotBlank() {
        assertThat(StringUtils.defaultIfBlank("hello", "default")).isEqualTo("hello");
    }

    @Test
    void defaultIfBlank_shouldReturnDefaultWhenBlank() {
        assertThat(StringUtils.defaultIfBlank(null, "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfBlank("", "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfBlank("   ", "default")).isEqualTo("default");
    }

    @Test
    void capitalize_shouldCapitalizeFirstLetter() {
        assertThat(StringUtils.capitalize("hello")).isEqualTo("Hello");
    }

    @Test
    void capitalize_shouldHandleNullAndEmpty() {
        assertThat(StringUtils.capitalize(null)).isNull();
        assertThat(StringUtils.capitalize("")).isEqualTo("");
    }

    @Test
    void toCamelCase_shouldConvertSnakeCase() {
        assertThat(StringUtils.toCamelCase("hello_world")).isEqualTo("helloWorld");
    }

    @Test
    void toCamelCase_shouldConvertKebabCase() {
        assertThat(StringUtils.toCamelCase("hello-world")).isEqualTo("helloWorld");
    }

    @Test
    void toCamelCase_shouldConvertSpaceSeparated() {
        assertThat(StringUtils.toCamelCase("hello world")).isEqualTo("helloWorld");
    }

    @Test
    void toCamelCase_shouldReturnBlankAsIs() {
        assertThat(StringUtils.toCamelCase(null)).isNull();
        assertThat(StringUtils.toCamelCase("")).isEqualTo("");
    }

    @Test
    void toTitleCase_shouldCapitalizeFirstLetterAndLowercaseRest() {
        assertThat(StringUtils.toTitleCase("HELLO")).isEqualTo("Hello");
        assertThat(StringUtils.toTitleCase("hello")).isEqualTo("Hello");
    }

    @Test
    void toTitleCase_shouldReturnBlankAsIs() {
        assertThat(StringUtils.toTitleCase(null)).isNull();
        assertThat(StringUtils.toTitleCase("")).isEqualTo("");
    }

    @Test
    void constructor_shouldThrowUnsupportedOperationException() throws Exception {
        var constructor = StringUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
