package com.adhar.kit.ai.guardrail;

import com.adhar.kit.commons.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GuardrailChain} ordering and request/response execution.
 */
class GuardrailChainTest {

    private static Guardrail recording(String name, int order, List<String> log) {
        return new Guardrail() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public void validateRequest(GuardrailRequest request) {
                log.add(name);
            }

            @Override
            public String validateResponse(String content) {
                return content + "|" + name;
            }
        };
    }

    @Test
    void runsRequestGuardrailsInOrder() {
        List<String> log = new ArrayList<>();
        GuardrailChain chain = new GuardrailChain(List.of(
                recording("c", 30, log),
                recording("a", 10, log),
                recording("b", 20, log)));

        chain.validateRequest("content", "user", "tenant");

        assertThat(log).containsExactly("a", "b", "c");
        assertThat(chain.getGuardrails()).extracting(Guardrail::getName).containsExactly("a", "b", "c");
    }

    @Test
    void foldsResponseThroughGuardrailsInOrder() {
        GuardrailChain chain = new GuardrailChain(List.of(
                recording("second", 20, new ArrayList<>()),
                recording("first", 10, new ArrayList<>())));

        assertThat(chain.applyResponse("base")).isEqualTo("base|first|second");
    }

    @Test
    void nullResponseReturnedUnchanged() {
        GuardrailChain chain = new GuardrailChain(List.of(recording("x", 1, new ArrayList<>())));
        assertThat(chain.applyResponse(null)).isNull();
    }

    @Test
    void firstThrowingGuardrailAbortsRequestChain() {
        List<String> log = new ArrayList<>();
        Guardrail blocking = new Guardrail() {
            @Override
            public String getName() {
                return "blocker";
            }

            @Override
            public int getOrder() {
                return 15;
            }

            @Override
            public void validateRequest(GuardrailRequest request) {
                throw new ValidationException("BLOCKED", "nope");
            }
        };

        GuardrailChain chain = new GuardrailChain(List.of(
                recording("before", 10, log),
                blocking,
                recording("after", 20, log)));

        assertThatThrownBy(() -> chain.validateRequest("c", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nope");
        // "before" ran, "after" did not.
        assertThat(log).containsExactly("before");
    }

    @Test
    void emptyChainIsANoOp() {
        GuardrailChain chain = new GuardrailChain(null);
        chain.validateRequest("c", "u", "t");
        assertThat(chain.applyResponse("x")).isEqualTo("x");
        assertThat(chain.getGuardrails()).isEmpty();
    }
}
