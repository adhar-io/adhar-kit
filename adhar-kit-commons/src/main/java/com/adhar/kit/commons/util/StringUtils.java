package com.adhar.kit.commons.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced string utility class providing additional functionality beyond Apache Commons Lang.
 */
public final class StringUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[1-9]\\d{1,14}$"
    );

    // Private constructor to prevent instantiation
    private StringUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace.
     *
     * @param str the string to check
     * @return true if the string is blank
     */
    public static boolean isBlank(String str) {
        return org.apache.commons.lang3.StringUtils.isBlank(str);
    }

    /**
     * Checks if a string is not null, not empty, and contains non-whitespace characters.
     *
     * @param str the string to check
     * @return true if the string is not blank
     */
    public static boolean isNotBlank(String str) {
        return org.apache.commons.lang3.StringUtils.isNotBlank(str);
    }

    /**
     * Returns a default string if the input is null or empty.
     *
     * @param str the string to check
     * @param defaultStr the default string to return
     * @return the original string or the default
     */
    public static String defaultIfBlank(String str, String defaultStr) {
        return org.apache.commons.lang3.StringUtils.defaultIfBlank(str, defaultStr);
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param str the string to capitalize
     * @return the capitalized string
     */
    public static String capitalize(String str) {
        return org.apache.commons.lang3.StringUtils.capitalize(str);
    }

    /**
     * Converts a string to camelCase.
     *
     * @param str the string to convert
     * @return the camelCase string
     */
    public static String toCamelCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        String[] words = str.toLowerCase().split("[\\s_-]+");
        StringBuilder result = new StringBuilder(words[0]);

        for (int i = 1; i < words.length; i++) {
            result.append(capitalize(words[i]));
        }

        return result.toString();
    }

    // ...existing code...

    /**
     * Converts a string to title case.
     *
     * @param str the string to convert
     * @return the title case string
     */
    public static String toTitleCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        return org.apache.commons.lang3.StringUtils.capitalize(str.toLowerCase(Locale.ROOT));
    }

    // ...existing code...
}
