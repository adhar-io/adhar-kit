package com.adhar.kit.config.audit;

import java.util.List;

/**
 * Utility for masking configuration values whose keys look secret.
 *
 * <p>Used by audit events and the actuator endpoint so that sensitive values
 * (passwords, tokens, keys, ...) are never logged or exposed in clear text.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class ConfigMasking {

    /**
     * Default substrings (case-insensitive) that mark a key as secret.
     */
    public static final List<String> DEFAULT_SECRET_PATTERNS = List.of(
            "password", "passwd", "secret", "token", "credential",
            "apikey", "api-key", "api.key", "privatekey", "private-key", ".key", "accesskey");

    private static final String MASK = "***";

    private ConfigMasking() {
    }

    /**
     * Determines whether a key should be treated as secret against the default patterns.
     *
     * @param key property key
     * @return {@code true} when the key looks secret
     */
    public static boolean isSecretKey(String key) {
        return isSecretKey(key, DEFAULT_SECRET_PATTERNS);
    }

    /**
     * Determines whether a key should be treated as secret against the given patterns.
     *
     * @param key property key
     * @param patterns case-insensitive substrings marking a key as secret
     * @return {@code true} when the key looks secret
     */
    public static boolean isSecretKey(String key, List<String> patterns) {
        if (key == null || patterns == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isEmpty() && lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Masks the value when the key looks secret (default patterns), otherwise
     * returns the value unchanged.
     *
     * @param key property key
     * @param value property value
     * @return masked or original value
     */
    public static Object maskIfSecret(String key, Object value) {
        return maskIfSecret(key, value, DEFAULT_SECRET_PATTERNS);
    }

    /**
     * Masks the value when the key looks secret (given patterns), otherwise
     * returns the value unchanged.
     *
     * @param key property key
     * @param value property value
     * @param patterns secret key patterns
     * @return masked or original value
     */
    public static Object maskIfSecret(String key, Object value, List<String> patterns) {
        if (value == null) {
            return null;
        }
        return isSecretKey(key, patterns) ? MASK : value;
    }

    /**
     * The literal mask token.
     *
     * @return {@code ***}
     */
    public static String mask() {
        return MASK;
    }
}
