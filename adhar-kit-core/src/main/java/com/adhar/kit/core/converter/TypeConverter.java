package com.adhar.kit.core.converter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Type converter utility for converting between different types.
 *
 * <p>Provides safe type conversions with default values.</p>
 *
 * <p>The {@code convert(...)} methods delegate to the process-wide
 * {@linkplain ConverterRegistry#getDefault() default converter registry}, which
 * ships with converters for the common JDK value types and is extensible: custom
 * {@link TypeConverterSpi} converters can be added programmatically via
 * {@link #registerConverter(TypeConverterSpi)} or discovered automatically via
 * the {@link java.util.ServiceLoader}. This keeps the static utility API stable
 * while allowing new conversions to be plugged in.</p>
 *
 * <p><b>Example - String to Types:</b></p>
 * <pre>{@code
 * Integer age = TypeConverter.convert("25", Integer.class);
 * Boolean active = TypeConverter.convert("true", Boolean.class);
 * LocalDate date = TypeConverter.convert("2025-01-01", LocalDate.class);
 *
 * // With default value
 * Integer count = TypeConverter.convert("invalid", Integer.class, 0);
 * }</pre>
 *
 * <p><b>Example - Object to Map:</b></p>
 * <pre>{@code
 * Map<String, Object> map = TypeConverter.objectToMap(user);
 * }</pre>
 *
 * <p><b>Example - custom converter:</b></p>
 * <pre>{@code
 * TypeConverter.registerConverter(new TypeConverterSpi<Color>() {
 *     public Class<Color> targetType() { return Color.class; }
 *     public Color convert(Object value) { return Color.parse(value.toString()); }
 * });
 * Color c = TypeConverter.convert("#ff0000", Color.class);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TypeConverter {

    private TypeConverter() {
    }

    /**
     * Converts value to target type using the default {@link ConverterRegistry}.
     *
     * @param value the value to convert
     * @param targetType target type class
     * @param <T> target type
     * @return converted value or null if conversion fails
     */
    public static <T> T convert(Object value, Class<T> targetType) {
        return ConverterRegistry.getDefault().convert(value, targetType);
    }

    /**
     * Registers a custom converter on the default {@link ConverterRegistry},
     * making it available to all {@link #convert(Object, Class)} callers.
     *
     * @param converter the converter (its {@link TypeConverterSpi#targetType()} must be non-null)
     * @param <T> the target type
     */
    public static <T> void registerConverter(TypeConverterSpi<T> converter) {
        ConverterRegistry.getDefault().register(converter);
    }

    /**
     * Registers a custom converter for an explicit target type on the default
     * {@link ConverterRegistry}.
     *
     * @param targetType the exact target type the converter produces
     * @param converter the converter
     * @param <T> the target type
     */
    public static <T> void registerConverter(Class<T> targetType, TypeConverterSpi<T> converter) {
        ConverterRegistry.getDefault().register(targetType, converter);
    }

    /**
     * Converts value to target type with default value.
     *
     * @param value the value to convert
     * @param targetType target type class
     * @param defaultValue default value if conversion fails
     * @param <T> target type
     * @return converted value or default value
     */
    public static <T> T convert(Object value, Class<T> targetType, T defaultValue) {
        T result = convert(value, targetType);
        return result != null ? result : defaultValue;
    }

    /**
     * Converts object to map using reflection.
     *
     * @param obj the object to convert
     * @return map representation
     */
    public static Map<String, Object> objectToMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }

        if (obj instanceof Map) {
            Map<String, Object> map = new HashMap<>();
            Map<?, ?> sourceMap = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return map;
        }

        Map<String, Object> map = new HashMap<>();

        try {
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();
                Object fieldValue = field.get(obj);
                map.put(fieldName, fieldValue);
            }
        } catch (Exception e) {
            // Return empty map on error
        }

        return map;
    }

    /**
     * Converts map to object.
     *
     * @param map the map
     * @param targetType target type class
     * @param <T> target type
     * @return object instance
     */
    public static <T> T mapToObject(Map<String, Object> map, Class<T> targetType) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            T instance = targetType.getDeclaredConstructor().newInstance();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                try {
                    java.lang.reflect.Field field = targetType.getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    field.set(instance, entry.getValue());
                } catch (NoSuchFieldException e) {
                    // Skip unknown fields
                }
            }

            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts string to date with pattern.
     *
     * @param dateString date string
     * @param pattern date pattern
     * @return LocalDate or null
     */
    public static LocalDate parseDate(String dateString, String pattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.parse(dateString, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts string to datetime with pattern.
     *
     * @param dateTimeString datetime string
     * @param pattern datetime pattern
     * @return LocalDateTime or null
     */
    public static LocalDateTime parseDateTime(String dateTimeString, String pattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.parse(dateTimeString, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if value can be converted to type.
     *
     * @param value the value
     * @param targetType target type
     * @return true if convertible
     */
    public static boolean canConvert(Object value, Class<?> targetType) {
        return convert(value, targetType) != null;
    }
}

