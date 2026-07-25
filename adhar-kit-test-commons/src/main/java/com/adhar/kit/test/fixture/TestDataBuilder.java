package com.adhar.kit.test.fixture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * Generate a random alphanumeric string of the given length. Useful for test tokens,
     * codes, or IDs that must not look like a UUID.
     */
    public static String randomAlphanumeric(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative: " + length);
        }
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Pick a random element from a non-empty list. Handy for randomizing enum-like test data
     * (statuses, categories, roles, ...).
     */
    public static <E> E randomElement(List<E> options) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be null or empty");
        }
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    /**
     * A timestamp {@code minutesAgo} minutes before now. Useful for building "created before
     * the retention window" style fixtures.
     */
    public static LocalDateTime pastTimestamp(long minutesAgo) {
        return LocalDateTime.now().minusMinutes(minutesAgo);
    }

    /**
     * A timestamp {@code minutesAhead} minutes after now. Useful for building "expires in the
     * future" style fixtures.
     */
    public static LocalDateTime futureTimestamp(long minutesAhead) {
        return LocalDateTime.now().plusMinutes(minutesAhead);
    }
}

