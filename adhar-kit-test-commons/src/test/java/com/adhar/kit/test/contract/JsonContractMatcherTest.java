package com.adhar.kit.test.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsonContractMatcher} - pure JSON assertion logic, no I/O or containers.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("JsonContractMatcher Tests")
class JsonContractMatcherTest {

    private final JsonContractMatcher matcher = new JsonContractMatcher();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }

    @Test
    @DisplayName("verify should match when actual contains all expected fields with equal values")
    void testValueMatchSubset() throws Exception {
        JsonNode expected = json("{\"id\":1,\"name\":\"John\"}");
        JsonNode actual = json("{\"id\":1,\"name\":\"John\",\"extra\":\"ignored\"}");

        ContractVerificationResult result = matcher.verify(expected, actual);

        assertTrue(result.matched());
        assertTrue(result.mismatches().isEmpty());
    }

    @Test
    @DisplayName("verify should report a mismatch when a scalar value differs")
    void testValueMismatch() throws Exception {
        JsonNode expected = json("{\"id\":1}");
        JsonNode actual = json("{\"id\":2}");

        ContractVerificationResult result = matcher.verify(expected, actual);

        assertFalse(result.matched());
        assertEquals(1, result.mismatches().size());
        assertTrue(result.mismatches().get(0).contains("$.id"));
    }

    @Test
    @DisplayName("verify should report a missing expected field")
    void testValueMissingField() throws Exception {
        JsonNode expected = json("{\"id\":1,\"name\":\"John\"}");
        JsonNode actual = json("{\"id\":1}");

        ContractVerificationResult result = matcher.verify(expected, actual);

        assertFalse(result.matched());
        assertTrue(result.mismatches().get(0).contains("$.name"));
        assertTrue(result.mismatches().get(0).contains("missing"));
    }

    @Test
    @DisplayName("verify should recurse into nested objects")
    void testNestedObjectMatch() throws Exception {
        JsonNode expected = json("{\"address\":{\"city\":\"NYC\"}}");
        JsonNode actualOk = json("{\"address\":{\"city\":\"NYC\",\"zip\":\"10001\"}}");
        JsonNode actualBad = json("{\"address\":{\"city\":\"LA\"}}");

        assertTrue(matcher.verify(expected, actualOk).matched());
        assertFalse(matcher.verify(expected, actualBad).matched());
    }

    @Test
    @DisplayName("verify should flag when an object is expected but a scalar is found")
    void testExpectedObjectButScalar() throws Exception {
        JsonNode expected = json("{\"address\":{\"city\":\"NYC\"}}");
        JsonNode actual = json("{\"address\":\"nope\"}");

        ContractVerificationResult result = matcher.verify(expected, actual);

        assertFalse(result.matched());
        assertTrue(result.mismatches().get(0).contains("expected object"));
    }

    @Test
    @DisplayName("verify should match arrays element-by-element and flag short arrays")
    void testArrayMatching() throws Exception {
        JsonNode expected = json("{\"roles\":[\"a\",\"b\"]}");
        JsonNode actualOk = json("{\"roles\":[\"a\",\"b\"]}");
        JsonNode actualShort = json("{\"roles\":[\"a\"]}");
        JsonNode actualNotArray = json("{\"roles\":\"a\"}");

        assertTrue(matcher.verify(expected, actualOk).matched());
        assertFalse(matcher.verify(expected, actualShort).matched());
        assertTrue(matcher.verify(expected, actualShort).mismatches().get(0).contains("at least"));
        assertFalse(matcher.verify(expected, actualNotArray).matched());
        assertTrue(matcher.verify(expected, actualNotArray).mismatches().get(0).contains("expected array"));
    }

    @Test
    @DisplayName("verifySchema should match by type ignoring scalar values")
    void testSchemaIgnoresValues() throws Exception {
        JsonNode expected = json("{\"id\":1,\"name\":\"template\"}");
        JsonNode actual = json("{\"id\":999,\"name\":\"anything\"}");

        ContractVerificationResult result = matcher.verifySchema(expected, actual);

        assertTrue(result.matched());
    }

    @Test
    @DisplayName("verifySchema should flag a type mismatch")
    void testSchemaTypeMismatch() throws Exception {
        JsonNode expected = json("{\"id\":1}");
        JsonNode actual = json("{\"id\":\"not-a-number\"}");

        ContractVerificationResult result = matcher.verifySchema(expected, actual);

        assertFalse(result.matched());
        assertTrue(result.mismatches().get(0).contains("expected type"));
    }

    @Test
    @DisplayName("verifySchema should check every actual array element against the template element")
    void testSchemaArrayTemplate() throws Exception {
        JsonNode expected = json("{\"items\":[{\"id\":1}]}");
        JsonNode actualOk = json("{\"items\":[{\"id\":10},{\"id\":20}]}");
        JsonNode actualBad = json("{\"items\":[{\"id\":10},{\"id\":\"x\"}]}");

        assertTrue(matcher.verifySchema(expected, actualOk).matched());
        assertFalse(matcher.verifySchema(expected, actualBad).matched());
    }

    @Test
    @DisplayName("verifySchema should flag missing fields and non-array where array expected")
    void testSchemaMissingAndArrayShape() throws Exception {
        JsonNode expected = json("{\"tags\":[\"x\"],\"name\":\"n\"}");
        JsonNode actual = json("{\"tags\":\"notarray\"}");

        ContractVerificationResult result = matcher.verifySchema(expected, actual);

        assertFalse(result.matched());
        assertEquals(2, result.mismatches().size());
    }
}
