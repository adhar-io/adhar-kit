package com.adhar.kit.core.converter;

/**
 * A {@link TypeConverterSpi} registered via
 * {@code META-INF/services/com.adhar.kit.core.converter.TypeConverterSpi} so the
 * ServiceLoader discovery path of {@link ConverterRegistry} can be verified.
 */
public class ServiceLoadedColorConverter implements TypeConverterSpi<ServiceLoadedColorConverter.Color> {

    public enum Color {
        RED, GREEN, BLUE
    }

    @Override
    public Class<Color> targetType() {
        return Color.class;
    }

    @Override
    public Color convert(Object value) {
        return Color.valueOf(value.toString().trim().toUpperCase());
    }
}
