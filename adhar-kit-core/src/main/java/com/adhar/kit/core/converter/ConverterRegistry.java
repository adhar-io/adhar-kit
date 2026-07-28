package com.adhar.kit.core.converter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link TypeConverterSpi} instances keyed by exact target type.
 *
 * <p>This is the extensible engine behind the static {@link TypeConverter}
 * utility. It ships with converters for the common JDK value types (numbers,
 * booleans, {@code java.time} types, {@link UUID}, {@link BigDecimal}) and lets
 * callers add their own - either programmatically via {@link #register} or
 * through the {@link ServiceLoader} mechanism (see {@link TypeConverterSpi}).</p>
 *
 * <p>The process-wide {@linkplain #getDefault() default registry} is what
 * {@link TypeConverter} delegates to, so registering a converter there changes
 * the behaviour of every {@code TypeConverter.convert(...)} caller. Independent
 * registries can also be created with {@link #withDefaults()} or
 * {@link #empty()} for isolated use (e.g. in tests).</p>
 *
 * <p>Instances are thread-safe.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class ConverterRegistry {

    private static final ConverterRegistry DEFAULT = withDefaults().loadFromServiceLoader();

    private final Map<Class<?>, TypeConverterSpi<?>> converters = new ConcurrentHashMap<>();

    private ConverterRegistry() {
    }

    /**
     * Returns the process-wide default registry used by {@link TypeConverter}.
     *
     * @return the shared default registry
     */
    public static ConverterRegistry getDefault() {
        return DEFAULT;
    }

    /**
     * Creates an empty registry with no converters registered.
     *
     * @return a new empty registry
     */
    public static ConverterRegistry empty() {
        return new ConverterRegistry();
    }

    /**
     * Creates a registry pre-populated with the built-in converters for the
     * common JDK value types, without running {@link ServiceLoader} discovery.
     *
     * @return a new registry with the built-in converters
     */
    public static ConverterRegistry withDefaults() {
        ConverterRegistry registry = new ConverterRegistry();
        registry.registerBuiltins();
        return registry;
    }

    /**
     * Registers a converter for its declared {@link TypeConverterSpi#targetType()}.
     * Replaces any converter previously registered for that type.
     *
     * @param converter the converter to register (its target type must be non-null)
     * @param <T> the target type
     * @return this registry, for chaining
     * @throws NullPointerException if the converter or its target type is null
     */
    public <T> ConverterRegistry register(TypeConverterSpi<T> converter) {
        Objects.requireNonNull(converter, "converter must not be null");
        Class<T> targetType = converter.targetType();
        Objects.requireNonNull(targetType,
            "converter targetType() must not be null when registered without an explicit type");
        return register(targetType, converter);
    }

    /**
     * Registers a converter for an explicit target type. Replaces any converter
     * previously registered for that type.
     *
     * @param targetType the exact target type the converter produces
     * @param converter the converter
     * @param <T> the target type
     * @return this registry, for chaining
     * @throws NullPointerException if either argument is null
     */
    public <T> ConverterRegistry register(Class<T> targetType, TypeConverterSpi<T> converter) {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
        converters.put(targetType, converter);
        return this;
    }

    /**
     * Removes any converter registered for the given target type.
     *
     * @param targetType the target type to unregister
     * @return this registry, for chaining
     */
    public ConverterRegistry unregister(Class<?> targetType) {
        converters.remove(targetType);
        return this;
    }

    /**
     * Returns whether a converter is registered for the given target type.
     *
     * @param targetType the target type
     * @return true if a converter is registered
     */
    public boolean hasConverter(Class<?> targetType) {
        return converters.containsKey(targetType);
    }

    /**
     * Discovers and registers {@link TypeConverterSpi} providers via the
     * {@link ServiceLoader}. Discovered converters override built-ins for the
     * same target type.
     *
     * @return this registry, for chaining
     */
    public ConverterRegistry loadFromServiceLoader() {
        for (TypeConverterSpi<?> converter : ServiceLoader.load(TypeConverterSpi.class)) {
            Class<?> targetType = converter.targetType();
            if (targetType != null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                TypeConverterSpi raw = converter;
                converters.put(targetType, raw);
            }
        }
        return this;
    }

    /**
     * Converts the value to the target type using the registered converters.
     *
     * <p>Resolution order: {@code null} in gives {@code null} out; a value that
     * is already an instance of the target type is returned as-is; otherwise the
     * converter registered for the exact target type (if any) is applied. Any
     * exception thrown by a converter is swallowed and reported as a failed
     * ({@code null}) conversion, preserving the historical contract of
     * {@link TypeConverter#convert(Object, Class)}.</p>
     *
     * @param value the value to convert
     * @param targetType the target type
     * @param <T> the target type
     * @return the converted value, or {@code null} if not convertible
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Object value, Class<T> targetType) {
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        TypeConverterSpi<?> converter = converters.get(targetType);
        if (converter == null) {
            return null;
        }
        try {
            return (T) converter.convert(value);
        } catch (Exception e) {
            return null;
        }
    }

    private void registerBuiltins() {
        register(String.class, Object::toString);

        TypeConverterSpi<Integer> toInt = v -> Integer.valueOf(v.toString());
        converters.put(Integer.class, toInt);
        converters.put(int.class, toInt);

        TypeConverterSpi<Long> toLong = v -> Long.valueOf(v.toString());
        converters.put(Long.class, toLong);
        converters.put(long.class, toLong);

        TypeConverterSpi<Double> toDouble = v -> Double.valueOf(v.toString());
        converters.put(Double.class, toDouble);
        converters.put(double.class, toDouble);

        TypeConverterSpi<Float> toFloat = v -> Float.valueOf(v.toString());
        converters.put(Float.class, toFloat);
        converters.put(float.class, toFloat);

        TypeConverterSpi<Boolean> toBoolean = v -> Boolean.valueOf(v.toString());
        converters.put(Boolean.class, toBoolean);
        converters.put(boolean.class, toBoolean);

        register(BigDecimal.class, v -> new BigDecimal(v.toString()));
        register(LocalDate.class, v -> LocalDate.parse(v.toString()));
        register(LocalDateTime.class, v -> LocalDateTime.parse(v.toString()));
        register(LocalTime.class, v -> LocalTime.parse(v.toString()));
        register(Instant.class, v -> Instant.parse(v.toString()));
        register(UUID.class, v -> UUID.fromString(v.toString()));
    }
}
