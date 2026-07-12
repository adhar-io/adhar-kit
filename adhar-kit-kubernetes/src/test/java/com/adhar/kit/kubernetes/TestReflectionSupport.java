package com.adhar.kit.kubernetes;

import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;

/**
 * Small reflection helpers for unit tests.
 *
 * <p>The production services create their own internal Fabric8
 * {@code io.fabric8.kubernetes.client.KubernetesClient} in their constructors.
 * To unit-test them without a real cluster we replace that private field with a
 * Mockito mock after construction.</p>
 */
public final class TestReflectionSupport {

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();

    private TestReflectionSupport() {
    }

    /**
     * Allocates an instance of the given type <em>without</em> invoking any
     * constructor. The production services build a real Fabric8 client in their
     * constructors (which is not possible in a unit-test environment), so tests
     * allocate the object and then inject a mocked Fabric8 client.
     *
     * @param type the class to instantiate
     * @param <T>  the type
     * @return a bare instance with all fields at their default values
     */
    public static <T> T newInstanceWithoutConstructor(Class<T> type) {
        return OBJENESIS.newInstance(type);
    }

    /**
     * Sets a (possibly private/final) field on the given target instance.
     *
     * @param target    the object whose field should be set
     * @param fieldName the field name (searched up the class hierarchy)
     * @param value     the new value
     */
    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set field '" + fieldName + "'", e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
