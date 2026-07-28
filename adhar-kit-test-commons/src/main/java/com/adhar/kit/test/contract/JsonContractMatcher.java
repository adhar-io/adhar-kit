package com.adhar.kit.test.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pragmatic JSON contract matcher - the assertion engine behind {@code ContractTestBase} and the
 * provider-side of {@code contract} tests, with no Pact (or any other contract framework) on the
 * classpath.
 *
 * <p>Two matching modes are offered:</p>
 * <ul>
 *   <li>{@link #verify(JsonNode, JsonNode)} - <b>value</b> matching. The expected payload is treated
 *       as a subset template: every field present in the expected payload must exist in the actual
 *       payload with an equal value. Fields present only in the actual payload are ignored, so a
 *       provider may return extra fields without breaking the contract.</li>
 *   <li>{@link #verifySchema(JsonNode, JsonNode)} - <b>schema</b> matching. Only structure and JSON
 *       type are checked: every expected field must be present with the same type category
 *       (object/array/string/number/boolean/null), but scalar values are not compared. Useful when
 *       the contract fixes the shape of a response but the concrete values are dynamic.</li>
 * </ul>
 *
 * <p>Object matching is recursive; arrays are matched element-by-element by index, and the actual
 * array must have at least as many elements as the expected one.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public final class JsonContractMatcher {

    /**
     * Value matching: every field in {@code expected} must be present in {@code actual} with an
     * equal value. See the class javadoc for the exact semantics.
     */
    public ContractVerificationResult verify(JsonNode expected, JsonNode actual) {
        List<String> mismatches = new ArrayList<>();
        matchValues("$", expected, actual, mismatches);
        return new ContractVerificationResult(mismatches);
    }

    /**
     * Schema matching: every field in {@code expected} must be present in {@code actual} with the
     * same JSON type, but scalar values are not compared.
     */
    public ContractVerificationResult verifySchema(JsonNode expected, JsonNode actual) {
        List<String> mismatches = new ArrayList<>();
        matchSchema("$", expected, actual, mismatches);
        return new ContractVerificationResult(mismatches);
    }

    private void matchValues(String path, JsonNode expected, JsonNode actual, List<String> mismatches) {
        if (actual == null || actual.isMissingNode()) {
            mismatches.add(path + ": expected present but was missing");
            return;
        }
        if (expected.isObject()) {
            if (!actual.isObject()) {
                mismatches.add(path + ": expected object but was " + typeOf(actual));
                return;
            }
            for (Iterator<String> it = expected.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                matchValues(path + "." + field, expected.get(field), actual.get(field), mismatches);
            }
        } else if (expected.isArray()) {
            if (!actual.isArray()) {
                mismatches.add(path + ": expected array but was " + typeOf(actual));
                return;
            }
            if (actual.size() < expected.size()) {
                mismatches.add(path + ": expected at least " + expected.size() + " element(s) but was " + actual.size());
            }
            for (int i = 0; i < expected.size() && i < actual.size(); i++) {
                matchValues(path + "[" + i + "]", expected.get(i), actual.get(i), mismatches);
            }
        } else if (!expected.equals(actual)) {
            mismatches.add(path + ": expected " + expected + " but was " + actual);
        }
    }

    private void matchSchema(String path, JsonNode expected, JsonNode actual, List<String> mismatches) {
        if (actual == null || actual.isMissingNode()) {
            mismatches.add(path + ": expected present but was missing");
            return;
        }
        if (expected.isObject()) {
            if (!actual.isObject()) {
                mismatches.add(path + ": expected object but was " + typeOf(actual));
                return;
            }
            for (Iterator<String> it = expected.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                matchSchema(path + "." + field, expected.get(field), actual.get(field), mismatches);
            }
        } else if (expected.isArray()) {
            if (!actual.isArray()) {
                mismatches.add(path + ": expected array but was " + typeOf(actual));
                return;
            }
            if (!expected.isEmpty() && !actual.isEmpty()) {
                JsonNode elementTemplate = expected.get(0);
                for (int i = 0; i < actual.size(); i++) {
                    matchSchema(path + "[" + i + "]", elementTemplate, actual.get(i), mismatches);
                }
            }
        } else if (!sameTypeCategory(expected, actual)) {
            mismatches.add(path + ": expected type " + typeOf(expected) + " but was " + typeOf(actual));
        }
    }

    private boolean sameTypeCategory(JsonNode a, JsonNode b) {
        if (a.isNumber()) {
            return b.isNumber();
        }
        if (a.isTextual()) {
            return b.isTextual();
        }
        if (a.isBoolean()) {
            return b.isBoolean();
        }
        if (a.isNull()) {
            return b.isNull();
        }
        return a.getNodeType() == b.getNodeType();
    }

    private String typeOf(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "missing";
        }
        return node.getNodeType().name().toLowerCase();
    }
}
