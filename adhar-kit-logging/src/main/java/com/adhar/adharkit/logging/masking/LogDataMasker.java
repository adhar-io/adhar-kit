package com.adhar.adharkit.logging.masking;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central, reusable masking service for sensitive data in log output.
 *
 * <p>Complements {@link com.adhar.adharkit.logging.encoder.MaskingJsonEncoder} (which masks at the
 * encoder level) by providing programmatic masking for structured payloads before they are handed
 * to the logging pipeline: event metadata, captured HTTP payloads, audit change sets, etc.</p>
 *
 * <p><b>Masking dimensions:</b></p>
 * <ul>
 *   <li><b>Key-based masking:</b> values whose key matches a configured sensitive key
 *       (default set plus {@code adhar.logging.masking.additional-keys}) are masked entirely.</li>
 *   <li><b>Pattern-based masking:</b> free text is scanned for {@code key=value}/{@code key: value}
 *       pairs of sensitive keys, and for well-known PII shapes (credit card numbers, SSNs,
 *       e-mail addresses) plus any custom regular expressions.</li>
 *   <li><b>Strategy:</b> {@link MaskingStrategy#FULL}, {@link MaskingStrategy#PARTIAL} or
 *       {@link MaskingStrategy#HASH} controls how a matched value is replaced.</li>
 * </ul>
 *
 * <p>Thread-safe: all state is immutable after construction.</p>
 */
public class LogDataMasker {

    /** Fixed mask token used by the FULL strategy (and as fallback). */
    public static final String MASK_VALUE = "********";

    private static final Set<String> DEFAULT_MASKED_KEYS = new HashSet<>(Arrays.asList(
            "password", "secret", "token", "authorization", "credential", "creditCard",
            "ssn", "socialSecurity", "accountNumber", "apiKey", "privateKey"
    ));

    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");
    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final boolean enabled;
    private final MaskingStrategy strategy;
    private final Set<String> maskedKeys;
    private final List<Pattern> keyValuePatterns;
    private final List<Pattern> valuePatterns;

    /**
     * Creates a masker configured from the masking section of the logging properties.
     *
     * @param properties the masking configuration
     */
    public LogDataMasker(AdharLoggingProperties.MaskingProperties properties) {
        this.enabled = properties.isEnabled();
        this.strategy = properties.getStrategy() != null ? properties.getStrategy() : MaskingStrategy.FULL;

        Set<String> keys = new HashSet<>(DEFAULT_MASKED_KEYS);
        if (properties.getAdditionalKeys() != null) {
            keys.addAll(properties.getAdditionalKeys());
        }
        this.maskedKeys = Set.copyOf(keys);

        List<Pattern> kvPatterns = new ArrayList<>(maskedKeys.size());
        for (String key : maskedKeys) {
            kvPatterns.add(Pattern.compile(
                    "(\\b" + Pattern.quote(key) + "\\b\\s*[:=]\\s*)[\"']?([^\"',;\\s]+)[\"']?",
                    Pattern.CASE_INSENSITIVE));
        }
        this.keyValuePatterns = List.copyOf(kvPatterns);

        List<Pattern> patterns = new ArrayList<>();
        if (properties.isMaskCreditCards()) {
            patterns.add(CREDIT_CARD_PATTERN);
        }
        if (properties.isMaskSsn()) {
            patterns.add(SSN_PATTERN);
        }
        if (properties.isMaskEmails()) {
            patterns.add(EMAIL_PATTERN);
        }
        if (properties.getCustomPatterns() != null) {
            for (String custom : properties.getCustomPatterns()) {
                if (custom != null && !custom.isBlank()) {
                    patterns.add(Pattern.compile(custom));
                }
            }
        }
        this.valuePatterns = List.copyOf(patterns);
    }

    /**
     * Whether masking is enabled. When disabled all mask methods return their input unchanged.
     *
     * @return true if masking is active
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Checks whether the given key identifies a sensitive field (case-insensitive).
     *
     * @param key the field/MDC/header key
     * @return true if values under this key must be masked
     */
    public boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (String masked : maskedKeys) {
            if (masked.toLowerCase(Locale.ROOT).equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Masks free text: sensitive {@code key=value} pairs and configured PII patterns.
     *
     * @param text the text to mask (may be null)
     * @return the masked text, or the original when masking is disabled or nothing matched
     */
    public String maskText(String text) {
        if (!enabled || text == null || text.isEmpty()) {
            return text;
        }
        String masked = text;
        for (Pattern pattern : keyValuePatterns) {
            masked = pattern.matcher(masked)
                    .replaceAll(m -> m.group(1) + Matcher.quoteReplacement(applyStrategy(m.group(2))));
        }
        for (Pattern pattern : valuePatterns) {
            masked = pattern.matcher(masked)
                    .replaceAll(m -> Matcher.quoteReplacement(applyStrategy(m.group())));
        }
        return masked;
    }

    /**
     * Masks a single value in the context of its key: if the key is sensitive the whole value is
     * masked with the configured strategy, otherwise string values are pattern-masked.
     *
     * @param key   the key the value belongs to (may be null)
     * @param value the value (may be null)
     * @return the masked value
     */
    public Object maskValue(String key, Object value) {
        if (!enabled || value == null) {
            return value;
        }
        if (isSensitiveKey(key)) {
            return applyStrategy(String.valueOf(value));
        }
        if (value instanceof String s) {
            return maskText(s);
        }
        return value;
    }

    /**
     * Returns a deep copy of the given map with sensitive values masked. Nested maps and
     * collections are processed recursively; the original map is never modified.
     *
     * @param data the map to mask (may be null)
     * @return a masked copy, or the original reference when masking is disabled or the map is empty
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> maskMap(Map<String, ?> data) {
        if (!enabled || data == null || data.isEmpty()) {
            return (Map<String, Object>) data;
        }
        Map<String, Object> masked = new LinkedHashMap<>(data.size());
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            masked.put(entry.getKey(), maskNested(entry.getKey(), entry.getValue()));
        }
        return masked;
    }

    private Object maskNested(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return applyStrategy(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nestedKey = String.valueOf(entry.getKey());
                nested.put(nestedKey, maskNested(nestedKey, entry.getValue()));
            }
            return nested;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> nested = new ArrayList<>(collection.size());
            for (Object element : collection) {
                nested.add(maskNested(null, element));
            }
            return nested;
        }
        if (value instanceof String s) {
            return maskText(s);
        }
        return value;
    }

    /**
     * Applies the configured masking strategy to a raw sensitive value.
     *
     * @param value the raw sensitive value
     * @return the replacement string
     */
    public String applyStrategy(String value) {
        if (value == null || value.isEmpty()) {
            return MASK_VALUE;
        }
        return switch (strategy) {
            case FULL -> MASK_VALUE;
            case PARTIAL -> value.length() > 8
                    ? MASK_VALUE + value.substring(value.length() - 4)
                    : MASK_VALUE;
            case HASH -> "sha256:" + sha256Prefix(value);
        };
    }

    private String sha256Prefix(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec; fall back defensively anyway.
            return MASK_VALUE;
        }
    }
}
