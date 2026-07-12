package com.adhar.kit.test.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link BaseControllerTest} JSON helper methods.
 *
 * <p>Exercises {@code toJson}/{@code fromJson} directly via a concrete subclass with a
 * manually supplied {@link ObjectMapper} - no Spring context is started.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("BaseControllerTest Tests")
class BaseControllerTestTest {

    /** Minimal concrete subclass to access the protected helpers. */
    private static class ConcreteControllerTest extends BaseControllerTest {
    }

    public static class Sample {
        public String name;
        public int value;

        public Sample() {
        }

        public Sample(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    private ConcreteControllerTest subject;

    @BeforeEach
    void setUp() {
        subject = new ConcreteControllerTest();
        subject.objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("toJson should serialize an object to JSON")
    void testToJson() throws Exception {
        String json = subject.toJson(new Sample("widget", 42));

        assertTrue(json.contains("\"name\":\"widget\""), "JSON should contain the name field");
        assertTrue(json.contains("\"value\":42"), "JSON should contain the value field");
    }

    @Test
    @DisplayName("fromJson should deserialize JSON into an object")
    void testFromJson() throws Exception {
        Sample result = subject.fromJson("{\"name\":\"gadget\",\"value\":7}", Sample.class);

        assertNotNull(result);
        assertEquals("gadget", result.name);
        assertEquals(7, result.value);
    }

    @Test
    @DisplayName("toJson then fromJson should round-trip")
    void testRoundTrip() throws Exception {
        Sample original = new Sample("round", 99);

        Sample copy = subject.fromJson(subject.toJson(original), Sample.class);

        assertEquals(original.name, copy.name);
        assertEquals(original.value, copy.value);
    }

    @Test
    @DisplayName("fromJson should throw for invalid JSON")
    void testFromJsonInvalid() {
        assertThrows(Exception.class, () -> subject.fromJson("not-json", Sample.class));
    }
}
