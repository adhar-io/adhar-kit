package com.adhar.kit.core.converter;

/**
 * Service provider interface for a single custom type conversion.
 *
 * <p>A {@code TypeConverterSpi} knows how to produce a value of one specific
 * {@linkplain #targetType() target type} from an arbitrary source object.
 * Implementations can be registered in two ways:</p>
 * <ul>
 *   <li><b>Programmatically</b> - via
 *       {@link ConverterRegistry#register(TypeConverterSpi)} (or the
 *       convenience {@link TypeConverter#registerConverter(TypeConverterSpi)}).</li>
 *   <li><b>Via {@link java.util.ServiceLoader}</b> - declare the implementation
 *       under
 *       {@code META-INF/services/com.adhar.kit.core.converter.TypeConverterSpi};
 *       it is discovered automatically the first time the default
 *       {@link ConverterRegistry} is used.</li>
 * </ul>
 *
 * <p>A converter registered for a target type <em>overrides</em> any built-in
 * conversion for that type, so applications can customise how, for example,
 * {@code LocalDate} strings are parsed.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * public final class ColorConverter implements TypeConverterSpi<Color> {
 *     public Class<Color> targetType() { return Color.class; }
 *     public Color convert(Object value) { return Color.parse(value.toString()); }
 * }
 *
 * TypeConverter.registerConverter(new ColorConverter());
 * Color c = TypeConverter.convert("#ff0000", Color.class);
 * }</pre>
 *
 * @param <T> the target type produced by this converter
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface TypeConverterSpi<T> {

    /**
     * Converts the (non-null) source value to the target type.
     *
     * <p>The registry guarantees {@code value} is never {@code null} and is not
     * already an instance of the target type. Implementations may throw any
     * exception to signal an unconvertible value; the registry treats a thrown
     * exception the same as a {@code null} result (conversion failed).</p>
     *
     * @param value the non-null source value
     * @return the converted value, or {@code null} if it cannot be converted
     */
    T convert(Object value);

    /**
     * The exact target type this converter produces.
     *
     * <p>Defaults to {@code null}, which is only valid for converters created
     * through {@link ConverterRegistry#register(Class, TypeConverterSpi)} where
     * the type is supplied separately. Standalone/ServiceLoader providers must
     * override this to return their concrete target type.</p>
     *
     * @return the target type, or {@code null} if supplied externally
     */
    default Class<T> targetType() {
        return null;
    }
}
