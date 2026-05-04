package com.adhar.kit.core.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TypeConverter Tests")
class TypeConverterTest {

    @Nested
    @DisplayName("Basic Type Conversions")
    class BasicConversions {

        @Test
        void convert_nullReturnsNull() {
            assertThat(TypeConverter.convert(null, String.class)).isNull();
        }

        @Test
        void convert_sameTypeReturnsSameInstance() {
            String s = "hello";
            assertThat(TypeConverter.convert(s, String.class)).isSameAs(s);
        }

        @Test
        void convert_toString() {
            assertThat(TypeConverter.convert(123, String.class)).isEqualTo("123");
        }

        @Test
        void convert_toInteger() {
            assertThat(TypeConverter.convert("42", Integer.class)).isEqualTo(42);
            assertThat(TypeConverter.convert("abc", Integer.class)).isNull();
        }

        @Test
        void convert_toLong() {
            assertThat(TypeConverter.convert("100", Long.class)).isEqualTo(100L);
        }

        @Test
        void convert_toDouble() {
            assertThat(TypeConverter.convert("3.14", Double.class)).isEqualTo(3.14);
        }

        @Test
        void convert_toFloat() {
            assertThat(TypeConverter.convert("2.5", Float.class)).isEqualTo(2.5f);
        }

        @Test
        void convert_toBoolean() {
            assertThat(TypeConverter.convert("true", Boolean.class)).isTrue();
            assertThat(TypeConverter.convert("false", Boolean.class)).isFalse();
        }

        @Test
        void convert_toBigDecimal() {
            assertThat(TypeConverter.convert("99.99", BigDecimal.class))
                .isEqualByComparingTo(new BigDecimal("99.99"));
        }

        @Test
        void convert_toLocalDate() {
            assertThat(TypeConverter.convert("2025-06-15", LocalDate.class))
                .isEqualTo(LocalDate.of(2025, 6, 15));
        }

        @Test
        void convert_toUUID() {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            assertThat(TypeConverter.convert(uuid, UUID.class)).isEqualTo(UUID.fromString(uuid));
        }
    }

    @Nested
    @DisplayName("Convert with Default")
    class ConvertWithDefault {

        @Test
        void convert_withDefault_returnsDefaultOnFailure() {
            assertThat(TypeConverter.convert("abc", Integer.class, 0)).isEqualTo(0);
            assertThat(TypeConverter.convert("42", Integer.class, 0)).isEqualTo(42);
            assertThat(TypeConverter.convert(null, String.class, "def")).isEqualTo("def");
        }
    }

    @Nested
    @DisplayName("Object/Map Conversions")
    class ObjectMapConversions {

        @Test
        void objectToMap_convertsFields() {
            TestObj obj = new TestObj();
            obj.name = "test";
            obj.age = 25;
            Map<String, Object> map = TypeConverter.objectToMap(obj);
            assertThat(map).containsEntry("name", "test").containsEntry("age", 25);
        }

        @Test
        void objectToMap_nullReturnsEmptyMap() {
            assertThat(TypeConverter.objectToMap(null)).isEmpty();
        }

        @Test
        void objectToMap_fromMap() {
            Map<String, Object> input = Map.of("key", "value");
            Map<String, Object> result = TypeConverter.objectToMap(input);
            assertThat(result).containsEntry("key", "value");
        }

        @Test
        void mapToObject_convertsToObject() {
            Map<String, Object> map = Map.of("name", "test", "age", 30);
            TestObj obj = TypeConverter.mapToObject(map, TestObj.class);
            assertThat(obj).isNotNull();
            assertThat(obj.name).isEqualTo("test");
            assertThat(obj.age).isEqualTo(30);
        }

        @Test
        void mapToObject_nullReturnsNull() {
            assertThat(TypeConverter.mapToObject(null, TestObj.class)).isNull();
            assertThat(TypeConverter.mapToObject(Map.of(), TestObj.class)).isNull();
        }
    }

    @Nested
    @DisplayName("Date Parsing")
    class DateParsing {

        @Test
        void parseDate_validPattern() {
            assertThat(TypeConverter.parseDate("15/06/2025", "dd/MM/yyyy"))
                .isEqualTo(LocalDate.of(2025, 6, 15));
        }

        @Test
        void parseDate_invalidReturnsNull() {
            assertThat(TypeConverter.parseDate("bad", "yyyy-MM-dd")).isNull();
        }

        @Test
        void parseDateTime_validPattern() {
            assertThat(TypeConverter.parseDateTime("2025-06-15 10:30:00", "yyyy-MM-dd HH:mm:ss"))
                .isEqualTo(LocalDateTime.of(2025, 6, 15, 10, 30, 0));
        }

        @Test
        void parseDateTime_invalidReturnsNull() {
            assertThat(TypeConverter.parseDateTime("bad", "yyyy-MM-dd HH:mm:ss")).isNull();
        }
    }

    @Test
    @DisplayName("canConvert returns true for valid conversions")
    void canConvert() {
        assertThat(TypeConverter.canConvert("42", Integer.class)).isTrue();
        assertThat(TypeConverter.canConvert("abc", Integer.class)).isFalse();
    }

    public static class TestObj {
        public String name;
        public int age;

        public TestObj() {}
    }
}
