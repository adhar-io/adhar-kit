package com.adhar.kit.ai.guardrail;

import org.springframework.core.Ordered;

/**
 * SPI for a single AI guardrail.
 *
 * <p>A guardrail can screen an outgoing request (throwing to block it) and/or
 * transform an incoming model response (e.g. redacting sensitive data). Guardrails
 * are composed into an ordered {@link GuardrailChain}; lower {@link #getOrder()}
 * values run first.</p>
 *
 * <p>The default chain adapts the checks historically performed by
 * {@code AiSecurityValidator} (content safety, PII, sensitive data). Applications
 * can contribute additional {@code Guardrail} beans, which are automatically
 * discovered and merged into the chain.</p>
 */
public interface Guardrail extends Ordered {

    /** @return a short, unique, human-readable name (used in logs) */
    String getName();

    /**
     * Ordering hint; guardrails with a lower value run earlier. Defaults to
     * {@code 0}.
     */
    @Override
    default int getOrder() {
        return 0;
    }

    /**
     * Validates an outgoing request. Implementations throw (typically a
     * {@link com.adhar.kit.commons.exception.ValidationException}) to block the
     * request. The default is a no-op.
     *
     * @param request the request under inspection
     */
    default void validateRequest(GuardrailRequest request) {
        // no-op by default
    }

    /**
     * Inspects/transforms a model response, returning the (possibly modified)
     * content. The default returns the content unchanged.
     *
     * @param content the model response content
     * @return the content to pass downstream (never used to block; throw from here
     * only for genuinely unrecoverable responses)
     */
    default String validateResponse(String content) {
        return content;
    }

    /**
     * The request under inspection.
     *
     * @param content  the prompt/content being sent to the model
     * @param userId   the requesting user id (may be {@code null})
     * @param tenantId the requesting tenant id (may be {@code null})
     */
    record GuardrailRequest(String content, String userId, String tenantId) {
    }
}
