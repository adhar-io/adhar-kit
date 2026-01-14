package com.adhar.kit.commons.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Utility class for validation operations.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Null checks
 * ValidationUtils.requireNonNull(user, "User must not be null");
 * ValidationUtils.requireNonEmpty(username, "Username required");
 *
 * // Business validation
 * ValidationUtils.requireTrue(amount > 0, "Amount must be positive");
 * ValidationUtils.requireFalse(isDuplicate, "Duplicate entry");
 *
 * // Email/Phone validation
 * boolean valid = ValidationUtils.isValidEmail(email);
 * boolean phoneValid = ValidationUtils.isValidPhone(phone);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE
    );

    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== NULL CHECKS ====================

    /**
     * Ensures object is not null.
     *
     * @param obj the object to check
     * @param message error message
     * @param <T> object type
     * @return the object if not null
     * @throws IllegalArgumentException if null
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * Ensures object is not null with supplier message.
     */
    public static <T> T requireNonNull(T obj, Supplier<String> messageSupplier) {
        if (obj == null) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return obj;
    }

    /**
     * Checks if object is null.
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     * Checks if object is not null.
     */
    public static boolean isNotNull(Object obj) {
        return obj != null;
    }

    // ==================== STRING VALIDATIONS ====================

    /**
     * Ensures string is not null or empty.
     */
    public static String requireNonEmpty(String str, String message) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * Checks if string is null or empty.
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if string is not null and not empty.
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Checks if string is blank (null, empty, or whitespace).
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if string is not blank.
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    // ==================== COLLECTION VALIDATIONS ====================

    /**
     * Ensures collection is not null or empty.
     */
    public static <T extends Collection<?>> T requireNonEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * Checks if collection is null or empty.
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks if collection is not null and not empty.
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Ensures map is not null or empty.
     */
    public static <T extends Map<?, ?>> T requireNonEmpty(T map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    // ==================== BOOLEAN VALIDATIONS ====================

    /**
     * Ensures condition is true.
     */
    public static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Ensures condition is true with supplier message.
     */
    public static void requireTrue(boolean condition, Supplier<String> messageSupplier) {
        if (!condition) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }

    /**
     * Ensures condition is false.
     */
    public static void requireFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }

    // ==================== NUMERIC VALIDATIONS ====================

    /**
     * Ensures number is positive.
     */
    public static <T extends Number> T requirePositive(T number, String message) {
        requireNonNull(number, "Number must not be null");
        if (number.doubleValue() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    /**
     * Ensures number is non-negative.
     */
    public static <T extends Number> T requireNonNegative(T number, String message) {
        requireNonNull(number, "Number must not be null");
        if (number.doubleValue() < 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    /**
     * Ensures number is in range.
     */
    public static <T extends Number> T requireInRange(T number, T min, T max, String message) {
        requireNonNull(number, "Number must not be null");
        double value = number.doubleValue();
        if (value < min.doubleValue() || value > max.doubleValue()) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    // ==================== FORMAT VALIDATIONS ====================

    /**
     * Validates email format.
     */
    public static boolean isValidEmail(String email) {
        return isNotEmpty(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Requires valid email.
     */
    public static String requireValidEmail(String email, String message) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException(message);
        }
        return email;
    }

    /**
     * Validates phone number format (E.164).
     */
    public static boolean isValidPhone(String phone) {
        return isNotEmpty(phone) && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Requires valid phone number.
     */
    public static String requireValidPhone(String phone, String message) {
        if (!isValidPhone(phone)) {
            throw new IllegalArgumentException(message);
        }
        return phone;
    }

    /**
     * Validates UUID format.
     */
    public static boolean isValidUUID(String uuid) {
        return isNotEmpty(uuid) && UUID_PATTERN.matcher(uuid).matches();
    }

    /**
     * Requires valid UUID.
     */
    public static String requireValidUUID(String uuid, String message) {
        if (!isValidUUID(uuid)) {
            throw new IllegalArgumentException(message);
        }
        return uuid;
    }

    // ==================== STRING LENGTH VALIDATIONS ====================

    /**
     * Ensures string length is within range.
     */
    public static String requireLength(String str, int minLength, int maxLength, String message) {
        requireNonNull(str, "String must not be null");
        int length = str.length();
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * Ensures string minimum length.
     */
    public static String requireMinLength(String str, int minLength, String message) {
        requireNonNull(str, "String must not be null");
        if (str.length() < minLength) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * Ensures string maximum length.
     */
    public static String requireMaxLength(String str, int maxLength, String message) {
        requireNonNull(str, "String must not be null");
        if (str.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    // ==================== PATTERN VALIDATIONS ====================

    /**
     * Validates string matches pattern.
     */
    public static boolean matches(String str, Pattern pattern) {
        return isNotEmpty(str) && pattern.matcher(str).matches();
    }

    /**
     * Requires string matches pattern.
     */
    public static String requireMatches(String str, Pattern pattern, String message) {
        if (!matches(str, pattern)) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    // ==================== EQUALITY CHECKS ====================

    /**
     * Ensures two objects are equal.
     */
    public static void requireEquals(Object obj1, Object obj2, String message) {
        if (!Objects.equals(obj1, obj2)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Ensures two objects are not equal.
     */
    public static void requireNotEquals(Object obj1, Object obj2, String message) {
        if (Objects.equals(obj1, obj2)) {
            throw new IllegalArgumentException(message);
        }
    }
}

