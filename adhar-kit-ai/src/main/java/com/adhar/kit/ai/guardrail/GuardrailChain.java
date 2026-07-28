package com.adhar.kit.ai.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered executor for a set of {@link Guardrail}s.
 *
 * <p>On the request path, {@link #validateRequest} runs every guardrail in order;
 * the first one to throw aborts the chain (and the AI call). On the response path,
 * {@link #applyResponse} folds each guardrail's {@link Guardrail#validateResponse}
 * transformation over the content.</p>
 */
@Slf4j
public class GuardrailChain {

    private final List<Guardrail> guardrails;

    /**
     * Creates a chain from the supplied guardrails, sorted by
     * {@link Guardrail#getOrder()} (and {@code @Order}/{@code Ordered} semantics).
     */
    public GuardrailChain(List<Guardrail> guardrails) {
        List<Guardrail> copy = new ArrayList<>(guardrails != null ? guardrails : List.of());
        copy.sort(AnnotationAwareOrderComparator.INSTANCE);
        this.guardrails = List.copyOf(copy);
        log.info("Guardrail chain initialised with {} guardrail(s): {}",
                this.guardrails.size(), this.guardrails.stream().map(Guardrail::getName).toList());
    }

    /** @return the ordered guardrails in this chain */
    public List<Guardrail> getGuardrails() {
        return guardrails;
    }

    /**
     * Runs the request-validation path. Propagates the first guardrail exception.
     */
    public void validateRequest(String content, String userId, String tenantId) {
        Guardrail.GuardrailRequest request = new Guardrail.GuardrailRequest(content, userId, tenantId);
        for (Guardrail guardrail : guardrails) {
            guardrail.validateRequest(request);
        }
    }

    /**
     * Runs the response-transformation path, returning the final content after all
     * guardrails have been applied. A {@code null} input is returned unchanged.
     */
    public String applyResponse(String content) {
        if (content == null) {
            return null;
        }
        String current = content;
        for (Guardrail guardrail : guardrails) {
            current = guardrail.validateResponse(current);
        }
        return current;
    }
}
