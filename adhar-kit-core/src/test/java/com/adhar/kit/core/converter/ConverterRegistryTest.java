package com.adhar.kit.core.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("ConverterRegistry Tests")
class ConverterRegistryTest {

    @Nested
    @DisplayName("Built-in converters")
    class Builtins {

        private final ConverterRegistry registry = ConverterRegistry.withDefaults();

        @Test
        void nullValueReturnsNull() {
            assertThat(registry.convert(null, Integer.class)).isNull();
        }

        @Test
        void alreadyInstanceReturnedAsIs() {
            String s = "hello";
            assertThat(registry.convert(s, String.class)).isSameAs(s);
        }

        @Test
        void convertsCommonTypes() {
            assertThat(registry.convert("42", Integer.class)).isEqualTo(42);
            assertThat(registry.convert("100", Long.class)).isEqualTo(100L);
            assertThat(registry.convert("3.14", Double.class)).isEqualTo(3.14);
            assertThat(registry.convert("2.5", Float.class)).isEqualTo(2.5f);
            assertThat(registry.convert("true", Boolean.class)).isTrue();
            assertThat(registry.convert("9.99", BigDecimal.class)).isEqualByComparingTo(new BigDecimal("9.99"));
            assertThat(registry.convert("2025-06-15", LocalDate.class)).isEqualTo(LocalDate.of(2025, 6, 15));
        }

        @Test
        void convertsPrimitiveTargetTypes() {
            assertThat(registry.convert("7", int.class)).isEqualTo(7);
            assertThat(registry.convert("8", long.class)).isEqualTo(8L);
            assertThat(registry.convert("true", boolean.class)).isEqualTo(Boolean.TRUE);
        }

        @Test
        void failedConversionReturnsNull() {
            assertThat(registry.convert("abc", Integer.class)).isNull();
        }

        @Test
        void unknownTargetTypeReturnsNull() {
            assertThat(registry.convert("x", Thread.class)).isNull();
        }

        @Test
        void hasConverterReflectsRegistration() {
            assertThat(registry.hasConverter(UUID.class)).isTrue();
            assertThat(registry.hasConverter(Thread.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("Empty registry")
    class Empty {

        @Test
        void emptyHasNoConverters() {
            ConverterRegistry registry = ConverterRegistry.empty();
            assertThat(registry.hasConverter(Integer.class)).isFalse();
            assertThat(registry.convert("42", Integer.class)).isNull();
        }
    }

    @Nested
    @DisplayName("Custom converter registration")
    class CustomConverters {

        @Test
        void registerWithDeclaredTargetType() {
            ConverterRegistry registry = ConverterRegistry.empty();
            registry.register(new PointConverter());

            Point p = registry.convert("3,4", Point.class);
            assertThat(p).isNotNull();
            assertThat(p.x).isEqualTo(3);
            assertThat(p.y).isEqualTo(4);
        }

        @Test
        void registerWithExplicitType() {
            ConverterRegistry registry = ConverterRegistry.empty();
            registry.register(Point.class, value -> {
                String[] parts = value.toString().split(",");
                return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            });

            assertThat(registry.convert("5,6", Point.class)).extracting(pt -> pt.x + pt.y).isEqualTo(11);
        }

        @Test
        void customConverterOverridesBuiltin() {
            ConverterRegistry registry = ConverterRegistry.withDefaults();
            registry.register(Integer.class, value -> 999);

            assertThat(registry.convert("42", Integer.class)).isEqualTo(999);
        }

        @Test
        void converterThrowingIsTreatedAsFailure() {
            ConverterRegistry registry = ConverterRegistry.empty();
            registry.register(Point.class, value -> {
                throw new IllegalStateException("boom");
            });

            assertThat(registry.convert("x", Point.class)).isNull();
        }

        @Test
        void unregisterRemovesConverter() {
            ConverterRegistry registry = ConverterRegistry.withDefaults();
            assertThat(registry.hasConverter(Integer.class)).isTrue();

            registry.unregister(Integer.class);

            assertThat(registry.hasConverter(Integer.class)).isFalse();
            assertThat(registry.convert("42", Integer.class)).isNull();
        }

        @Test
        void registerNullConverterThrows() {
            ConverterRegistry registry = ConverterRegistry.empty();
            assertThatNullPointerException().isThrownBy(() -> registry.register(null));
        }

        @Test
        void registerConverterWithNullTargetTypeThrows() {
            ConverterRegistry registry = ConverterRegistry.empty();
            // A functional-interface converter has a null targetType() by default.
            TypeConverterSpi<Point> noType = value -> new Point(0, 0);
            assertThatNullPointerException().isThrownBy(() -> registry.register(noType));
        }

        @Test
        void convertNullTargetTypeThrows() {
            ConverterRegistry registry = ConverterRegistry.empty();
            assertThatNullPointerException().isThrownBy(() -> registry.convert("x", null));
        }
    }

    @Nested
    @DisplayName("Default registry and static facade")
    class DefaultRegistry {

        @Test
        void defaultRegistryIsSharedAndPopulated() {
            assertThat(ConverterRegistry.getDefault()).isSameAs(ConverterRegistry.getDefault());
            assertThat(ConverterRegistry.getDefault().hasConverter(Integer.class)).isTrue();
        }

        @Test
        void typeConverterStaticRegistrationFlowsThroughDefaultRegistry() {
            TypeConverter.registerConverter(new PointConverter());
            try {
                Point p = TypeConverter.convert("7,8", Point.class);
                assertThat(p).isNotNull();
                assertThat(p.x).isEqualTo(7);
                assertThat(p.y).isEqualTo(8);
            } finally {
                ConverterRegistry.getDefault().unregister(Point.class);
            }
        }

        @Test
        void typeConverterStaticRegistrationWithExplicitType() {
            TypeConverter.registerConverter(Point.class, value -> new Point(1, 1));
            try {
                assertThat(TypeConverter.convert("ignored", Point.class))
                    .extracting(p -> p.x + p.y).isEqualTo(2);
            } finally {
                ConverterRegistry.getDefault().unregister(Point.class);
            }
        }
    }

    // --- test fixtures -----------------------------------------------------

    static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class PointConverter implements TypeConverterSpi<Point> {
        @Override
        public Class<Point> targetType() {
            return Point.class;
        }

        @Override
        public Point convert(Object value) {
            String[] parts = value.toString().split(",");
            return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }
    }
}
