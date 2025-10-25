package com.adhar.kit.test.fixture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic builder for test data.
 * Provides fluent API for creating test objects.
 *
 * @param <T> the type of object to build
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TestDataBuilder<T> {

    private final T instance;
    private final List<Consumer<T>> modifiers = new ArrayList<>();

    /**
     * Create a builder with an instance.
     */
    public TestDataBuilder(T instance) {
        this.instance = instance;
    }

    /**
     * Add a modifier to the builder.
     */
    public TestDataBuilder<T> with(Consumer<T> modifier) {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Build the object.
     */
    public T build() {
        modifiers.forEach(modifier -> modifier.accept(instance));
        return instance;
    }

    /**
     * Build a list of objects.
     */
    public List<T> buildList(int count) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(build());
        }
        return list;
    }

    // Common test data generators

    /**
     * Generate a random UUID string.
     */
    public static String randomId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a random email.
     */
    public static String randomEmail() {
        return String.format("test-%s@example.com", randomId().substring(0, 8));
    }

    /**
     * Generate a random name.
     */
    public static String randomName() {
        return "TestName-" + randomId().substring(0, 8);
    }

    /**
     * Generate a random phone number.
     */
    public static String randomPhone() {
        return String.format("+1%010d", (long) (Math.random() * 10000000000L));
    }

    /**
     * Get current timestamp.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Generate a random integer between min and max.
     */
    public static int randomInt(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }

    /**
     * Generate a random boolean.
     */
    public static boolean randomBoolean() {
        return Math.random() > 0.5;
    }
}

