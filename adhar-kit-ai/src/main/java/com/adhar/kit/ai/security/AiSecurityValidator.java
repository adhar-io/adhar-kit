package com.adhar.kit.ai.security;

import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.commons.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Security validator for AI requests.
 * Implements content filtering, PII detection, and security policies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSecurityValidator {

    private final AiProperties aiProperties;

    // Common PII patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-?\\d{2}-?\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b");

    // Sensitive keywords
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
        "password", "secret", "token", "api_key", "private_key",
        "confidential", "restricted", "classified"
    );

    /**
     * Validates AI request for security compliance.
     */
    public void validateRequest(String content, String userId, String tenantId) {
        if (!aiProperties.getSecurity().getEnabled()) {
            return;
        }

        validateContentSafety(content);
        validatePiiCompliance(content);
        validateSensitiveInformation(content);
        validateUserPermissions(userId, tenantId);

        log.debug("Security validation passed for user: {}, tenant: {}", userId, tenantId);
    }

    /**
     * Validates content for safety and appropriateness.
     */
    public void validateContentSafety(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        // Check for harmful content patterns
        if (containsHarmfulContent(content)) {
            log.warn("Harmful content detected in AI request");
            throw new ValidationException("CONTENT_VIOLATION",
                "Request contains inappropriate or harmful content");
        }

        // Check content length
        if (content.length() > 50000) {
            throw new ValidationException("CONTENT_TOO_LONG",
                "Request content exceeds maximum allowed length");
        }
    }

    /**
     * Validates PII compliance.
     */
    public void validatePiiCompliance(String content) {
        if (content == null) return;

        PiiDetectionResult piiResult = detectPii(content);

        if (piiResult.hasPii()) {
            log.warn("PII detected in AI request: {}", piiResult);

            if (aiProperties.getSecurity().getValidateApiKeys()) {
                throw new ValidationException("PII_DETECTED",
                    "Request contains personally identifiable information (PII)");
            } else {
                log.warn("PII detected but validation is disabled");
            }
        }
    }

    /**
     * Validates sensitive information.
     */
    public void validateSensitiveInformation(String content) {
        if (content == null) return;

        String lowerContent = content.toLowerCase();

        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerContent.contains(keyword)) {
                log.warn("Sensitive keyword detected: {}", keyword);
                throw new ValidationException("SENSITIVE_CONTENT",
                    "Request contains sensitive information");
            }
        }
    }

    /**
     * Validates user permissions for AI operations.
     */
    public void validateUserPermissions(String userId, String tenantId) {
        // This would integrate with your authorization system
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("AI request without user identification");
            // Could throw exception or just log depending on requirements
        }

        if (tenantId == null || tenantId.trim().isEmpty()) {
            log.warn("AI request without tenant identification");
        }
    }

    /**
     * Sanitizes content by removing or masking PII.
     */
    public String sanitizeContent(String content) {
        if (content == null) return null;

        String sanitized = content;

        // Mask email addresses
        sanitized = EMAIL_PATTERN.matcher(sanitized)
            .replaceAll("[EMAIL_REDACTED]");

        // Mask SSNs
        sanitized = SSN_PATTERN.matcher(sanitized)
            .replaceAll("[SSN_REDACTED]");

        // Mask credit cards
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized)
            .replaceAll("[CARD_REDACTED]");

        // Mask phone numbers
        sanitized = PHONE_PATTERN.matcher(sanitized)
            .replaceAll("[PHONE_REDACTED]");

        return sanitized;
    }

    /**
     * Detects PII in content.
     */
    public PiiDetectionResult detectPii(String content) {
        if (content == null) {
            return new PiiDetectionResult(false, List.of());
        }

        List<String> detectedTypes = new java.util.ArrayList<>();

        if (EMAIL_PATTERN.matcher(content).find()) {
            detectedTypes.add("EMAIL");
        }
        if (SSN_PATTERN.matcher(content).find()) {
            detectedTypes.add("SSN");
        }
        if (CREDIT_CARD_PATTERN.matcher(content).find()) {
            detectedTypes.add("CREDIT_CARD");
        }
        if (PHONE_PATTERN.matcher(content).find()) {
            detectedTypes.add("PHONE");
        }

        return new PiiDetectionResult(!detectedTypes.isEmpty(), detectedTypes);
    }

    private boolean containsHarmfulContent(String content) {
        String lowerContent = content.toLowerCase();

        // Basic harmful content patterns
        List<String> harmfulPatterns = List.of(
            "how to hack", "create virus", "illegal activities",
            "harmful instructions", "dangerous chemicals"
        );

        return harmfulPatterns.stream()
            .anyMatch(lowerContent::contains);
    }

    public record PiiDetectionResult(boolean hasPii, List<String> detectedTypes) {}
}
