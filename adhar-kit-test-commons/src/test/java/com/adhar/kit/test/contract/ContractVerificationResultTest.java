package com.adhar.kit.test.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ContractVerificationResult}.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("ContractVerificationResult Tests")
class ContractVerificationResultTest {

    @Test
    @DisplayName("empty mismatches means matched and assertMatched is a no-op")
    void testMatched() {
        ContractVerificationResult result = new ContractVerificationResult(List.of());

        assertTrue(result.matched());
        assertDoesNotThrow(result::assertMatched);
        assertTrue(result.toString().contains("matched"));
    }

    @Test
    @DisplayName("non-empty mismatches means not matched and assertMatched throws with details")
    void testNotMatched() {
        ContractVerificationResult result = new ContractVerificationResult(List.of("$.id: expected 1 but was 2"));

        assertFalse(result.matched());
        AssertionError error = assertThrows(AssertionError.class, result::assertMatched);
        assertTrue(error.getMessage().contains("$.id"));
        assertTrue(result.toString().contains("$.id"));
    }

    @Test
    @DisplayName("mismatches list is an immutable copy")
    void testImmutableMismatches() {
        ContractVerificationResult result = new ContractVerificationResult(List.of("a"));
        assertThrows(UnsupportedOperationException.class, () -> result.mismatches().add("b"));
    }
}
