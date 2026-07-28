package com.adhar.kit.test.contract;

import java.util.List;

/**
 * Outcome of verifying an actual JSON payload against a contract's expected payload.
 *
 * <p>A result is "matched" when {@link #mismatches()} is empty. Each mismatch is a
 * human-readable line describing where and how the actual payload deviated from the
 * contract (e.g. {@code $.id: expected 1 but was 2}), using a {@code $}-rooted path so a
 * failed assertion points straight at the offending field.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public final class ContractVerificationResult {

    private final List<String> mismatches;

    public ContractVerificationResult(List<String> mismatches) {
        this.mismatches = List.copyOf(mismatches);
    }

    /**
     * Whether the actual payload satisfied the contract (no mismatches).
     */
    public boolean matched() {
        return mismatches.isEmpty();
    }

    /**
     * The list of mismatches, empty when {@link #matched()} is {@code true}.
     */
    public List<String> mismatches() {
        return mismatches;
    }

    /**
     * Throw an {@link AssertionError} describing every mismatch when the payload did not
     * satisfy the contract. A no-op when {@link #matched()} is {@code true}.
     */
    public void assertMatched() {
        if (!matched()) {
            throw new AssertionError("Contract verification failed:\n  " + String.join("\n  ", mismatches));
        }
    }

    @Override
    public String toString() {
        return matched()
                ? "ContractVerificationResult{matched}"
                : "ContractVerificationResult{mismatches=" + mismatches + "}";
    }
}
