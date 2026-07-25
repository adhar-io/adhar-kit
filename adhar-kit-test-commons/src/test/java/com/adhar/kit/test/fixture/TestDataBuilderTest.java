package com.adhar.kit.test.fixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TestDataBuilder.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("TestDataBuilder Tests")
class TestDataBuilderTest {

    @Test
    @DisplayName("Should create builder with instance")
    void testBuilderCreation() {
        // When
        TestPerson person = new TestPerson();
        TestDataBuilder<TestPerson> builder = new TestDataBuilder<>(person);

        // Then
        assertNotNull(builder, "Builder should not be null");
    }

    @Test
    @DisplayName("Should build object without modifications")
    void testBuildWithoutModifications() {
        // Given
        TestPerson person = new TestPerson();
        person.name = "John";
        person.age = 30;

        // When
        TestPerson result = new TestDataBuilder<>(person).build();

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals("John", result.name);
        assertEquals(30, result.age);
    }

    @Test
    @DisplayName("Should build object with single modification")
    void testBuildWithSingleModification() {
        // Given
        TestPerson person = new TestPerson();
        person.name = "John";
        person.age = 30;

        // When
        TestPerson result = new TestDataBuilder<>(person)
                .with(p -> p.age = 35)
                .build();

        // Then
        assertEquals("John", result.name);
        assertEquals(35, result.age);
    }

    @Test
    @DisplayName("Should build object with multiple modifications")
    void testBuildWithMultipleModifications() {
        // Given
        TestPerson person = new TestPerson();

        // When
        TestPerson result = new TestDataBuilder<>(person)
                .with(p -> p.name = "Alice")
                .with(p -> p.age = 25)
                .with(p -> p.email = "alice@example.com")
                .build();

        // Then
        assertEquals("Alice", result.name);
        assertEquals(25, result.age);
        assertEquals("alice@example.com", result.email);
    }

    @Test
    @DisplayName("Should build list of objects")
    void testBuildList() {
        // Given
        TestPerson person = new TestPerson();
        person.name = "John";

        // When
        List<TestPerson> results = new TestDataBuilder<>(person).buildList(5);

        // Then
        assertNotNull(results);
        assertEquals(5, results.size());
        results.forEach(p -> assertEquals("John", p.name));
    }

