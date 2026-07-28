package com.adhar.kit.ai.guardrail;

import com.adhar.kit.ai.security.AiSecurityValidator;

/**
 * Guardrail adapting {@link AiSecurityValidator}'s PII handling to the
 * {@link Guardrail} SPI: it blocks requests containing PII (when configured to) and
 * redacts PII from responses.
 */
public class PiiGuardrail implements Guardrail {

    /** Order matching the legacy validation sequence (PII runs after content safety). */
    public static final int ORDER = 20;

    private final AiSecurityValidator securityValidator;

    public PiiGuardrail(AiSecurityValidator securityValidator) {
        this.securityValidator = securityValidator;
    }

    @Override
    public String getName() {
        return "pii";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void validateRequest(GuardrailRequest request) {
        securityValidator.validatePiiCompliance(request.content());
    }

    @Override
    public String validateResponse(String content) {
        // Uses the raw redaction routine (not sanitizeContent) to avoid recursing
        // back through the chain when AiSecurityValidator delegates to it.
        return securityValidator.redactPii(content);
    }
}
