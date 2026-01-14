package com.adhar.kit.core.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * String utility class with common string operations.
 *
 * <p>Provides null-safe string operations, validation, and manipulation utilities.</p>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Null-safe operations
 * boolean isEmpty = AdharStringUtils.isEmpty(str);
 * boolean isNotEmpty = AdharStringUtils.isNotEmpty(str);
 * String trimmed = AdharStringUtils.trimToEmpty(str);
 *
 * // Validation
 * boolean isEmail = AdharStringUtils.isValidEmail(email);
 * boolean isPhone = AdharStringUtils.isValidPhone(phone);
 * boolean isAlphanumeric = AdharStringUtils.isAlphanumeric(str);
 *
 * // Masking
 * String masked = AdharStringUtils.maskEmail("user@example.com");
 * // Returns: "u***@example.com"
 *
 * String maskedPhone = AdharStringUtils.maskPhone("1234567890");
 * // Returns: "******7890"
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@UtilityClass
public class AdharStringUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[(]?[0-9]{1,4}[)]?[-\\s.]?[(]?[0-9]{1,4}[)]?[-\\s.]?[0-9]{1,9}$"
    );

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    // ==================== Null-Safe Operations ====================

    /**
     * Checks if a string is empty or null.
     *
     * @param str the string to check
     * @return true if empty or null
     */
    public static boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * Checks if a string is not empty.
     *
     * @param str the string to check
     * @return true if not empty and not null
     */
    public static boolean isNotEmpty(String str) {
        return StringUtils.isNotEmpty(str);
    }

    /**
     * Checks if a string is blank (empty or whitespace only).
     *
     * @param str the string to check
     * @return true if blank
     */
    public static boolean isBlank(String str) {
        return StringUtils.isBlank(str);
    }

    /**
     * Checks if a string is not blank.
     *
     * @param str the string to check
     * @return true if not blank
     */
    public static boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }

    /**
     * Trims a string, returning empty string if null.
     *
     * @param str the string to trim
     * @return trimmed string or empty string
     */
    public static String trimToEmpty(String str) {
        return StringUtils.trimToEmpty(str);
    }

    /**
     * Trims a string, returning null if empty.
     *
     * @param str the string to trim
     * @return trimmed string or null
     */
    public static String trimToNull(String str) {
        return StringUtils.trimToNull(str);
    }

    /**
     * Returns default value if string is empty.
     *
     * @param str the string to check
     * @param defaultValue the default value
     * @return the string or default value
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return StringUtils.defaultIfEmpty(str, defaultValue);
    }

    // ==================== Validation ====================

    /**
     * Validates if a string is a valid email address.
     *
     * @param email the email to validate
     * @return true if valid email
     */
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validates if a string is a valid phone number.
     *
     * @param phone the phone number to validate
     * @return true if valid phone number
     */
    public static boolean isValidPhone(String phone) {
        if (isBlank(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.replaceAll("\\s", "")).matches();
    }

    /**
     * Checks if a string contains only alphanumeric characters.
     *
     * @param str the string to check
     * @return true if alphanumeric
     */
    public static boolean isAlphanumeric(String str) {
        if (isBlank(str)) {
            return false;
        }
        return ALPHANUMERIC_PATTERN.matcher(str).matches();
    }

    /**
     * Checks if a string is numeric.
     *
     * @param str the string to check
     * @return true if numeric
     */
    public static boolean isNumeric(String str) {
        return StringUtils.isNumeric(str);
    }

    // ==================== Masking ====================

    /**
     * Masks an email address for privacy.
     * Example: user@example.com -> u***@example.com
     *
     * @param email the email to mask
     * @return masked email
     */
    public static String maskEmail(String email) {
        if (isBlank(email) || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        if (parts[0].length() <= 1) {
            return email;
        }

        return parts[0].charAt(0) + "***@" + parts[1];
    }

    /**
     * Masks a phone number for privacy.
     * Shows only last 4 digits.
     *
     * @param phone the phone number to mask
     * @return masked phone number
     */
    public static String maskPhone(String phone) {
        if (isBlank(phone)) {
            return phone;
        }

        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() <= 4) {
            return phone;
        }

        int visibleDigits = 4;
        int maskedDigits = cleaned.length() - visibleDigits;
        return "*".repeat(maskedDigits) + cleaned.substring(maskedDigits);
    }

    /**
     * Masks a credit card number.
     * Shows only last 4 digits.
     *
     * @param cardNumber the card number
     * @return masked card number
     */
    public static String maskCreditCard(String cardNumber) {
        if (isBlank(cardNumber)) {
            return cardNumber;
        }

        String cleaned = cardNumber.replaceAll("[^0-9]", "");
        if (cleaned.length() <= 4) {
            return cardNumber;
        }

        return "**** **** **** " + cleaned.substring(cleaned.length() - 4);
    }

    // ==================== Case Operations ====================

    /**
     * Converts string to camelCase.
     *
     * @param str the string to convert
     * @return camelCase string
     */
    public static String toCamelCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        String[] words = str.split("[\\s_-]+");
        StringBuilder result = new StringBuilder(words[0].toLowerCase());

        for (int i = 1; i < words.length; i++) {
            result.append(StringUtils.capitalize(words[i].toLowerCase()));
        }

        return result.toString();
    }

    /**
     * Converts string to snake_case.
     *
     * @param str the string to convert
     * @return snake_case string
     */
    public static String toSnakeCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        return str.replaceAll("([a-z])([A-Z])", "$1_$2")
                  .replaceAll("[\\s-]+", "_")
                  .toLowerCase();
    }

    /**
     * Converts string to kebab-case.
     *
     * @param str the string to convert
     * @return kebab-case string
     */
    public static String toKebabCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        return str.replaceAll("([a-z])([A-Z])", "$1-$2")
                  .replaceAll("[\\s_]+", "-")
                  .toLowerCase();
    }

    // ==================== Truncation ====================

    /**
     * Truncates a string to the specified length.
     *
     * @param str the string to truncate
     * @param maxLength maximum length
     * @return truncated string
     */
    public static String truncate(String str, int maxLength) {
        return StringUtils.truncate(str, maxLength);
    }

    /**
     * Truncates a string and adds ellipsis.
     *
     * @param str the string to truncate
     * @param maxLength maximum length (including ellipsis)
     * @return truncated string with ellipsis
     */
    public static String abbreviate(String str, int maxLength) {
        return StringUtils.abbreviate(str, maxLength);
    }

    // ==================== Other Utilities ====================

    /**
     * Reverses a string.
     *
     * @param str the string to reverse
     * @return reversed string
     */
    public static String reverse(String str) {
        return StringUtils.reverse(str);
    }

    /**
     * Repeats a string n times.
     *
     * @param str the string to repeat
     * @param count number of repetitions
     * @return repeated string
     */
    public static String repeat(String str, int count) {
        return StringUtils.repeat(str, count);
    }

    /**
     * Removes all whitespace from a string.
     *
     * @param str the string
     * @return string without whitespace
     */
    public static String removeWhitespace(String str) {
        return StringUtils.deleteWhitespace(str);
    }
}

