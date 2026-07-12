package com.adhar.kit.test.assertion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for CustomAssertions.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("CustomAssertions Tests")
class CustomAssertionsTest {

    @Test
    @DisplayName("Should assert time approximately equal within seconds")
    void testAssertTimeApproximately_Success() {
        // Given
        LocalDateTime expected = LocalDateTime.now();
        LocalDateTime actual = expected.plusSeconds(5);

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertTimeApproximately(actual, expected, 10),
                "Should not throw for times within threshold");
    }

    @Test
    @DisplayName("Should throw when time difference exceeds threshold")
    void testAssertTimeApproximately_Failure() {
        // Given
        LocalDateTime expected = LocalDateTime.now();
        LocalDateTime actual = expected.plusSeconds(70);

        // When/Then
        AssertionError error = assertThrows(AssertionError.class,
                () -> CustomAssertions.assertTimeApproximately(actual, expected, 60),
                "Should throw when difference exceeds threshold");
        assertTrue(error.getMessage().contains("within 60 seconds"));
    }

    @Test
    @DisplayName("Should assert time approximately for past times")
    void testAssertTimeApproximately_Past() {
        // Given
        LocalDateTime expected = LocalDateTime.now();
        LocalDateTime actual = expected.minusSeconds(30);

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertTimeApproximately(actual, expected, 60),
                "Should work for past times");
    }

    @Test
    @DisplayName("Should assert recent timestamp for current time")
    void testAssertRecentTimestamp_Success() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertRecentTimestamp(now),
                "Should not throw for current timestamp");
    }

    @Test
    @DisplayName("Should assert recent timestamp for time within last minute")
    void testAssertRecentTimestamp_WithinMinute() {
        // Given
        LocalDateTime recent = LocalDateTime.now().minusSeconds(30);

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertRecentTimestamp(recent),
                "Should not throw for timestamp within last minute");
    }

    @Test
    @DisplayName("Should throw for old timestamp")
    void testAssertRecentTimestamp_Failure() {
        // Given
        LocalDateTime old = LocalDateTime.now().minusMinutes(5);

        // When/Then
        assertThrows(AssertionError.class,
                () -> CustomAssertions.assertRecentTimestamp(old),
                "Should throw for old timestamp");
    }

    @Test
    @DisplayName("Should validate correct UUID")
    void testAssertValidUUID_Success() {
        // Given
        String validUUID = "123e4567-e89b-12d3-a456-426614174000";

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertValidUUID(validUUID),
                "Should not throw for valid UUID");
    }

    @Test
    @DisplayName("Should validate random UUID")
    void testAssertValidUUID_RandomUUID() {
        // Given
        String validUUID = UUID.randomUUID().toString();

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertValidUUID(validUUID),
                "Should not throw for random UUID");
    }

    @Test
    @DisplayName("Should throw for invalid UUID")
    void testAssertValidUUID_Failure() {
        // Given
        String invalidUUID = "not-a-valid-uuid";

        // When/Then
        AssertionError error = assertThrows(AssertionError.class,
                () -> CustomAssertions.assertValidUUID(invalidUUID),
                "Should throw for invalid UUID");
        assertTrue(error.getMessage().contains("Expected valid UUID"));
    }

    @Test
    @DisplayName("Should throw for null UUID")
    void testAssertValidUUID_Null() {
        // When/Then
        assertThrows(AssertionError.class,
                () -> CustomAssertions.assertValidUUID(null),
                "Should throw for null UUID");
    }

    @Test
    @DisplayName("Should validate correct email")
    void testAssertValidEmail_Success() {
        // Given
        String validEmail = "test@example.com";

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertValidEmail(validEmail),
                "Should not throw for valid email");
    }

    @Test
    @DisplayName("Should validate email with subdomain")
    void testAssertValidEmail_Subdomain() {
        // Given
        String validEmail = "user@mail.example.com";

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertValidEmail(validEmail),
                "Should not throw for email with subdomain");
    }

    @Test
    @DisplayName("Should validate email with plus sign")
    void testAssertValidEmail_PlusSign() {
        // Given
        String validEmail = "user+tag@example.com";

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertValidEmail(validEmail),
                "Should not throw for email with plus sign");
    }

    @Test
    @DisplayName("Should throw for invalid email")
    void testAssertValidEmail_Failure() {
        // Given
        String invalidEmail = "not-an-email";

        // When/Then
        AssertionError error = assertThrows(AssertionError.class,
                () -> CustomAssertions.assertValidEmail(invalidEmail),
                "Should throw for invalid email");
        assertTrue(error.getMessage().contains("Expected valid email"));
    }

    @Test
    @DisplayName("Should throw for null email")
    void testAssertValidEmail_Null() {
        // When/Then
        assertThrows(AssertionError.class,
                () -> CustomAssertions.assertValidEmail(null),
                "Should throw for null email");
    }

    @Test
    @DisplayName("Should throw for email without domain")
    void testAssertValidEmail_NoDomain() {
        // Given
        String invalidEmail = "user@";

        // When/Then
        assertThrows(AssertionError.class,
                () -> CustomAssertions.assertValidEmail(invalidEmail),
                "Should throw for email without domain");
    }

    @Test
    @DisplayName("Should assert non-empty collection")
    void testAssertNotEmpty_Success() {
        // Given
        List<String> nonEmpty = Arrays.asList("item1", "item2");

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertNotEmpty(nonEmpty),
                "Should not throw for non-empty collection");
    }

    @Test
    @DisplayName("Should throw for empty collection")
    void testAssertNotEmpty_Empty() {
        // Given
        List<String> empty = new ArrayList<>();

        // When/Then
        AssertionError error = assertThrows(AssertionError.class,
                () -> CustomAssertions.assertNotEmpty(empty),
                "Should throw for empty collection");
        assertTrue(error.getMessage().contains("Expected non-empty collection"));
    }

    @Test
    @DisplayName("Should throw for null collection")
    void testAssertNotEmpty_Null() {
        // When/Then
        assertThrows(AssertionError.class,
                () -> CustomAssertions.assertNotEmpty(null),
                "Should throw for null collection");
    }

    @Test
    @DisplayName("Should assert same properties")
    void testAssertSameProperties_Success() {
        // Given
        TestObject obj1 = new TestObject("John", 30, "Engineer");
        TestObject obj2 = new TestObject("John", 30, "Engineer");

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertSameProperties(obj1, obj2),
                "Should not throw for objects with same properties");
    }

    @Test
    @DisplayName("Should assert same properties excluding fields")
    void testAssertSameProperties_ExcludingFields() {
        // Given
        TestObject obj1 = new TestObject("John", 30, "Engineer");
        TestObject obj2 = new TestObject("John", 35, "Engineer");

        // When/Then
        assertDoesNotThrow(() -> CustomAssertions.assertSameProperties(obj1, obj2, "age"),
                "Should not throw when excluding different fields");
    }

    @Test
    @DisplayName("Should be instantiable (utility class default constructor)")
    void testConstructor() {
        assertNotNull(new CustomAssertions(), "CustomAssertions should be instantiable");
    }

    @Test
    @DisplayName("Should throw when properties differ")
    void testAssertSameProperties_Failure() {
        // Given
        TestObject obj1 = new TestObject("John", 30, "Engineer");
        TestObject obj2 = new TestObject("Jane", 30, "Engineer");

        // When/Then
        assertThrows(AssertionError.class,
                () -> CustomAssertions.assertSameProperties(obj1, obj2),
                "Should throw when properties differ");
    }

    // Test helper class
    private static class TestObject {
        private final String name;
        private final int age;
        private final String title;

        public TestObject(String name, int age, String title) {
            this.name = name;
            this.age = age;
            this.title = title;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
        public String getTitle() { return title; }
    }
}

