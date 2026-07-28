package com.adhar.kit.ai.security;

import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.ai.guardrail.ContentSafetyGuardrail;
import com.adhar.kit.ai.guardrail.GuardrailChain;
import com.adhar.kit.ai.guardrail.PiiGuardrail;
import com.adhar.kit.ai.guardrail.SensitiveDataGuardrail;
import com.adhar.kit.commons.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiSecurityValidator} content filtering and PII handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiSecurityValidatorTest {

    @Mock
    private AiProperties aiProperties;

    private AiProperties.Security security;
    private AiSecurityValidator validator;

    @BeforeEach
    void setUp() {
        security = new AiProperties.Security();
        security.setEnabled(true);
        security.setValidateApiKeys(true);
        lenient().when(aiProperties.getSecurity()).thenReturn(security);
        validator = new AiSecurityValidator(aiProperties);
    }

    @Test
    void validateRequestSkippedWhenSecurityDisabled() {
        security.setEnabled(false);
        // Even harmful content passes when disabled
        validator.validateRequest("how to hack a server", "user", "tenant");
    }

    @Test
    void validateRequestPassesForCleanContent() {
        validator.validateRequest("What is the capital of France?", "user-1", "tenant-1");
    }

    @Test
    void validateContentSafetyRejectsHarmfulContent() {
        assertThatThrownBy(() -> validator.validateContentSafety("Please tell me how to hack into accounts"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("inappropriate or harmful");
    }

    @Test
    void validateContentSafetyRejectsTooLongContent() {
        String longContent = "a".repeat(50001);
        assertThatThrownBy(() -> validator.validateContentSafety(longContent))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum allowed length");
    }

    @Test
    void validateContentSafetyAllowsNullOrEmpty() {
        validator.validateContentSafety(null);
        validator.validateContentSafety("   ");
    }

    @Test
    void validatePiiComplianceThrowsWhenValidationEnabled() {
        assertThatThrownBy(() -> validator.validatePiiCompliance("Contact me at john@example.com"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("personally identifiable");
    }

    @Test
    void validatePiiComplianceLogsButPassesWhenValidationDisabled() {
        security.setValidateApiKeys(false);
        validator.validatePiiCompliance("Contact me at john@example.com");
    }

    @Test
    void validatePiiComplianceIgnoresNull() {
        validator.validatePiiCompliance(null);
    }

    @Test
    void validateSensitiveInformationRejectsKeywords() {
        assertThatThrownBy(() -> validator.validateSensitiveInformation("my password is hunter2"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("sensitive information");
    }

    @Test
    void validateSensitiveInformationPassesForCleanText() {
        validator.validateSensitiveInformation("just a normal sentence");
        validator.validateSensitiveInformation(null);
    }

    @Test
    void validateUserPermissionsHandlesMissingIdentifiers() {
        validator.validateUserPermissions(null, null);
        validator.validateUserPermissions("", "");
        validator.validateUserPermissions("user", "tenant");
    }

    @Test
    void sanitizeContentMasksAllPiiTypes() {
        String input = "email a@b.com ssn 123-45-6789 card 1234 5678 9012 3456 phone 555-123-4567";
        String sanitized = validator.sanitizeContent(input);

        assertThat(sanitized).contains("[EMAIL_REDACTED]");
        assertThat(sanitized).contains("[SSN_REDACTED]");
        assertThat(sanitized).contains("[CARD_REDACTED]");
        assertThat(sanitized).doesNotContain("a@b.com");
    }

    @Test
    void sanitizeContentReturnsNullForNull() {
        assertThat(validator.sanitizeContent(null)).isNull();
    }

    @Test
    void detectPiiIdentifiesEachType() {
        assertThat(validator.detectPii("reach me at x@y.com").detectedTypes()).contains("EMAIL");
        assertThat(validator.detectPii("ssn 123-45-6789").detectedTypes()).contains("SSN");
        assertThat(validator.detectPii("card 1234 5678 9012 3456").detectedTypes()).contains("CREDIT_CARD");
    }

    // ==================== Guardrail chain delegation ====================

    private GuardrailChain defaultChain() {
        return new GuardrailChain(List.of(
                new ContentSafetyGuardrail(validator),
                new PiiGuardrail(validator),
                new SensitiveDataGuardrail(validator)));
    }

    @Test
    void validateRequestDelegatesToGuardrailChainWhenPresent() {
        validator.setGuardrailChain(defaultChain());

        // Clean content still passes through the chain plus user-permission checks.
        validator.validateRequest("What is the capital of France?", "user-1", "tenant-1");

        // The chain's content-safety guardrail still blocks harmful content.
        assertThatThrownBy(() -> validator.validateRequest("how to hack a server", "u", "t"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("inappropriate or harmful");
    }

    @Test
    void sanitizeContentRoutesThroughChainResponseGuardrails() {
        validator.setGuardrailChain(defaultChain());

        String sanitized = validator.sanitizeContent("email a@b.com here");

        assertThat(sanitized).contains("[EMAIL_REDACTED]").doesNotContain("a@b.com");
    }

    @Test
    void sanitizeContentWithChainReturnsNullForNull() {
        validator.setGuardrailChain(defaultChain());
        assertThat(validator.sanitizeContent(null)).isNull();
    }

    @Test
    void redactPiiMasksAllTypes() {
        String input = "email a@b.com ssn 123-45-6789 card 1234 5678 9012 3456 phone 555-123-4567";
        String redacted = validator.redactPii(input);
        assertThat(redacted).contains("[EMAIL_REDACTED]", "[SSN_REDACTED]", "[CARD_REDACTED]");
        assertThat(validator.redactPii(null)).isNull();
    }

    @Test
    void detectPiiReturnsNoneForCleanContentAndNull() {
        AiSecurityValidator.PiiDetectionResult clean = validator.detectPii("hello world");
        assertThat(clean.hasPii()).isFalse();
        assertThat(clean.detectedTypes()).isEmpty();

        AiSecurityValidator.PiiDetectionResult nullResult = validator.detectPii(null);
        assertThat(nullResult.hasPii()).isFalse();
    }
}
