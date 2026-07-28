package com.adhar.kit.ai.guardrail;

import com.adhar.kit.ai.security.AiSecurityValidator;

/**
 * Guardrail adapting {@link AiSecurityValidator#validateSensitiveInformation(String)}
 * (sensitive-keyword screening) to the {@link Guardrail} SPI.
 */
public class SensitiveDataGuardrail implements Guardrail {

    /** Order matching the legacy validation sequence (sensitive data runs last). */
    public static final int ORDER = 30;

    private final AiSecurityValidator securityValidator;

    public SensitiveDataGuardrail(AiSecurityValidator securityValidator) {
        this.securityValidator = securityValidator;
    }

    @Override
    public String getName() {
        return "sensitive-data";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void validateRequest(GuardrailRequest request) {
        securityValidator.validateSensitiveInformation(request.content());
    }
}
