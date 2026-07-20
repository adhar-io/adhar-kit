package com.adhar.adharkit.cache.key;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheKeyGenerator}.
 */
@DisplayName("CacheKeyGenerator Tests")
class CacheKeyGeneratorTest {

    private CacheKeyGenerator generator;
    private SampleTarget target;
    private Method findMethod;

    static class SampleTarget {
        public String find(String userId, String type) {
            return userId + type;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        generator = new CacheKeyGenerator();
        target = new SampleTarget();
        findMethod = SampleTarget.class.getMethod("find", String.class, String.class);
    }

    @Test
    @DisplayName("generates key from parameter name expression")
    void keyFromParameterName() {
        Object key = generator.generate("#userId", findMethod, target, new Object[]{"u1", "premium"});
        assertEquals("u1", key);
    }

    @Test
    @DisplayName("generates key from indexed parameter expressions #p0 and #a1")
    void keyFromIndexedParameters() {
        Object[] args = {"u1", "premium"};
        assertEquals("u1", generator.generate("#p0", findMethod, target, args));
        assertEquals("premium", generator.generate("#a1", findMethod, target, args));
    }

    @Test
    @DisplayName("generates composite keys")
    void compositeKey() {
        Object key = generator.generate("#userId + '-' + #type", findMethod, target,
            new Object[]{"u1", "premium"});
        assertEquals("u1-premium", key);
    }

    @Test
    @DisplayName("exposes #root.methodName, #root.target and #root.args")
    void rootObject() {
        Object[] args = {"u1", "premium"};
        assertEquals("find", generator.generate("#root.methodName", findMethod, target, args));
        assertSame(target, generator.generate("#root.target", findMethod, target, args));
        assertEquals("u1", generator.generate("#root.args[0]", findMethod, target, args));
        assertSame(findMethod, generator.generate("#root.method", findMethod, target, args));
    }

    @Test
    @DisplayName("blank or null expression falls back to default key")
    void defaultKeyFallback() {
        Object[] args = {"u1", "premium"};
        String expected = generator.defaultKey(findMethod, args);

        assertEquals(expected, generator.generate(null, findMethod, target, args));
        assertEquals(expected, generator.generate("  ", findMethod, target, args));
    }

    @Test
    @DisplayName("expression evaluating to null falls back to default key")
    void nullExpressionValueFallsBack() {
        Object key = generator.generate("#result", findMethod, target, new Object[]{"u1", "t"});
        assertEquals(generator.defaultKey(findMethod, new Object[]{"u1", "t"}), key);
    }

    @Test
    @DisplayName("default key is deterministic and includes class.method")
    void defaultKeyShape() {
        Object[] args = {"u1", "premium"};
        String key1 = generator.defaultKey(findMethod, args);
        String key2 = generator.defaultKey(findMethod, new Object[]{"u1", "premium"});

        assertEquals(key1, key2);
        assertTrue(key1.startsWith(SampleTarget.class.getName() + ".find:"));
        assertNotEquals(key1, generator.defaultKey(findMethod, new Object[]{"u2", "premium"}));
        assertNotNull(generator.defaultKey(findMethod, null));
    }

    @Test
    @DisplayName("evaluateCondition returns true for blank conditions")
    void blankConditionIsTrue() {
        assertTrue(generator.evaluateCondition(null, findMethod, target, new Object[]{"u1", "t"}, null));
        assertTrue(generator.evaluateCondition("", findMethod, target, new Object[]{"u1", "t"}, null));
    }

    @Test
    @DisplayName("evaluateCondition evaluates parameter-based conditions")
    void parameterCondition() {
        Object[] args = {"PREM-1", "t"};
        assertTrue(generator.evaluateCondition("#userId.startsWith('PREM')", findMethod, target, args, null));
        assertFalse(generator.evaluateCondition("#userId.startsWith('STD')", findMethod, target, args, null));
    }

    @Test
    @DisplayName("evaluateCondition binds #result")
    void resultCondition() {
        Object[] args = {"u1", "t"};
        assertTrue(generator.evaluateCondition("#result != null && #result.length() > 2",
            findMethod, target, args, "long-value"));
        assertFalse(generator.evaluateCondition("#result != null", findMethod, target, args, null));
    }

    @Test
    @DisplayName("referencesResult detects #result usage")
    void referencesResult() {
        assertTrue(generator.referencesResult("#result != null"));
        assertFalse(generator.referencesResult("#userId"));
        assertFalse(generator.referencesResult(null));
    }

    @Test
    @DisplayName("generate requires a method")
    void generateRequiresMethod() {
        assertThrows(NullPointerException.class,
            () -> generator.generate("#p0", null, target, new Object[]{"u1"}));
    }
}
