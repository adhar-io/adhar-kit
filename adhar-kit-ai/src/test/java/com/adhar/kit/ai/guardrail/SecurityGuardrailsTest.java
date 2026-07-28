package com.adhar.kit.ai.guardrail;

import com.adhar.kit.ai.security.AiSecurityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the security guardrail adapters delegate to the corresponding
 * {@link AiSecurityValidator} methods and expose the expected ordering.
 */
@ExtendWith(MockitoExtension.class)
class SecurityGuardrailsTest {

    @Mock
    private AiSecurityValidator validator;

    @Test
    void contentSafetyGuardrailDelegates() {
        ContentSafetyGuardrail guardrail = new ContentSafetyGuardrail(validator);
        guardrail.validateRequest(new Guardrail.GuardrailRequest("hello", "u", "t"));

        verify(validator).validateContentSafety("hello");
        assertThat(guardrail.getName()).isEqualTo("content-safety");
        assertThat(guardrail.getOrder()).isEqualTo(ContentSafetyGuardrail.ORDER);
    }

    @Test
    void piiGuardrailDelegatesRequestAndResponse() {
        PiiGuardrail guardrail = new PiiGuardrail(validator);
        guardrail.validateRequest(new Guardrail.GuardrailRequest("a@b.com", "u", "t"));
        verify(validator).validatePiiCompliance("a@b.com");

        when(validator.redactPii("email a@b.com")).thenReturn("email [EMAIL_REDACTED]");
        assertThat(guardrail.validateResponse("email a@b.com")).isEqualTo("email [EMAIL_REDACTED]");
        assertThat(guardrail.getOrder()).isEqualTo(PiiGuardrail.ORDER);
    }

    @Test
    void sensitiveDataGuardrailDelegates() {
        SensitiveDataGuardrail guardrail = new SensitiveDataGuardrail(validator);
        guardrail.validateRequest(new Guardrail.GuardrailRequest("password", "u", "t"));

        verify(validator).validateSensitiveInformation("password");
        assertThat(guardrail.getName()).isEqualTo("sensitive-data");
        assertThat(guardrail.getOrder()).isEqualTo(SensitiveDataGuardrail.ORDER);
    }

    @Test
    void ordersReflectLegacySequence() {
        assertThat(ContentSafetyGuardrail.ORDER)
                .isLessThan(PiiGuardrail.ORDER)
                .isLessThan(SensitiveDataGuardrail.ORDER);
        assertThat(PiiGuardrail.ORDER).isLessThan(SensitiveDataGuardrail.ORDER);
    }
}
