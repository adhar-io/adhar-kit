package com.adhar.kit.analytics.flag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalFlagEvaluator Tests")
class LocalFlagEvaluatorTest {

    private LocalFlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new LocalFlagEvaluator();
    }

    private FlagDefinition.Group group(Integer rollout, FlagDefinition.Condition... conditions) {
        return new FlagDefinition.Group(List.of(conditions), rollout, null);
    }

    @Test
    @DisplayName("unknown flag cannot be evaluated locally -> empty (fallback)")
    void unknownFlagFallsBack() {
        assertTrue(evaluator.evaluate("missing", "user-1", Map.of()).isEmpty());
    }

    @Test
    @DisplayName("null key or distinctId -> empty")
    void nullArgsFallBack() {
        evaluator.addDefinition(new FlagDefinition("f", true, List.of(group(100)), List.of()));
        assertTrue(evaluator.evaluate(null, "u", Map.of()).isEmpty());
        assertTrue(evaluator.evaluate("f", null, Map.of()).isEmpty());
    }

    @Test
    @DisplayName("inactive flag evaluates to disabled locally")
    void inactiveFlagDisabled() {
        evaluator.addDefinition(new FlagDefinition("f", false, List.of(group(100)), List.of()));
        Optional<LocalFlagEvaluator.FlagEvaluation> result = evaluator.evaluate("f", "u", Map.of());
        assertTrue(result.isPresent());
        assertFalse(result.get().enabled());
        assertNull(result.get().value());
    }

    @Test
    @DisplayName("rollout 100% with no conditions is always enabled")
    void rollout100AlwaysEnabled() {
        evaluator.addDefinition(new FlagDefinition("f", true, List.of(group(100)), List.of()));
        LocalFlagEvaluator.FlagEvaluation eval = evaluator.evaluate("f", "anybody", Map.of()).orElseThrow();
        assertTrue(eval.enabled());
        assertEquals(Boolean.TRUE, eval.value());
    }

    @Test
    @DisplayName("null rollout percentage defaults to 100%")
    void nullRolloutDefaultsTo100() {
        evaluator.addDefinition(new FlagDefinition("f", true, List.of(group(null)), List.of()));
        assertTrue(evaluator.evaluate("f", "anybody", Map.of()).orElseThrow().enabled());
    }

    @Test
    @DisplayName("rollout 0% is always disabled")
    void rollout0AlwaysDisabled() {
        evaluator.addDefinition(new FlagDefinition("f", true, List.of(group(0)), List.of()));
        LocalFlagEvaluator.FlagEvaluation eval = evaluator.evaluate("f", "anybody", Map.of()).orElseThrow();
        assertFalse(eval.enabled());
    }

    @Test
    @DisplayName("exact property condition (scalar and list membership)")
    void exactCondition() {
        evaluator.addDefinition(new FlagDefinition("f", true,
                List.of(group(100, new FlagDefinition.Condition("plan", "premium", "exact"))), List.of()));
        assertTrue(evaluator.evaluate("f", "u", Map.of("plan", "premium")).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("f", "u", Map.of("plan", "free")).orElseThrow().enabled());

        evaluator.addDefinition(new FlagDefinition("g", true,
                List.of(group(100, new FlagDefinition.Condition("country", List.of("US", "CA"), "exact"))), List.of()));
        assertTrue(evaluator.evaluate("g", "u", Map.of("country", "CA")).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("g", "u", Map.of("country", "UK")).orElseThrow().enabled());
    }

    @Test
    @DisplayName("is_not, icontains and numeric comparison operators")
    void otherOperators() {
        evaluator.addDefinition(new FlagDefinition("isnot", true,
                List.of(group(100, new FlagDefinition.Condition("plan", "free", "is_not"))), List.of()));
        assertTrue(evaluator.evaluate("isnot", "u", Map.of("plan", "premium")).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("isnot", "u", Map.of("plan", "free")).orElseThrow().enabled());

        evaluator.addDefinition(new FlagDefinition("contains", true,
                List.of(group(100, new FlagDefinition.Condition("email", "@acme.com", "icontains"))), List.of()));
        assertTrue(evaluator.evaluate("contains", "u", Map.of("email", "bob@ACME.com")).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("contains", "u", Map.of("email", "bob@other.com")).orElseThrow().enabled());

        evaluator.addDefinition(new FlagDefinition("age", true,
                List.of(group(100, new FlagDefinition.Condition("age", 18, "gte"))), List.of()));
        assertTrue(evaluator.evaluate("age", "u", Map.of("age", 21)).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("age", "u", Map.of("age", 17)).orElseThrow().enabled());
        assertTrue(evaluator.evaluate("age", "u", Map.of("age", 18)).orElseThrow().enabled());

        evaluator.addDefinition(new FlagDefinition("agelt", true,
                List.of(group(100, new FlagDefinition.Condition("age", 18, "lt"))), List.of()));
        assertTrue(evaluator.evaluate("agelt", "u", Map.of("age", 17)).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("agelt", "u", Map.of("age", 18)).orElseThrow().enabled());
    }

    @Test
    @DisplayName("is_set / is_not_set are decidable even when the property is absent")
    void setOperators() {
        evaluator.addDefinition(new FlagDefinition("isset", true,
                List.of(group(100, new FlagDefinition.Condition("email", null, "is_set"))), List.of()));
        assertTrue(evaluator.evaluate("isset", "u", Map.of("email", "x@y.com")).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("isset", "u", Map.of()).orElseThrow().enabled());

        evaluator.addDefinition(new FlagDefinition("notset", true,
                List.of(group(100, new FlagDefinition.Condition("email", null, "is_not_set"))), List.of()));
        assertTrue(evaluator.evaluate("notset", "u", Map.of()).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("notset", "u", Map.of("email", "x@y.com")).orElseThrow().enabled());
    }

    @Test
    @DisplayName("missing property for a value-based condition -> empty (fallback to /decide)")
    void missingPropertyFallsBack() {
        evaluator.addDefinition(new FlagDefinition("f", true,
                List.of(group(100, new FlagDefinition.Condition("plan", "premium", "exact"))), List.of()));
        assertTrue(evaluator.evaluate("f", "u", Map.of("other", "x")).isEmpty());
    }

    @Test
    @DisplayName("unsupported operator -> empty (fallback)")
    void unsupportedOperatorFallsBack() {
        evaluator.addDefinition(new FlagDefinition("f", true,
                List.of(group(100, new FlagDefinition.Condition("plan", "x", "regex"))), List.of()));
        assertTrue(evaluator.evaluate("f", "u", Map.of("plan", "x")).isEmpty());
    }

    @Test
    @DisplayName("non-numeric comparison -> empty (fallback)")
    void nonNumericComparisonFallsBack() {
        evaluator.addDefinition(new FlagDefinition("f", true,
                List.of(group(100, new FlagDefinition.Condition("age", 18, "gt"))), List.of()));
        assertTrue(evaluator.evaluate("f", "u", Map.of("age", "not-a-number")).isEmpty());
    }

    @Test
    @DisplayName("a later group can enable the flag after an earlier group fails to match")
    void multipleGroups() {
        FlagDefinition def = new FlagDefinition("f", true, List.of(
                group(100, new FlagDefinition.Condition("plan", "gold", "exact")),
                group(100, new FlagDefinition.Condition("plan", "premium", "exact"))
        ), List.of());
        evaluator.addDefinition(def);
        assertTrue(evaluator.evaluate("f", "u", Map.of("plan", "premium")).orElseThrow().enabled());
    }

    @Test
    @DisplayName("group variant override pins matched users to that variant")
    void groupVariantOverride() {
        FlagDefinition def = new FlagDefinition("f", true,
                List.of(new FlagDefinition.Group(List.of(), 100, "test-variant")),
                List.of(new FlagDefinition.Variant("control", 50), new FlagDefinition.Variant("test", 50)));
        evaluator.addDefinition(def);
        LocalFlagEvaluator.FlagEvaluation eval = evaluator.evaluate("f", "u", Map.of()).orElseThrow();
        assertTrue(eval.enabled());
        assertEquals("test-variant", eval.value());
    }

    @Test
    @DisplayName("multivariate flags resolve a deterministic variant from the variant hash")
    void multivariateVariant() {
        FlagDefinition def = new FlagDefinition("f", true, List.of(group(100)),
                List.of(new FlagDefinition.Variant("control", 50), new FlagDefinition.Variant("test", 50)));
        evaluator.addDefinition(def);

        LocalFlagEvaluator.FlagEvaluation first = evaluator.evaluate("f", "user-xyz", Map.of()).orElseThrow();
        assertTrue(first.enabled());
        assertTrue(List.of("control", "test").contains(first.value()));
        // deterministic
        assertEquals(first.value(), evaluator.evaluate("f", "user-xyz", Map.of()).orElseThrow().value());
    }

    @Test
    @DisplayName("partial rollout is deterministic and roughly proportional across many ids")
    void partialRolloutDistribution() {
        evaluator.addDefinition(new FlagDefinition("f", true, List.of(group(50)), List.of()));
        int enabled = 0;
        int n = 2000;
        for (int i = 0; i < n; i++) {
            if (evaluator.evaluate("f", "user-" + i, Map.of()).orElseThrow().enabled()) {
                enabled++;
            }
        }
        double ratio = (double) enabled / n;
        assertTrue(ratio > 0.42 && ratio < 0.58, "ratio was " + ratio);
    }

    @Test
    @DisplayName("setDefinitions replaces the whole set; clear/size behave")
    void definitionManagement() {
        evaluator.addDefinition(new FlagDefinition("a", true, List.of(group(100)), List.of()));
        evaluator.setDefinitions(java.util.Arrays.asList(
                new FlagDefinition("b", true, List.of(group(100)), List.of()),
                new FlagDefinition("c", true, List.of(group(100)), List.of()),
                null,
                new FlagDefinition(null, true, List.of(), List.of())
        ));
        assertEquals(2, evaluator.size());
        assertTrue(evaluator.evaluate("a", "u", Map.of()).isEmpty());
        assertTrue(evaluator.definition("b").isPresent());
        evaluator.clear();
        assertEquals(0, evaluator.size());
    }

    @Test
    @DisplayName("fromPostHog parses key/active/groups/multivariate")
    void fromPostHogParsing() {
        Map<String, Object> json = Map.of(
                "key", "beta",
                "active", true,
                "filters", Map.of(
                        "groups", List.of(Map.of(
                                "properties", List.of(Map.of("key", "plan", "value", "premium", "operator", "exact")),
                                "rollout_percentage", 100)),
                        "multivariate", Map.of("variants", List.of(
                                Map.of("key", "control", "rollout_percentage", 50),
                                Map.of("key", "test", "rollout_percentage", 50)))
                ));
        FlagDefinition def = FlagDefinition.fromPostHog(json);
        assertEquals("beta", def.key());
        assertTrue(def.active());
        assertEquals(1, def.groups().size());
        assertEquals(2, def.variants().size());

        evaluator.addDefinition(def);
        assertTrue(evaluator.evaluate("beta", "u", Map.of("plan", "premium")).orElseThrow().enabled());
        assertFalse(evaluator.evaluate("beta", "u", Map.of("plan", "free")).orElseThrow().enabled());
    }

    @Test
    @DisplayName("fromPostHog is null-safe and defaults active to true")
    void fromPostHogNullSafe() {
        assertFalse(FlagDefinition.fromPostHog(null).active());
        FlagDefinition minimal = FlagDefinition.fromPostHog(Map.of("key", "k"));
        assertEquals("k", minimal.key());
        assertTrue(minimal.active());
        assertTrue(minimal.groups().isEmpty());
    }
}
