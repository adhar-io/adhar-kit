package com.adhar.kit.analytics.pii;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Redacts sensitive property values before they are sent to PostHog.
 *
 * <p>Two independent mechanisms are applied:</p>
 * <ul>
 *   <li><b>Key-based redaction</b>: any property whose key matches a
 *       configured (case-insensitive) name - e.g. {@code password}, {@code ssn} -
 *       is always redacted, regardless of its value.</li>
 *   <li><b>Pattern-based redaction</b> (optional, on by default): string
 *       values that look like an email address, a US SSN, a credit card
 *       number, or a phone number are redacted even if the key itself looks
 *       innocuous. This is a heuristic and, like any pattern-based PII
 *       detector, can have false positives/negatives - it is a safety net,
 *       not a substitute for not sending PII in the first place.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class PiiScrubber {

    public static final String REDACTED = "***REDACTED***";

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern SSN = Pattern.compile("^\\d{3}-\\d{2}-\\d{4}$");
    private static final Pattern CREDIT_CARD_DIGITS = Pattern.compile("^\\d{13,19}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d[\\d\\-. ]{7,14}\\d$");

    private final Set<String> redactedKeys;
    private final boolean patternDetectionEnabled;

    public PiiScrubber(Collection<String> redactedKeys, boolean patternDetectionEnabled) {
        this.redactedKeys = redactedKeys == null ? Set.of()
                : redactedKeys.stream()
                        .filter(Objects::nonNull)
                        .map(String::toLowerCase)
                        .collect(Collectors.toUnmodifiableSet());
        this.patternDetectionEnabled = patternDetectionEnabled;
    }

    /**
     * Returns a new map with sensitive values replaced by {@link #REDACTED}.
     * Never mutates the input map; a {@code null} or empty input yields an
     * empty map.
     */
    public Map<String, Object> scrub(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && redactedKeys.contains(key.toLowerCase())) {
                result.put(key, REDACTED);
            } else if (patternDetectionEnabled && looksLikePii(value)) {
                result.put(key, REDACTED);
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private boolean looksLikePii(Object value) {
        if (!(value instanceof String s) || s.isBlank()) {
            return false;
        }
        String trimmed = s.trim();
        if (EMAIL.matcher(trimmed).matches() || SSN.matcher(trimmed).matches()) {
            return true;
        }
        String digitsOnly = trimmed.replaceAll("[ -]", "");
        if (CREDIT_CARD_DIGITS.matcher(digitsOnly).matches()) {
            return true;
        }
        return PHONE.matcher(trimmed).matches();
    }
}
