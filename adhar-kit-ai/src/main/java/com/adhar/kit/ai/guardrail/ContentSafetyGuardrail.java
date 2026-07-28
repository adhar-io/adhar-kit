package com.adhar.kit.ai.guardrail;

import com.adhar.kit.ai.security.AiSecurityValidator;

/**
 * Guardrail adapting {@link AiSecurityValidator#validateContentSafety(String)}
 * (harmful-content and length screening) to the {@link Guardrail} SPI.
 */
public class ContentSafetyGuardrail implements Guardrail {

    /** Order matching the legacy validation sequence (content safety runs first). */
    public static final int ORDER = 10;

    private final AiSecurityValidator securityValidator;

    public ContentSafetyGuardrail(AiSecurityValidator securityValidator) {
        this.securityValidator = securityValidator;
    }

    @Override
    public String getName() {
        return "content-safety";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void validateRequest(GuardrailRequest request) {
        securityValidator.validateContentSafety(request.content());
    }
}