    @Test
    @DisplayName("Should build empty list when count is zero")
    void testBuildListEmpty() {
        // Given
        TestPerson person = new TestPerson();

        // When
        List<TestPerson> results = new TestDataBuilder<>(person).buildList(0);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should generate random ID")
    void testRandomId() {
        // When
        String id1 = TestDataBuilder.randomId();
        String id2 = TestDataBuilder.randomId();

        // Then
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Random IDs should be different");
        assertTrue(id1.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
                "Should be valid UUID format");
    }

    @Test
    @DisplayName("Should generate random email")
    void testRandomEmail() {
        // When
        String email1 = TestDataBuilder.randomEmail();
        String email2 = TestDataBuilder.randomEmail();

        // Then
        assertNotNull(email1);
        assertNotNull(email2);
        assertNotEquals(email1, email2, "Random emails should be different");
        assertTrue(email1.startsWith("test-"), "Email should start with test-");
        assertTrue(email1.endsWith("@example.com"), "Email should end with @example.com");
        assertTrue(email1.contains("@"), "Email should contain @");
    }

    @Test
    @DisplayName("Should generate random name")
    void testRandomName() {
        // When
        String name1 = TestDataBuilder.randomName();
        String name2 = TestDataBuilder.randomName();

        // Then
        assertNotNull(name1);
        assertNotNull(name2);
        assertNotEquals(name1, name2, "Random names should be different");
        assertTrue(name1.startsWith("TestName-"), "Name should start with TestName-");
    }

    @Test
    @DisplayName("Should generate random phone")
    void testRandomPhone() {
        // When
        String phone1 = TestDataBuilder.randomPhone();
        String phone2 = TestDataBuilder.randomPhone();

        // Then
        assertNotNull(phone1);
        assertNotNull(phone2);
        assertTrue(phone1.startsWith("+1"), "Phone should start with +1");
        assertTrue(phone1.length() > 5, "Phone should have reasonable length");
    }

    @Test
    @DisplayName("Should generate current timestamp")
    void testNow() {
        // When
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime now = TestDataBuilder.now();
        LocalDateTime after = LocalDateTime.now();

        // Then
        assertNotNull(now);
        assertTrue(now.isAfter(before.minusSeconds(1)) || now.isEqual(before.minusSeconds(1)),
                "Timestamp should not be before the test started");
        assertTrue(now.isBefore(after.plusSeconds(1)) || now.isEqual(after.plusSeconds(1)),
                "Timestamp should not be after the test ended");
    }

    @Test
    @DisplayName("Should generate random integer in range")
    void testRandomInt() {
        // When
        int value1 = TestDataBuilder.randomInt(10, 20);
        int value2 = TestDataBuilder.randomInt(10, 20);
        int value3 = TestDataBuilder.randomInt(10, 20);

        // Then
        assertTrue(value1 >= 10 && value1 <= 20, "Value should be in range [10, 20]");
        assertTrue(value2 >= 10 && value2 <= 20, "Value should be in range [10, 20]");
        assertTrue(value3 >= 10 && value3 <= 20, "Value should be in range [10, 20]");
    }

    @Test
    @DisplayName("Should generate random integer with single value range")
    void testRandomIntSingleValue() {
        // When
        int value = TestDataBuilder.randomInt(5, 5);

        // Then
        assertEquals(5, value, "Should return exact value when min equals max");
    }

    @Test
    @DisplayName("Should generate random integer for large range")
    void testRandomIntLargeRange() {
        // When
        int value = TestDataBuilder.randomInt(0, 1000);

        // Then
        assertTrue(value >= 0 && value <= 1000, "Value should be in range [0, 1000]");
    }

    @Test
    @DisplayName("Should generate random boolean")
    void testRandomBoolean() {
        // When - Generate multiple values and count
        int trueCount = 0;
        int falseCount = 0;

        for (int i = 0; i < 100; i++) {
            boolean value = TestDataBuilder.randomBoolean();
            if (value) {
                trueCount++;
            } else {
                falseCount++;
            }
        }

        // Then - Should generate both true and false values
        assertTrue(trueCount > 0, "Should generate at least one true value");
        assertTrue(falseCount > 0, "Should generate at least one false value");
        assertEquals(100, trueCount + falseCount, "Total should be 100");
    }

    @Test
    @DisplayName("Should chain multiple with calls")
    void testChainedModifications() {
        // Given
        TestPerson person = new TestPerson();

        // When
        TestPerson result = new TestDataBuilder<>(person)
                .with(p -> p.name = "Bob")
                .with(p -> p.age = 40)
                .with(p -> p.email = "bob@test.com")
                .with(p -> p.active = true)
                .build();

        // Then
        assertEquals("Bob", result.name);
        assertEquals(40, result.age);
        assertEquals("bob@test.com", result.email);
        assertTrue(result.active);
    }

    @Test
    @DisplayName("Should generate random alphanumeric string of requested length")
    void testRandomAlphanumeric() {
        String value = TestDataBuilder.randomAlphanumeric(12);

        assertNotNull(value);
        assertEquals(12, value.length());
        assertTrue(value.matches("^[A-Za-z0-9]+$"), "Should only contain alphanumeric characters");
    }

    @Test
    @DisplayName("Should generate empty alphanumeric string for zero length")
    void testRandomAlphanumericZeroLength() {
        assertEquals("", TestDataBuilder.randomAlphanumeric(0));
    }

    @Test
    @DisplayName("Should throw for negative alphanumeric length")
    void testRandomAlphanumericNegativeLength() {
        assertThrows(IllegalArgumentException.class, () -> TestDataBuilder.randomAlphanumeric(-1));
    }

    @Test
    @DisplayName("Should generate different alphanumeric strings across calls")
    void testRandomAlphanumericVaries() {
        String first = TestDataBuilder.randomAlphanumeric(20);
        String second = TestDataBuilder.randomAlphanumeric(20);

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("Should pick a random element from a list")
    void testRandomElement() {
        List<String> options = List.of("a", "b", "c");

        String picked = TestDataBuilder.randomElement(options);

        assertTrue(options.contains(picked));
    }

    @Test
    @DisplayName("Should return the only element for a single-element list")
    void testRandomElementSingle() {
        assertEquals("only", TestDataBuilder.randomElement(List.of("only")));
    }

    @Test
    @DisplayName("Should throw for null or empty options list")
    void testRandomElementInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TestDataBuilder.randomElement(null));
        assertThrows(IllegalArgumentException.class, () -> TestDataBuilder.randomElement(List.of()));
    }

    @Test
    @DisplayName("Should build a timestamp in the past")
    void testPastTimestamp() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(10);

        LocalDateTime past = TestDataBuilder.pastTimestamp(5);

        assertTrue(past.isAfter(before));
        assertTrue(past.isBefore(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Should build a timestamp in the future")
    void testFutureTimestamp() {
        LocalDateTime future = TestDataBuilder.futureTimestamp(5);

        assertTrue(future.isAfter(LocalDateTime.now()));
    }

    // Test helper class
    private static class TestPerson {
        String name;
        int age;
        String email;
        boolean active;
    }
}

